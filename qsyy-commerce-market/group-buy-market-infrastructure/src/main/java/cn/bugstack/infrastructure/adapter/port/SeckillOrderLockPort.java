package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderLockPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillReservationPublishSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillOrderLockPort implements ISeckillOrderLockPort {

    @Value("${app.seckill.stock-bucket-try-count:64}")
    private Integer stockBucketTryCount;

    @Resource
    private SeckillReservationPublishSupport seckillReservationPublishSupport;

    @Override
    public SeckillOrderEntity lockSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        return seckillReservationPublishSupport.reserveAndPublish(seckillOrderEntity, stockBucketTryCount);
    }

}
