package cn.bugstack.domain.seckill.service;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderOutboxPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;

import java.util.Collections;
import java.util.List;

/**
 * Seckill order outbox maintenance service implementation.
 */
public class SeckillOrderOutboxService implements ISeckillOrderOutboxService {

    private final ISeckillOrderOutboxPort seckillOrderOutboxPort;

    public SeckillOrderOutboxService(ISeckillOrderOutboxPort seckillOrderOutboxPort) {
        this.seckillOrderOutboxPort = seckillOrderOutboxPort;
    }

    @Override
    public int retryDueMessages(int limit) {
        return seckillOrderOutboxPort.retryDueMessages(Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public int retryManualMessages(int limit) {
        return seckillOrderOutboxPort.retryManualMessages(Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public List<SeckillOrderOutboxEntity> queryMessages(Integer status, int limit) {
        if (!SeckillOrderOutboxEntity.validStatus(status)) {
            return Collections.emptyList();
        }
        return seckillOrderOutboxPort.queryMessages(status, Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public int countMessages(Integer status) {
        if (!SeckillOrderOutboxEntity.validStatus(status)) {
            return 0;
        }
        return seckillOrderOutboxPort.countMessages(status);
    }

}
