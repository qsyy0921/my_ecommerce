package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.CreatePayRequestDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.entity.ShopCartEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class MallPayOrderCreateSupport {

    @Resource
    private IOrderService orderService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    public Response<String> create(CreatePayRequestDTO request) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == request) {
                businessLogger.warn("mall_create_pay_order", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }

            String userId = request.getUserId();
            String productId = request.getProductId();
            log.info("商品下单，根据商品ID创建支付单开始 userId:{} productId:{}", userId, productId);

            PayOrderEntity payOrderEntity = orderService.createOrder(ShopCartEntity.builder()
                    .userId(userId)
                    .productId(productId)
                    .teamId(request.getTeamId())
                    .marketTypeVO(MarketTypeVO.valueOf(request.getMarketType()))
                    .activityId(request.getActivityId())
                    .payChannel(request.getPayChannel())
                    .build());

            log.info("商品下单，根据商品ID创建支付单完成 userId:{} productId:{} orderId:{}", userId, productId, payOrderEntity.getOrderId());
            businessLogger.info("mall_create_pay_order", "success", businessLogger.fields(
                    "userId", userId,
                    "productId", productId,
                    "orderId", payOrderEntity.getOrderId(),
                    "teamId", request.getTeamId(),
                    "activityId", request.getActivityId(),
                    "marketType", request.getMarketType(),
                    "payChannel", request.getPayChannel(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(payOrderEntity.getPayUrl())
                    .build();
        } catch (Exception e) {
            log.error("商品下单，根据商品ID创建支付单失败 userId:{} productId:{}",
                    null == request ? null : request.getUserId(),
                    null == request ? null : request.getProductId(), e);
            businessLogger.error("mall_create_pay_order", "system_error", businessLogger.fields(
                    "userId", null == request ? null : request.getUserId(),
                    "productId", null == request ? null : request.getProductId(),
                    "teamId", null == request ? null : request.getTeamId(),
                    "activityId", null == request ? null : request.getActivityId(),
                    "marketType", null == request ? null : request.getMarketType(),
                    "payChannel", null == request ? null : request.getPayChannel(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return unError();
        }
    }

    private Response<String> illegalParameter() {
        return Response.<String>builder()
                .code(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(Constants.ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

    private Response<String> unError() {
        return Response.<String>builder()
                .code(Constants.ResponseCode.UN_ERROR.getCode())
                .info(Constants.ResponseCode.UN_ERROR.getInfo())
                .build();
    }

}
