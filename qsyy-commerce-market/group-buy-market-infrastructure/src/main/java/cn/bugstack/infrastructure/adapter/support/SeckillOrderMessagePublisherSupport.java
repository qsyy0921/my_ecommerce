package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Publishes seckill order creation messages to the configured queue backend.
 */
@Component
public class SeckillOrderMessagePublisherSupport {

    @Value("${spring.rabbitmq.config.producer.topic_seckill_order_create.routing_key}")
    private String rabbitRoutingKey;

    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;

    public boolean publish(String messageBody, String routeKey) {
        if (seckillOrderCreateBuffer.useMq()) {
            eventPublisher.publishWithoutConfirm(rabbitRoutingKey, messageBody);
            return true;
        }
        return seckillOrderCreateBuffer.offer(messageBody, routeKey);
    }

}
