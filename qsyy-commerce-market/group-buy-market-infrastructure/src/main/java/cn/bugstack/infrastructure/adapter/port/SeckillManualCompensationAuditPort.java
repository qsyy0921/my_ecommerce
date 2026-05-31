package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationAuditPort;
import cn.bugstack.domain.seckill.model.entity.SeckillManualCompensationLogEntity;
import cn.bugstack.infrastructure.dao.ISeckillManualCompensationLogDao;
import cn.bugstack.infrastructure.dao.po.SeckillManualCompensationLog;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SeckillManualCompensationAuditPort implements ISeckillManualCompensationAuditPort {

    @Resource
    private ISeckillManualCompensationLogDao seckillManualCompensationLogDao;

    @Override
    public void record(SeckillManualCompensationLogEntity logEntity) {
        if (null == logEntity) {
            return;
        }
        seckillManualCompensationLogDao.insert(SeckillManualCompensationLog.builder()
                .operator(left(logEntity.getOperator(), 64))
                .operationType(left(logEntity.getOperationType(), 64))
                .messageIds(left(logEntity.getMessageIds(), 1024))
                .requestLimit(logEntity.getRequestLimit())
                .manualStreamKey(left(logEntity.getManualStreamKey(), 128))
                .resultCount(logEntity.getResultCount())
                .success(logEntity.getSuccess())
                .errorMessage(left(logEntity.getErrorMessage(), 512))
                .build());
    }

    @Override
    public List<SeckillManualCompensationLogEntity> queryRecentLogs(int limit) {
        List<SeckillManualCompensationLog> logs = seckillManualCompensationLogDao.queryRecentLogs(Math.max(1, Math.min(limit, 200)));
        List<SeckillManualCompensationLogEntity> result = new ArrayList<>();
        if (null == logs || logs.isEmpty()) {
            return result;
        }
        for (SeckillManualCompensationLog log : logs) {
            result.add(SeckillManualCompensationLogEntity.builder()
                    .id(log.getId())
                    .operator(log.getOperator())
                    .operationType(log.getOperationType())
                    .messageIds(log.getMessageIds())
                    .requestLimit(log.getRequestLimit())
                    .manualStreamKey(log.getManualStreamKey())
                    .resultCount(log.getResultCount())
                    .success(log.getSuccess())
                    .errorMessage(log.getErrorMessage())
                    .createTime(log.getCreateTime())
                    .build());
        }
        return result;
    }

    private String left(String value, int length) {
        if (null == value || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

}
