use group_buy_market;

drop procedure if exists add_column_if_not_exists;
delimiter //
create procedure add_column_if_not_exists(in p_table varchar(64), in p_column varchar(64), in p_definition varchar(1024))
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = database() and table_name = p_table and column_name = p_column
  ) then
    set @ddl = concat('alter table `', p_table, '` add column ', p_definition);
    prepare stmt from @ddl;
    execute stmt;
    deallocate prepare stmt;
  end if;
end //
delimiter ;

create table if not exists `order_state_flow` (
  `id` bigint unsigned not null auto_increment comment 'id',
  `flow_no` varchar(192) not null comment 'unique state flow no',
  `biz_type` varchar(32) not null comment 'business type',
  `biz_id` varchar(64) not null comment 'business id',
  `sub_biz_id` varchar(64) default null comment 'sub business id',
  `from_status` varchar(32) not null comment 'from status',
  `to_status` varchar(32) not null comment 'to status',
  `event` varchar(64) not null comment 'state transition event',
  `operator_id` varchar(64) default null comment 'operator id',
  `trace_id` varchar(128) default null comment 'trace id',
  `source_message_id` varchar(192) default null comment 'source mq/stream message id',
  `message` varchar(512) default null comment 'message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  primary key (`id`),
  unique key `uq_flow_no` (`flow_no`),
  key `idx_biz` (`biz_type`, `biz_id`),
  key `idx_trace_id` (`trace_id`),
  key `idx_create_time` (`create_time`)
) engine=InnoDB default charset=utf8mb4 comment='order state transition flow';

call add_column_if_not_exists('seckill_stock_flow', 'stock_before', '`stock_before` int default null comment ''stock before change'' after `change_count`');
call add_column_if_not_exists('seckill_stock_flow', 'stock_after', '`stock_after` int default null comment ''stock after change'' after `stock_before`');
call add_column_if_not_exists('seckill_stock_flow', 'biz_event', '`biz_event` varchar(64) default null comment ''business event'' after `stock_after`');
call add_column_if_not_exists('seckill_stock_flow', 'trace_id', '`trace_id` varchar(128) default null comment ''trace id'' after `biz_event`');
call add_column_if_not_exists('seckill_stock_flow', 'source_message_id', '`source_message_id` varchar(192) default null comment ''source mq/stream message id'' after `trace_id`');

call add_column_if_not_exists('group_buy_stock_flow', 'stock_before', '`stock_before` int default null comment ''stock before change'' after `change_count`');
call add_column_if_not_exists('group_buy_stock_flow', 'stock_after', '`stock_after` int default null comment ''stock after change'' after `stock_before`');
call add_column_if_not_exists('group_buy_stock_flow', 'biz_event', '`biz_event` varchar(64) default null comment ''business event'' after `stock_after`');
call add_column_if_not_exists('group_buy_stock_flow', 'trace_id', '`trace_id` varchar(128) default null comment ''trace id'' after `biz_event`');
call add_column_if_not_exists('group_buy_stock_flow', 'source_message_id', '`source_message_id` varchar(192) default null comment ''source mq/stream message id'' after `trace_id`');

create table if not exists `seckill_order_outbox` (
  `id` bigint unsigned not null auto_increment comment 'id',
  `message_id` varchar(128) not null comment 'message id',
  `route_key` varchar(192) not null comment 'route key for mq partition/queue',
  `topic` varchar(128) not null comment 'mq topic',
  `message_body` text not null comment 'message body',
  `status` tinyint not null default 0 comment '0-init,1-sent,2-failed,3-dead',
  `retry_count` int not null default 0 comment 'retry count',
  `next_retry_time` datetime default null comment 'next retry time',
  `error_message` varchar(512) default null comment 'error message',
  `trace_id` varchar(128) default null comment 'trace id',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_message_id` (`message_id`),
  key `idx_status_retry` (`status`, `next_retry_time`),
  key `idx_route_key` (`route_key`)
) engine=InnoDB default charset=utf8mb4 comment='seckill order reliable outbox for RocketMQ evolution';

drop procedure if exists add_column_if_not_exists;
