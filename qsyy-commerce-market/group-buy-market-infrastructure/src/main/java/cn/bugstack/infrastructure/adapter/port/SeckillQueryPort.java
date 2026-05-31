package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillQueryPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillActivityQuerySupport;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderTableGateway;
import cn.bugstack.infrastructure.adapter.support.SeckillResultQuerySupport;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillQueryPort implements ISeckillQueryPort {

    @Resource
    private SeckillActivityQuerySupport seckillActivityQuerySupport;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;
    @Resource
    private SeckillResultQuerySupport seckillResultQuerySupport;

    @Override
    public SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId) {
        return seckillActivityQuerySupport.query(activityId, source, channel, goodsId);
    }

    @Override
    public SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo) {
        return seckillOrderTableGateway.queryEntityByOutTradeNo(userId, outTradeNo);
    }

    @Override
    public SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo) {
        return seckillResultQuerySupport.query(userId, activityId, outTradeNo);
    }

}
