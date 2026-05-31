package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderMessagePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillOrderMessagePort implements ISeckillOrderMessagePort {

    @Value("${spring.rabbitmq.config.producer.topic_seckill_order_create.routing_key}")
    private String topicSeckillOrderCreate;

    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;

    @Override
    public boolean publishOrderCreate(SeckillOrderEntity seckillOrderEntity) {
        SeckillOrderCreateMessageEntity messageEnvelope = SeckillOrderCreateMessageEntity.fromOrder(seckillOrderEntity);
        String message = JSON.toJSONString(messageEnvelope);
        if (seckillOrderCreateBuffer.useMq()) {
            eventPublisher.publishWithoutConfirm(topicSeckillOrderCreate, message);
            return true;
        }
        return seckillOrderCreateBuffer.offer(message, messageEnvelope.stableRouteKey());
    }

}
