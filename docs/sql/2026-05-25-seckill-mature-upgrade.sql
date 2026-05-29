-- Seckill mature architecture upgrade.
-- Apply this after 2026-05-24-full-score-upgrade.sql.

use `group_buy_market`;

create table if not exists `seckill_stock_flow` (
  `id` bigint unsigned not null auto_increment comment 'auto id',
  `flow_no` varchar(160) not null comment 'idempotent flow no',
  `user_id` varchar(64) not null comment 'user id',
  `activity_id` bigint not null comment 'activity id',
  `order_id` varchar(12) default null comment 'order id',
  `out_trade_no` varchar(32) not null comment 'external trade no',
  `stock_bucket` int default null comment 'redis stock bucket',
  `change_type` varchar(16) not null comment 'RESERVE or ROLLBACK',
  `change_count` int not null comment 'stock delta',
  `source` varchar(32) not null comment 'source system',
  `message` varchar(512) default null comment 'message',
  `create_time` datetime not null default current_timestamp comment 'create time',
  primary key (`id`),
  unique key `uq_flow_no` (`flow_no`),
  key `idx_activity_user` (`activity_id`,`user_id`),
  key `idx_out_trade_no` (`out_trade_no`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='seckill stock audit flow';

drop procedure if exists `create_seckill_order_shards`;

delimiter $$
create procedure `create_seckill_order_shards`()
begin
  declare shard_index int default 0;
  declare shard_table varchar(64);
  while shard_index < 16 do
    set shard_table = concat('seckill_order_', lpad(shard_index, 2, '0'));
    set @ddl = concat('create table if not exists `', shard_table, '` like `seckill_order`');
    prepare stmt from @ddl;
    execute stmt;
    deallocate prepare stmt;
    set shard_index = shard_index + 1;
  end while;
end$$
delimiter ;

call `create_seckill_order_shards`();

drop procedure if exists `create_seckill_order_shards`;
