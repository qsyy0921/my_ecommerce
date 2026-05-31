package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.RefundMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyTradeRequestValidator {

    public boolean validLock(LockMarketPayOrderRequestDTO requestDTO) {
        if (null == requestDTO || null == requestDTO.getNotifyConfigVO()) {
            return false;
        }
        return StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getSource())
                && StringUtils.isNotBlank(requestDTO.getChannel())
                && StringUtils.isNotBlank(requestDTO.getGoodsId())
                && null != requestDTO.getActivityId()
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo())
                && StringUtils.isNotBlank(requestDTO.getNotifyConfigVO().getNotifyType());
    }

    public NotifyTypeEnumVO resolveNotifyType(LockMarketPayOrderRequestDTO requestDTO) {
        try {
            return NotifyTypeEnumVO.valueOf(requestDTO.getNotifyConfigVO().getNotifyType());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validNotifyUrl(LockMarketPayOrderRequestDTO requestDTO, NotifyTypeEnumVO notifyTypeEnumVO) {
        return !NotifyTypeEnumVO.HTTP.equals(notifyTypeEnumVO)
                || StringUtils.isNotBlank(requestDTO.getNotifyConfigVO().getNotifyUrl());
    }

    public boolean hasTeamId(String teamId) {
        return StringUtils.isNotBlank(teamId);
    }

    public boolean validSettlement(SettlementMarketPayOrderRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getSource())
                && StringUtils.isNotBlank(requestDTO.getChannel())
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo())
                && null != requestDTO.getOutTradeTime();
    }

    public boolean validRefund(RefundMarketPayOrderRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo())
                && StringUtils.isNotBlank(requestDTO.getSource())
                && StringUtils.isNotBlank(requestDTO.getChannel());
    }

}
