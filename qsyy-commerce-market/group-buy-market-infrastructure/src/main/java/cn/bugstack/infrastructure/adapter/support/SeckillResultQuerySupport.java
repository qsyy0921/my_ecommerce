package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillResultQuerySupport {

    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;

    public SeckillOrderEntity query(String userId, Long activityId, String outTradeNo) {
        SeckillOrderEntity result = seckillResultCachePort.query(activityId, userId, outTradeNo);
        if (null != result) {
            return result;
        }

        SeckillOrderEntity existsOrder = seckillOrderTableGateway.queryEntityByOutTradeNo(userId, outTradeNo);
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
            return existsOrder;
        }

        return SeckillOrderEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .outTradeNo(outTradeNo)
                .resultStatus(SeckillOrderEntity.RESULT_NOT_FOUND)
                .message("seckill order not found")
                .build();
    }

}
