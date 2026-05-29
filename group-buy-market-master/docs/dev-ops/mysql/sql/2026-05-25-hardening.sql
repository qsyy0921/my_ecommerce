-- Production hardening patches for group-buy-market.
-- Apply after the base 2-29 schema when upgrading an existing local database.

-- Notify task uuid is a business idempotency key. Historical sample rows may
-- contain empty uuid values, so normalize them before adding a unique index.
update notify_task
set uuid = concat(team_id, '_', coalesce(notify_category, '0'), '_legacy_', id)
where uuid is null or uuid = '';

alter table notify_task drop index uq_uuid;
alter table notify_task add unique key uq_uuid (uuid);
