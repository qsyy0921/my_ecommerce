package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IMarketOrderLockPort;
import cn.bugstack.domain.order.model.entity.MarketPayDiscountEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.infrastructure.gateway.IGroupBuyMarketService;
import cn.bugstack.infrastructure.gateway.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.infrastructure.gateway.dto.LockMarketPayOrderResponseDTO;
import cn.bugstack.infrastructure.gateway.dto.LockSeckillOrderRequestDTO;
import cn.bugstack.infrastructure.gateway.dto.LockSeckillOrderResponseDTO;
import cn.bugstack.infrastructure.gateway.response.Response;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;

@Slf4j
@Component
public class MarketOrderLockPort implements IMarketOrderLockPort {

    @Value("${app.config.group-buy-market.source}")
    private String source;
    @Value("${app.config.group-buy-market.chanel}")
    private String chanel;

    private final IGroupBuyMarketService groupBuyMarketService;

    public MarketOrderLockPort(IGroupBuyMarketService groupBuyMarketService) {
        this.groupBuyMarketService = groupBuyMarketService;
    }

    @Override
    public MarketPayDiscountEntity lockGroupBuyMarketPayOrder(String userId, String teamId, Long activityId, String productId, String orderId) {
        LockMarketPayOrderRequestDTO requestDTO = new LockMarketPayOrderRequestDTO();
        requestDTO.setUserId(userId);
        requestDTO.setTeamId(teamId);
        requestDTO.setGoodsId(productId);
        requestDTO.setActivityId(activityId);
        requestDTO.setSource(source);
        requestDTO.setChannel(chanel);
        requestDTO.setOutTradeNo(orderId);
        requestDTO.setNotifyMQ();

        try {
            Call<Response<LockMarketPayOrderResponseDTO>> call = groupBuyMarketService.lockMarketPayOrder(requestDTO);
            Response<LockMarketPayOrderResponseDTO> response = call.execute().body();
            log.info("营销锁单{} requestDTO:{} responseDTO:{}", userId, JSON.toJSONString(requestDTO), JSON.toJSONString(response));
            if (null == response) return null;
            if (!"0000".equals(response.getCode())) {
                throw new AppException(response.getCode(), response.getInfo());
            }

            LockMarketPayOrderResponseDTO responseDTO = response.getData();
            return MarketPayDiscountEntity.builder()
                    .originalPrice(responseDTO.getOriginalPrice())
                    .deductionPrice(responseDTO.getDeductionPrice())
                    .payPrice(responseDTO.getPayPrice())
                    .marketType(MarketTypeVO.GROUP_BUY_MARKET.getCode())
                    .build();
        } catch (Exception e) {
            log.error("营销锁单失败{}", userId, e);
            return null;
        }
    }

    @Override
    public MarketPayDiscountEntity lockSeckillPayOrder(String userId, Long activityId, String productId, String orderId) {
        LockSeckillOrderRequestDTO requestDTO = LockSeckillOrderRequestDTO.builder()
                .userId(userId)
                .source(source)
                .channel(chanel)
                .goodsId(productId)
                .activityId(activityId)
                .outTradeNo(orderId)
                .build();

        try {
            Call<Response<LockSeckillOrderResponseDTO>> call = groupBuyMarketService.lockSeckillOrder(requestDTO);
            Response<LockSeckillOrderResponseDTO> response = call.execute().body();
            log.info("秒杀锁单{} requestDTO:{} responseDTO:{}", userId, JSON.toJSONString(requestDTO), JSON.toJSONString(response));
            if (null == response) return null;
            if (!"0000".equals(response.getCode())) {
                throw new AppException(response.getCode(), response.getInfo());
            }

            LockSeckillOrderResponseDTO responseDTO = response.getData();
            return MarketPayDiscountEntity.builder()
                    .originalPrice(responseDTO.getOriginalPrice())
                    .deductionPrice(responseDTO.getOriginalPrice().subtract(responseDTO.getSeckillPrice()))
                    .payPrice(responseDTO.getSeckillPrice())
                    .marketType(MarketTypeVO.SECKILL_MARKET.getCode())
                    .build();
        } catch (Exception e) {
            log.error("秒杀锁单失败{}", userId, e);
            return null;
        }
    }

}
