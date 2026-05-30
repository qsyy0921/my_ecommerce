package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockMarketPayOrderResponseDTO;
import cn.bugstack.api.dto.RefundMarketPayOrderResponseDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderResponseDTO;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.entity.TradePaySettlementEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyTradeResponseAssembler {

    public LockMarketPayOrderResponseDTO toLockResponse(MarketPayOrderEntity entity) {
        return LockMarketPayOrderResponseDTO.builder()
                .orderId(entity.getOrderId())
                .originalPrice(entity.getOriginalPrice())
                .deductionPrice(entity.getDeductionPrice())
                .payPrice(entity.getPayPrice())
                .tradeOrderStatus(entity.getTradeOrderStatusEnumVO().getCode())
                .teamId(entity.getTeamId())
                .build();
    }

    public SettlementMarketPayOrderResponseDTO toSettlementResponse(TradePaySettlementEntity entity) {
        return SettlementMarketPayOrderResponseDTO.builder()
                .userId(entity.getUserId())
                .teamId(entity.getTeamId())
                .activityId(entity.getActivityId())
                .outTradeNo(entity.getOutTradeNo())
                .build();
    }

    public RefundMarketPayOrderResponseDTO toRefundResponse(TradeRefundBehaviorEntity entity) {
        return RefundMarketPayOrderResponseDTO.builder()
                .userId(entity.getUserId())
                .orderId(entity.getOrderId())
                .teamId(entity.getTeamId())
                .code(entity.getTradeRefundBehaviorEnum().getCode())
                .info(entity.getTradeRefundBehaviorEnum().getInfo())
                .build();
    }

}
