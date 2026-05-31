package cn.bugstack.domain.seckill.service;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;

import java.util.List;

/**
 * Seckill order outbox maintenance service.
 */
public interface ISeckillOrderOutboxService {

    int retryDueMessages(int limit);

    int retryManualMessages(int limit);

    List<SeckillOrderOutboxEntity> queryMessages(Integer status, int limit);

    int countMessages(Integer status);

}
