package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;

import java.util.List;

/**
 * Reliable outbox for seckill order creation messages.
 */
public interface ISeckillOrderOutboxPort {

    boolean recordInit(SeckillOrderCreateMessageEntity message, String topic, String messageBody);

    void markSent(String messageId);

    void markFailed(String messageId, String errorMessage);

    int retryDueMessages(int limit);

    int retryManualMessages(int limit);

    List<SeckillOrderOutboxEntity> queryMessages(Integer status, int limit);

    int countMessages(Integer status);

}
