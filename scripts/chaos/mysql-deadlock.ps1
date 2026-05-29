param(
  [string]$Container = "gbm-mysql",
  [string]$Database = "s-pay-mall-ddd-market",
  [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
$setup = @"
use `$Database`;
create table if not exists chaos_deadlock_probe (
  id int primary key,
  value int not null
) engine=InnoDB;
insert into chaos_deadlock_probe(id, value) values (1, 1), (2, 2)
on duplicate key update value = values(value);
"@

$script1 = @"
use `$Database`;
set autocommit=0;
update chaos_deadlock_probe set value = value + 1 where id = 1;
select sleep(2);
update chaos_deadlock_probe set value = value + 1 where id = 2;
commit;
"@

$script2 = @"
use `$Database`;
set autocommit=0;
update chaos_deadlock_probe set value = value + 1 where id = 2;
select sleep(2);
update chaos_deadlock_probe set value = value + 1 where id = 1;
commit;
"@

$setup | docker exec -i $Container mysql -uroot -p$Password | Out-Host
$job1 = Start-Job -ScriptBlock { param($c,$p,$s) $s | docker exec -i $c mysql -uroot -p$p } -ArgumentList $Container,$Password,$script1
$job2 = Start-Job -ScriptBlock { param($c,$p,$s) $s | docker exec -i $c mysql -uroot -p$p } -ArgumentList $Container,$Password,$script2
Wait-Job $job1,$job2 | Out-Host
Receive-Job $job1,$job2 | Out-Host
Remove-Job $job1,$job2
Write-Host "mysql deadlock drill finished"
