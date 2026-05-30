package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.GoodsMarketRequestDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyMarketConfigRequestValidator {

    public boolean valid(GoodsMarketRequestDTO requestDTO) {
        return null != requestDTO
                && StringUtils.isNotBlank(requestDTO.getUserId())
                && StringUtils.isNotBlank(requestDTO.getSource())
                && StringUtils.isNotBlank(requestDTO.getChannel())
                && StringUtils.isNotBlank(requestDTO.getGoodsId());
    }

}
