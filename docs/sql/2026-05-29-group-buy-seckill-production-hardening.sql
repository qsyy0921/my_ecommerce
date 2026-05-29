use group_buy_market;

create table if not exists `group_buy_stock_flow` (
  `id` bigint unsigned not null auto_increment comment 'id',
  `flow_no` varchar(160) not null comment 'unique flow no',
  `user_id` varchar(64) not null comment 'user id',
  `activity_id` bigint not null comment 'activity id',
  `team_id` varchar(32) not null comment 'team id',
  `order_id` varchar(64) not null comment 'order id',
  `out_trade_no` varchar(64) not null comment 'external trade no',
  `change_type` varchar(32) not null comment 'RESERVE/ROLLBACK',
  `change_count` int not null comment 'stock change count',
  `source` varchar(32) not null comment 'flow source',
  `message` varchar(256) default null comment 'message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (`id`),
  unique key `uq_flow_no` (`flow_no`),
  key `idx_activity_team` (`activity_id`, `team_id`),
  key `idx_user_out_trade_no` (`user_id`, `out_trade_no`)
) engine=InnoDB default charset=utf8mb4 comment='group buy stock flow audit';
