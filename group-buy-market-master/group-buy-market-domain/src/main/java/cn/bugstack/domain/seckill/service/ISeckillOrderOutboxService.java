package cn.bugstack.domain.seckill.service;

/**
 * Seckill order outbox maintenance service.
 */
public interface ISeckillOrderOutboxService {

    int retryDueMessages(int limit);

    int retryManualMessages(int limit);

}
