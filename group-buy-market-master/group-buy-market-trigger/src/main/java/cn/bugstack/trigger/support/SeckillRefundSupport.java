package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.RefundSeckillOrderRequestDTO;
import cn.bugstack.api.dto.RefundSeckillOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillRefundSupport {

    @Resource
    private ISeckillService seckillService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    @Resource
    private SeckillRequestValidator requestValidator;

    @Resource
    private SeckillResponseAssembler responseAssembler;

    public Response<RefundSeckillOrderResponseDTO> refund(RefundSeckillOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (!requestValidator.validRefund(requestDTO)) {
                businessLogger.warn("seckill_refund", "illegal_parameter", businessLogger.fields(
                        "userId", null == requestDTO ? null : requestDTO.getUserId(),
                        "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<RefundSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillOrderEntity before = seckillService.querySeckillOrderByOutTradeNo(requestDTO.getUserId(), requestDTO.getOutTradeNo());
            SeckillOrderEntity entity = seckillService.refundSeckillOrder(
                    requestDTO.getUserId(),
                    requestDTO.getOutTradeNo(),
                    requestDTO.getRefundReason());
            boolean stockReleased = null != before && null != entity
                    && null != before.getStatus()
                    && !before.getStatus().equals(entity.getStatus());
            businessLogger.info("seckill_refund", "success", businessLogger.fields(
                    "userId", requestDTO.getUserId(),
                    "activityId", entity.getActivityId(),
                    "orderId", entity.getOrderId(),
                    "outTradeNo", requestDTO.getOutTradeNo(),
                    "fromStatus", null == before ? null : before.getStatus(),
                    "toStatus", entity.getStatus(),
                    "stockReleased", stockReleased,
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<RefundSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseAssembler.toRefundResponse(entity, stockReleased))
                    .build();
        } catch (AppException e) {
            businessLogger.warn("seckill_refund", "business_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "code", e.getCode(),
                    "info", e.getInfo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<RefundSeckillOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("refund seckill order error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            businessLogger.error("seckill_refund", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<RefundSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
