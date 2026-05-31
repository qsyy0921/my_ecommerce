package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;

public final class SeckillOrderAssembler {

    private SeckillOrderAssembler() {
    }

    public static SeckillOrderEntity toEntity(SeckillOrder seckillOrder) {
        if (null == seckillOrder) return null;
        return SeckillOrderEntity.builder()
                .userId(seckillOrder.getUserId())
                .activityId(seckillOrder.getActivityId())
                .activityName(seckillOrder.getActivityName())
                .goodsId(seckillOrder.getGoodsId())
                .goodsName(seckillOrder.getGoodsName())
                .source(seckillOrder.getSource())
                .channel(seckillOrder.getChannel())
                .orderId(seckillOrder.getOrderId())
                .outTradeNo(seckillOrder.getOutTradeNo())
                .originalPrice(seckillOrder.getOriginalPrice())
                .seckillPrice(seckillOrder.getSeckillPrice())
                .status(seckillOrder.getStatus())
                .resultStatus(SeckillOrderEntity.RESULT_SUCCESS)
                .message("order created")
                .createTime(seckillOrder.getCreateTime())
                .build();
    }

    public static SeckillOrder toPo(SeckillOrderEntity seckillOrderEntity) {
        return SeckillOrder.builder()
                .userId(seckillOrderEntity.getUserId())
                .activityId(seckillOrderEntity.getActivityId())
                .activityName(seckillOrderEntity.getActivityName())
                .goodsId(seckillOrderEntity.getGoodsId())
                .goodsName(seckillOrderEntity.getGoodsName())
                .source(seckillOrderEntity.getSource())
                .channel(seckillOrderEntity.getChannel())
                .orderId(seckillOrderEntity.getOrderId())
                .outTradeNo(seckillOrderEntity.getOutTradeNo())
                .originalPrice(seckillOrderEntity.getOriginalPrice())
                .seckillPrice(seckillOrderEntity.getSeckillPrice())
                .status(seckillOrderEntity.getStatus())
                .build();
    }

}
