package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IMarketSettlementPort;
import cn.bugstack.infrastructure.gateway.IGroupBuyMarketService;
import cn.bugstack.infrastructure.gateway.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.infrastructure.gateway.dto.SettlementMarketPayOrderResponseDTO;
import cn.bugstack.infrastructure.gateway.dto.SettlementSeckillOrderRequestDTO;
import cn.bugstack.infrastructure.gateway.dto.SettlementSeckillOrderResponseDTO;
import cn.bugstack.infrastructure.gateway.response.Response;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;

import java.util.Date;

@Slf4j
@Component
public class MarketSettlementPort implements IMarketSettlementPort {

    @Value("${app.config.group-buy-market.source}")
    private String source;
    @Value("${app.config.group-buy-market.chanel}")
    private String chanel;

    private final IGroupBuyMarketService groupBuyMarketService;

    public MarketSettlementPort(IGroupBuyMarketService groupBuyMarketService) {
        this.groupBuyMarketService = groupBuyMarketService;
    }

    @Override
    public void settlementGroupBuyMarketPayOrder(String userId, String orderId, Date orderTime) {
        SettlementMarketPayOrderRequestDTO requestDTO = new SettlementMarketPayOrderRequestDTO();
        requestDTO.setSource(source);
        requestDTO.setChannel(chanel);
        requestDTO.setUserId(userId);
        requestDTO.setOutTradeNo(orderId);
        requestDTO.setOutTradeTime(orderTime);

        try {
            Call<Response<SettlementMarketPayOrderResponseDTO>> call = groupBuyMarketService.settlementMarketPayOrder(requestDTO);
            Response<SettlementMarketPayOrderResponseDTO> response = call.execute().body();
            log.info("营销结算{} requestDTO:{} responseDTO:{}", userId, JSON.toJSONString(requestDTO), JSON.toJSONString(response));
            if (null == response) {
                throw new IllegalStateException("营销结算响应为空");
            }
            if (!"0000".equals(response.getCode())) {
                throw new AppException(response.getCode(), response.getInfo());
            }
        } catch (Exception e) {
            log.error("营销结算失败{}", userId, e);
            throw new IllegalStateException("营销结算失败 userId:" + userId + " orderId:" + orderId, e);
        }
    }

    @Override
    public void settlementSeckillPayOrder(String userId, String orderId, Date orderTime) {
        SettlementSeckillOrderRequestDTO requestDTO = SettlementSeckillOrderRequestDTO.builder()
                .source(source)
                .channel(chanel)
                .userId(userId)
                .outTradeNo(orderId)
                .outTradeTime(orderTime)
                .build();

        try {
            Call<Response<SettlementSeckillOrderResponseDTO>> call = groupBuyMarketService.settlementSeckillOrder(requestDTO);
            Response<SettlementSeckillOrderResponseDTO> response = call.execute().body();
            log.info("秒杀结算{} requestDTO:{} responseDTO:{}", userId, JSON.toJSONString(requestDTO), JSON.toJSONString(response));
            if (null == response) {
                throw new IllegalStateException("秒杀结算响应为空");
            }
            if (!"0000".equals(response.getCode())) {
                throw new AppException(response.getCode(), response.getInfo());
            }
        } catch (Exception e) {
            log.error("秒杀结算失败{}", userId, e);
            throw new IllegalStateException("秒杀结算失败 userId:" + userId + " orderId:" + orderId, e);
        }
    }

}
