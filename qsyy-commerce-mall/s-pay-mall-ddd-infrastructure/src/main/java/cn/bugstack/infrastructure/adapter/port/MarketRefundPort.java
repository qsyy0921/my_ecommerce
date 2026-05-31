package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IMarketRefundPort;
import cn.bugstack.infrastructure.gateway.IGroupBuyMarketService;
import cn.bugstack.infrastructure.gateway.dto.RefundMarketPayOrderRequestDTO;
import cn.bugstack.infrastructure.gateway.dto.RefundMarketPayOrderResponseDTO;
import cn.bugstack.infrastructure.gateway.dto.RefundSeckillOrderRequestDTO;
import cn.bugstack.infrastructure.gateway.dto.RefundSeckillOrderResponseDTO;
import cn.bugstack.infrastructure.gateway.response.Response;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;

@Slf4j
@Component
public class MarketRefundPort implements IMarketRefundPort {

    @Value("${app.config.group-buy-market.source}")
    private String source;
    @Value("${app.config.group-buy-market.chanel}")
    private String chanel;

    private final IGroupBuyMarketService groupBuyMarketService;

    public MarketRefundPort(IGroupBuyMarketService groupBuyMarketService) {
        this.groupBuyMarketService = groupBuyMarketService;
    }

    @Override
    public void refundGroupBuyMarketPayOrder(String userId, String orderId) {
        RefundMarketPayOrderRequestDTO requestDTO = new RefundMarketPayOrderRequestDTO();
        requestDTO.setSource(source);
        requestDTO.setChannel(chanel);
        requestDTO.setUserId(userId);
        requestDTO.setOutTradeNo(orderId);

        try {
            Call<Response<RefundMarketPayOrderResponseDTO>> call = groupBuyMarketService.refundMarketPayOrder(requestDTO);
            Response<RefundMarketPayOrderResponseDTO> response = call.execute().body();
            log.info("营销退单{} requestDTO:{} responseDTO:{}", userId, JSON.toJSONString(requestDTO), JSON.toJSONString(response));
            if (null == response) {
                throw new IllegalStateException("营销退单响应为空");
            }
            if (!"0000".equals(response.getCode())) {
                throw new AppException(response.getCode(), response.getInfo());
            }
        } catch (Exception e) {
            log.error("营销退单失败{}", userId, e);
            throw new IllegalStateException("营销退单失败 userId:" + userId + " orderId:" + orderId, e);
        }
    }

    @Override
    public void refundSeckillPayOrder(String userId, String orderId) {
        RefundSeckillOrderRequestDTO requestDTO = RefundSeckillOrderRequestDTO.builder()
                .source(source)
                .channel(chanel)
                .userId(userId)
                .outTradeNo(orderId)
                .refundReason("mall refund order")
                .build();

        try {
            Call<Response<RefundSeckillOrderResponseDTO>> call = groupBuyMarketService.refundSeckillOrder(requestDTO);
            Response<RefundSeckillOrderResponseDTO> response = call.execute().body();
            log.info("秒杀退单{} requestDTO:{} responseDTO:{}", userId, JSON.toJSONString(requestDTO), JSON.toJSONString(response));
            if (null == response) {
                throw new IllegalStateException("秒杀退单响应为空");
            }
            if (!"0000".equals(response.getCode())) {
                throw new AppException(response.getCode(), response.getInfo());
            }
        } catch (Exception e) {
            log.error("秒杀退单失败{}", userId, e);
            throw new IllegalStateException("秒杀退单失败 userId:" + userId + " orderId:" + orderId, e);
        }
    }

}
