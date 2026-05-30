package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderMessagePort;
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
        String message = JSON.toJSONString(seckillOrderEntity);
        if (seckillOrderCreateBuffer.useMq()) {
            eventPublisher.publishWithoutConfirm(topicSeckillOrderCreate, message);
            return true;
        }
        String routeKey = seckillOrderEntity.getActivityId() + ":" + seckillOrderEntity.getUserId() + ":" + seckillOrderEntity.getOutTradeNo();
        return seckillOrderCreateBuffer.offer(message, routeKey);
    }

}
