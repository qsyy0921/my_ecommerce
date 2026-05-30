package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockSeckillOrderResponseDTO;
import cn.bugstack.api.dto.RefundSeckillOrderResponseDTO;
import cn.bugstack.api.dto.SeckillMarketResponseDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderResponseDTO;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import org.springframework.stereotype.Component;

@Component
public class SeckillResponseAssembler {

    public SeckillMarketResponseDTO toMarketResponse(SeckillActivityEntity entity) {
        return SeckillMarketResponseDTO.builder()
                .activityId(entity.getActivityId())
                .activityName(entity.getActivityName())
                .goodsId(entity.getGoodsId())
                .goodsName(entity.getGoodsName())
                .originalPrice(entity.getOriginalPrice())
                .seckillPrice(entity.getSeckillPrice())
                .totalCount(entity.getTotalCount())
                .availableCount(entity.getAvailableCount())
                .lockCount(entity.getLockCount())
                .status(entity.getStatus())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .build();
    }

    public LockSeckillOrderResponseDTO toLockResponse(SeckillOrderEntity entity) {
        return LockSeckillOrderResponseDTO.builder()
                .orderId(entity.getOrderId())
                .activityId(entity.getActivityId())
                .goodsId(entity.getGoodsId())
                .originalPrice(entity.getOriginalPrice())
                .seckillPrice(entity.getSeckillPrice())
                .status(entity.getStatus())
                .resultStatus(entity.getResultStatus())
                .message(entity.getMessage())
                .build();
    }

    public SettlementSeckillOrderResponseDTO toSettlementResponse(SeckillOrderEntity entity) {
        return SettlementSeckillOrderResponseDTO.builder()
                .userId(entity.getUserId())
                .activityId(entity.getActivityId())
                .orderId(entity.getOrderId())
                .outTradeNo(entity.getOutTradeNo())
                .status(entity.getStatus())
                .build();
    }

    public RefundSeckillOrderResponseDTO toRefundResponse(SeckillOrderEntity entity, boolean stockReleased) {
        return RefundSeckillOrderResponseDTO.builder()
                .userId(entity.getUserId())
                .activityId(entity.getActivityId())
                .orderId(entity.getOrderId())
                .outTradeNo(entity.getOutTradeNo())
                .status(entity.getStatus())
                .stockReleased(stockReleased)
                .build();
    }

}
