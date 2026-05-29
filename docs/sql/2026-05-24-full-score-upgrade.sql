-- Full-score architecture upgrade migration.
-- Apply the group_buy_market block to the marketing database and the
-- s-pay-mall-ddd-market block to the payment mall database.

create database if not exists `group_buy_market` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `group_buy_market`;

create table if not exists `seckill_activity` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `activity_id` bigint not null comment 'seckill activity id',
  `activity_name` varchar(128) not null comment 'activity name',
  `source` varchar(8) not null comment 'source',
  `channel` varchar(8) not null comment 'channel',
  `goods_id` varchar(16) not null comment 'goods id',
  `seckill_price` decimal(10,2) not null comment 'seckill price',
  `total_count` int not null comment 'total stock',
  `available_count` int not null comment 'available stock',
  `lock_count` int not null default 0 comment 'locked stock',
  `take_limit_count` int not null default 1 comment 'user take limit',
  `status` tinyint(1) not null default 0 comment '0 create, 1 enabled, 2 expired, 3 disabled',
  `start_time` datetime not null comment 'start time',
  `end_time` datetime not null comment 'end time',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_activity_id` (`activity_id`),
  unique key `uq_sc_goods` (`source`,`channel`,`goods_id`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='seckill activity';

create table if not exists `seckill_order` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `user_id` varchar(64) not null comment 'user id',
  `activity_id` bigint not null comment 'activity id',
  `activity_name` varchar(128) not null comment 'activity name',
  `goods_id` varchar(16) not null comment 'goods id',
  `goods_name` varchar(128) not null comment 'goods name',
  `source` varchar(8) not null comment 'source',
  `channel` varchar(8) not null comment 'channel',
  `order_id` varchar(12) not null comment 'order id',
  `out_trade_no` varchar(32) not null comment 'external trade no',
  `original_price` decimal(10,2) not null comment 'original price',
  `seckill_price` decimal(10,2) not null comment 'seckill price',
  `status` tinyint(1) not null default 0 comment '0 locked, 1 paid, 2 closed',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_order_id` (`order_id`),
  unique key `uq_user_out_trade_no` (`user_id`,`out_trade_no`),
  unique key `uq_user_activity` (`user_id`,`activity_id`),
  key `idx_activity_status` (`activity_id`,`status`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='seckill order';

create table if not exists `mq_message_record` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `message_id` varchar(128) not null comment 'message id',
  `exchange_name` varchar(128) default null comment 'exchange',
  `queue_name` varchar(128) default null comment 'queue',
  `message_body` text comment 'message body',
  `status` tinyint(1) not null default 0 comment '0 processing, 1 success, 2 fail',
  `retry_count` int not null default 0 comment 'retry count',
  `error_message` varchar(512) default null comment 'error message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_message_id` (`message_id`),
  key `idx_status_update_time` (`status`,`update_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='mq consume record';

insert into `seckill_activity` (
  activity_id, activity_name, source, channel, goods_id, seckill_price,
  total_count, available_count, lock_count, take_limit_count, status, start_time, end_time
) values (
  900001, 'test seckill activity', 's01', 'c01', '9890001', 69.00,
  100, 100, 0, 1, 1, '2026-01-01 00:00:00', '2029-12-31 23:59:59'
) on duplicate key update update_time = now();

create database if not exists `s-pay-mall-ddd-market` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `s-pay-mall-ddd-market`;

create table if not exists `mq_message_record` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `message_id` varchar(128) not null comment 'message id',
  `exchange_name` varchar(128) default null comment 'exchange',
  `queue_name` varchar(128) default null comment 'queue',
  `message_body` text comment 'message body',
  `status` tinyint(1) not null default 0 comment '0 processing, 1 success, 2 fail',
  `retry_count` int not null default 0 comment 'retry count',
  `error_message` varchar(512) default null comment 'error message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_message_id` (`message_id`),
  key `idx_status_update_time` (`status`,`update_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='mq consume record';
