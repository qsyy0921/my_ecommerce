package cn.bugstack.domain.seckill.service;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderOutboxPort;

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

}
