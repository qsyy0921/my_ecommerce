package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.RefundMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.trade.model.entity.PayActivityEntity;
import cn.bugstack.domain.trade.model.entity.PayDiscountEntity;
import cn.bugstack.domain.trade.model.entity.TradePaySuccessEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundCommandEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyTradeCommandAssembler {

    public MarketProductEntity toMarketProduct(LockMarketPayOrderRequestDTO requestDTO) {
        return MarketProductEntity.builder()
                .userId(requestDTO.getUserId())
                .source(requestDTO.getSource())
                .channel(requestDTO.getChannel())
                .goodsId(requestDTO.getGoodsId())
                .activityId(requestDTO.getActivityId())
                .build();
    }

    public UserEntity toUser(String userId) {
        return UserEntity.builder()
                .userId(userId)
                .build();
    }

    public PayActivityEntity toPayActivity(String teamId, Long activityId, GroupBuyActivityDiscountVO discountVO) {
        return PayActivityEntity.builder()
                .teamId(teamId)
                .activityId(activityId)
                .activityName(discountVO.getActivityName())
                .startTime(discountVO.getStartTime())
                .endTime(discountVO.getEndTime())
                .validTime(discountVO.getValidTime())
                .targetCount(discountVO.getTarget())
                .build();
    }

    public PayDiscountEntity toPayDiscount(LockMarketPayOrderRequestDTO requestDTO,
                                           TrialBalanceEntity trialBalanceEntity,
                                           NotifyTypeEnumVO notifyTypeEnumVO) {
        LockMarketPayOrderRequestDTO.NotifyConfigVO notifyConfigVO = requestDTO.getNotifyConfigVO();
        return PayDiscountEntity.builder()
                .source(requestDTO.getSource())
                .channel(requestDTO.getChannel())
                .goodsId(requestDTO.getGoodsId())
                .goodsName(trialBalanceEntity.getGoodsName())
                .originalPrice(trialBalanceEntity.getOriginalPrice())
                .deductionPrice(trialBalanceEntity.getDeductionPrice())
                .payPrice(trialBalanceEntity.getPayPrice())
                .outTradeNo(requestDTO.getOutTradeNo())
                .notifyConfigVO(NotifyConfigVO.builder()
                        .notifyType(notifyTypeEnumVO)
                        .notifyMQ(notifyConfigVO.getNotifyMQ())
                        .notifyUrl(notifyConfigVO.getNotifyUrl())
                        .build())
                .build();
    }

    public TradePaySuccessEntity toTradePaySuccess(SettlementMarketPayOrderRequestDTO requestDTO) {
        return TradePaySuccessEntity.builder()
                .source(requestDTO.getSource())
                .channel(requestDTO.getChannel())
                .userId(requestDTO.getUserId())
                .outTradeNo(requestDTO.getOutTradeNo())
                .outTradeTime(requestDTO.getOutTradeTime())
                .build();
    }

    public TradeRefundCommandEntity toTradeRefundCommand(RefundMarketPayOrderRequestDTO requestDTO) {
        return TradeRefundCommandEntity.builder()
                .userId(requestDTO.getUserId())
                .outTradeNo(requestDTO.getOutTradeNo())
                .source(requestDTO.getSource())
                .channel(requestDTO.getChannel())
                .build();
    }

}
