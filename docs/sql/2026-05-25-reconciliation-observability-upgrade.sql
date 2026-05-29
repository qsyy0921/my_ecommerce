-- Reconciliation center and observability upgrade.
-- Apply to the local MySQL used by s-pay-mall-ddd-market and group-buy-market.

use `s-pay-mall-ddd-market`;

create table if not exists `reconcile_case` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `case_no` varchar(160) not null comment 'unique case no',
  `biz_type` varchar(32) not null comment 'PAY_ORDER or MQ_MESSAGE',
  `biz_id` varchar(128) not null comment 'business id',
  `user_id` varchar(64) default null comment 'user id',
  `case_type` varchar(64) not null comment 'case type',
  `case_status` tinyint(1) not null default 0 comment '0 open, 1 handled, 2 ignored',
  `severity` varchar(16) not null default 'warning' comment 'warning or critical',
  `source_status` varchar(64) default null comment 'current status',
  `target_status` varchar(64) default null comment 'expected status',
  `summary` varchar(256) not null comment 'summary',
  `detail` varchar(1024) default null comment 'detail',
  `retry_count` int not null default 0 comment 'refresh count',
  `handled_time` datetime default null comment 'handled time',
  `handler` varchar(64) default null comment 'handler',
  `handle_note` varchar(512) default null comment 'handle note',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_case_no` (`case_no`),
  key `idx_status_type` (`case_status`, `case_type`),
  key `idx_biz` (`biz_type`, `biz_id`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='reconciliation discrepancy case';

create table if not exists `payment_flow` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `flow_no` varchar(160) not null comment 'payment flow no',
  `order_id` varchar(32) not null comment 'order id',
  `user_id` varchar(64) default null comment 'user id',
  `pay_channel` varchar(32) not null default 'unknown' comment 'pay channel',
  `channel_trade_no` varchar(128) default null comment 'channel trade no',
  `pay_amount` decimal(10,2) default null comment 'pay amount',
  `pay_status` varchar(32) not null comment 'payment status',
  `pay_time` datetime default null comment 'payment time',
  `raw_message` text comment 'raw callback or message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_flow_no` (`flow_no`),
  key `idx_order_id` (`order_id`),
  key `idx_status_time` (`pay_status`, `pay_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='payment flow';

create table if not exists `refund_flow` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `flow_no` varchar(160) not null comment 'refund flow no',
  `order_id` varchar(32) not null comment 'order id',
  `user_id` varchar(64) default null comment 'user id',
  `refund_channel` varchar(32) not null default 'unknown' comment 'refund channel',
  `channel_refund_no` varchar(128) default null comment 'channel refund no',
  `refund_amount` decimal(10,2) default null comment 'refund amount',
  `refund_status` varchar(32) not null comment 'refund status',
  `refund_reason` varchar(256) default null comment 'refund reason',
  `refund_time` datetime default null comment 'refund time',
  `raw_message` text comment 'raw refund message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_flow_no` (`flow_no`),
  key `idx_order_id` (`order_id`),
  key `idx_status_time` (`refund_status`, `refund_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='refund flow';

create table if not exists `third_party_bill` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `bill_no` varchar(160) not null comment 'third party bill no',
  `order_id` varchar(32) not null comment 'order id',
  `channel` varchar(32) not null comment 'pay channel',
  `channel_trade_no` varchar(128) default null comment 'channel trade no',
  `bill_type` varchar(16) not null comment 'PAY or REFUND',
  `amount` decimal(10,2) not null comment 'bill amount',
  `bill_status` varchar(32) not null comment 'bill status',
  `bill_time` datetime not null comment 'bill time',
  `raw_line` text comment 'raw bill line',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_bill_no` (`bill_no`),
  key `idx_order_type` (`order_id`, `bill_type`),
  key `idx_bill_time` (`bill_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='third party payment bill';

create table if not exists `reconcile_operation_log` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `operator` varchar(64) not null comment 'operator',
  `operation_type` varchar(64) not null comment 'operation type',
  `biz_id` varchar(160) default null comment 'business id',
  `request_body` varchar(1024) default null comment 'request body snapshot',
  `result` varchar(512) default null comment 'operation result',
  `create_time` datetime not null default current_timestamp comment 'create time',
  primary key (`id`),
  key `idx_operator_time` (`operator`, `create_time`),
  key `idx_operation_biz` (`operation_type`, `biz_id`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='reconciliation operation audit log';

drop procedure if exists `add_index_if_absent`;

delimiter $$
create procedure `add_index_if_absent`(
  in table_name_value varchar(64),
  in index_name_value varchar(64),
  in ddl_value varchar(512)
)
begin
  if not exists (
    select 1
    from information_schema.statistics
    where table_schema = database()
      and table_name = table_name_value
      and index_name = index_name_value
  ) then
    set @ddl = ddl_value;
    prepare stmt from @ddl;
    execute stmt;
    deallocate prepare stmt;
  end if;
end$$
delimiter ;

call `add_index_if_absent`('pay_order', 'idx_status_update_time', 'alter table `pay_order` add index `idx_status_update_time` (`status`, `update_time`)');
call `add_index_if_absent`('pay_order', 'idx_status_order_time', 'alter table `pay_order` add index `idx_status_order_time` (`status`, `order_time`)');

drop procedure if exists `add_index_if_absent`;

use `group_buy_market`;

drop procedure if exists `add_index_if_absent`;

delimiter $$
create procedure `add_index_if_absent`(
  in table_name_value varchar(64),
  in index_name_value varchar(64),
  in ddl_value varchar(512)
)
begin
  if not exists (
    select 1
    from information_schema.statistics
    where table_schema = database()
      and table_name = table_name_value
      and index_name = index_name_value
  ) then
    set @ddl = ddl_value;
    prepare stmt from @ddl;
    execute stmt;
    deallocate prepare stmt;
  end if;
end$$
delimiter ;

call `add_index_if_absent`('notify_task', 'idx_notify_status_update_time', 'alter table `notify_task` add index `idx_notify_status_update_time` (`notify_status`, `update_time`)');

drop procedure if exists `add_index_if_absent`;
