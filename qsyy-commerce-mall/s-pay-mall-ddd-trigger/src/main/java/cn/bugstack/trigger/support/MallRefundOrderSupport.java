package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.RefundOrderRequestDTO;
import cn.bugstack.api.dto.RefundOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class MallRefundOrderSupport {

    @Resource
    private IOrderService orderService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    public Response<RefundOrderResponseDTO> refund(RefundOrderRequestDTO request) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == request) {
                businessLogger.warn("mall_refund_order", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }

            String userId = request.getUserId();
            String orderId = request.getOrderId();
            log.info("用户退单开始 userId:{} orderId:{}", userId, orderId);

            boolean success = orderService.refundMarketOrder(userId, orderId);
            RefundOrderResponseDTO responseDTO = response(success, orderId,
                    success ? "退单成功" : "退单失败，订单不存在、已关闭或不属于该用户");

            log.info("用户退单完成 userId:{} orderId:{} success:{}", userId, orderId, success);
            businessLogger.info("mall_refund_order", success ? "success" : "rejected", businessLogger.fields(
                    "userId", userId,
                    "orderId", orderId,
                    "success", success,
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<RefundOrderResponseDTO>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("用户退单失败 userId:{} orderId:{}",
                    null == request ? null : request.getUserId(),
                    null == request ? null : request.getOrderId(), e);
            businessLogger.error("mall_refund_order", "system_error", businessLogger.fields(
                    "userId", null == request ? null : request.getUserId(),
                    "orderId", null == request ? null : request.getOrderId(),
                    "costMs", System.currentTimeMillis() - startMillis), e);

            return Response.<RefundOrderResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .data(response(false, null == request ? null : request.getOrderId(), "退单失败，系统异常"))
                    .build();
        }
    }

    private Response<RefundOrderResponseDTO> illegalParameter() {
        return Response.<RefundOrderResponseDTO>builder()
                .code(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(Constants.ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

    private RefundOrderResponseDTO response(boolean success, String orderId, String message) {
        RefundOrderResponseDTO responseDTO = new RefundOrderResponseDTO();
        responseDTO.setSuccess(success);
        responseDTO.setOrderId(orderId);
        responseDTO.setMessage(message);
        return responseDTO;
    }

}
