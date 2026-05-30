-- Seckill manual compensation operation audit table.

create table if not exists `seckill_manual_compensation_log` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `operator` varchar(64) not null comment 'operator',
  `operation_type` varchar(64) not null comment 'QUERY_MANUAL/REPLAY_MANUAL',
  `message_ids` varchar(1024) default null comment 'manual stream message ids snapshot',
  `request_limit` int default null comment 'request limit',
  `manual_stream_key` varchar(128) default null comment 'manual compensation stream key',
  `result_count` int default null comment 'query or replay result count',
  `success` tinyint not null default 1 comment '1 success, 0 failed',
  `error_message` varchar(512) default null comment 'error message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  primary key (`id`),
  key `idx_operator_time` (`operator`, `create_time`),
  key `idx_operation_time` (`operation_type`, `create_time`)
) engine=InnoDB default charset=utf8mb4 comment='seckill manual compensation operation audit log';
