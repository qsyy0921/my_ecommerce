package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockSeckillOrderRequestDTO;
import cn.bugstack.api.dto.QuerySeckillOrderResultRequestDTO;
import cn.bugstack.api.dto.RefundSeckillOrderRequestDTO;
import cn.bugstack.api.dto.SeckillMarketRequestDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderRequestDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SeckillRequestValidator {

    public boolean validQueryMarketConfig(SeckillMarketRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getSource())
                && StringUtils.isNotBlank(requestDTO.getChannel())
                && StringUtils.isNotBlank(requestDTO.getGoodsId())
                && null != requestDTO.getActivityId();
    }

    public boolean validLockOrder(LockSeckillOrderRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getSource())
                && StringUtils.isNotBlank(requestDTO.getChannel())
                && StringUtils.isNotBlank(requestDTO.getGoodsId())
                && null != requestDTO.getActivityId()
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo());
    }

    public boolean validQueryOrderResult(QuerySeckillOrderResultRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && null != requestDTO.getActivityId()
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo());
    }

    public boolean validSettlement(SettlementSeckillOrderRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo());
    }

    public boolean validRefund(RefundSeckillOrderRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getOutTradeNo());
    }

}
