-- Job lock and execution audit tables for local multi-instance compensation jobs.

use `group_buy_market`;

create table if not exists `job_lock` (
  `job_name` varchar(128) not null comment 'job name',
  `owner_id` varchar(128) default null comment 'current owner instance',
  `lock_until` datetime not null comment 'lock expire time',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`job_name`),
  key `idx_lock_until` (`lock_until`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='job distributed lock';

create table if not exists `job_execution_record` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `execution_id` varchar(128) not null comment 'execution id',
  `job_name` varchar(128) not null comment 'job name',
  `owner_id` varchar(128) not null comment 'owner instance',
  `status` tinyint not null comment '0 running, 1 success, 2 fail, 3 skipped',
  `lock_acquired` tinyint(1) not null default 0 comment 'whether lock acquired',
  `start_time` datetime not null comment 'start time',
  `end_time` datetime default null comment 'end time',
  `duration_ms` bigint default null comment 'duration in milliseconds',
  `success_count` int not null default 0 comment 'success count',
  `fail_count` int not null default 0 comment 'fail count',
  `result_message` varchar(1024) default null comment 'result message',
  `error_message` varchar(2048) default null comment 'error message',
  `trace_id` varchar(128) default null comment 'trace id',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_execution_id` (`execution_id`),
  key `idx_job_status_time` (`job_name`, `status`, `start_time`),
  key `idx_start_time` (`start_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='job execution audit record';

use `s-pay-mall-ddd-market`;

create table if not exists `job_lock` (
  `job_name` varchar(128) not null comment 'job name',
  `owner_id` varchar(128) default null comment 'current owner instance',
  `lock_until` datetime not null comment 'lock expire time',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`job_name`),
  key `idx_lock_until` (`lock_until`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='job distributed lock';

create table if not exists `job_execution_record` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `execution_id` varchar(128) not null comment 'execution id',
  `job_name` varchar(128) not null comment 'job name',
  `owner_id` varchar(128) not null comment 'owner instance',
  `status` tinyint not null comment '0 running, 1 success, 2 fail, 3 skipped',
  `lock_acquired` tinyint(1) not null default 0 comment 'whether lock acquired',
  `start_time` datetime not null comment 'start time',
  `end_time` datetime default null comment 'end time',
  `duration_ms` bigint default null comment 'duration in milliseconds',
  `success_count` int not null default 0 comment 'success count',
  `fail_count` int not null default 0 comment 'fail count',
  `result_message` varchar(1024) default null comment 'result message',
  `error_message` varchar(2048) default null comment 'error message',
  `trace_id` varchar(128) default null comment 'trace id',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_execution_id` (`execution_id`),
  key `idx_job_status_time` (`job_name`, `status`, `start_time`),
  key `idx_start_time` (`start_time`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='job execution audit record';
