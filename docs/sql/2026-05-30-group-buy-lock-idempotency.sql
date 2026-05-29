-- Group-buy lock idempotency hardening.
-- Adds DB-level fences for the Redis request lock and user/team occupancy cache.

set @idx_exists := (
    select count(1)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'group_buy_order_list'
      and index_name = 'uq_gbol_user_out_trade_no'
);
set @ddl := if(@idx_exists = 0,
    'alter table group_buy_order_list add unique key uq_gbol_user_out_trade_no (user_id, out_trade_no)',
    'select 1'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @idx_exists := (
    select count(1)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'group_buy_order_list'
      and index_name = 'uq_gbol_biz_id'
);
set @ddl := if(@idx_exists = 0,
    'alter table group_buy_order_list add unique key uq_gbol_biz_id (biz_id)',
    'select 1'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
