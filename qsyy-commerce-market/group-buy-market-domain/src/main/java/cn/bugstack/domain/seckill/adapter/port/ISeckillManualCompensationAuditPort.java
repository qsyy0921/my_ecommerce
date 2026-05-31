package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillManualCompensationLogEntity;

import java.util.List;

public interface ISeckillManualCompensationAuditPort {

    void record(SeckillManualCompensationLogEntity logEntity);

    List<SeckillManualCompensationLogEntity> queryRecentLogs(int limit);

}
