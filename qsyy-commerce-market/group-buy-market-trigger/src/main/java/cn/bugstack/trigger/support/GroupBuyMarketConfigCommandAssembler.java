package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.GoodsMarketRequestDTO;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyMarketConfigCommandAssembler {

    public MarketProductEntity toMarketProduct(GoodsMarketRequestDTO requestDTO) {
        return MarketProductEntity.builder()
                .userId(requestDTO.getUserId())
                .source(requestDTO.getSource())
                .channel(requestDTO.getChannel())
                .goodsId(requestDTO.getGoodsId())
                .build();
    }

}
