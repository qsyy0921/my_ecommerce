package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.SettlementSeckillOrderRequestDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderResponseDTO;
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
public class SeckillSettlementSupport {

    @Resource
    private ISeckillService seckillService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    @Resource
    private SeckillRequestValidator requestValidator;

    @Resource
    private SeckillResponseAssembler responseAssembler;

    public Response<SettlementSeckillOrderResponseDTO> settlement(SettlementSeckillOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (!requestValidator.validSettlement(requestDTO)) {
                businessLogger.warn("seckill_settlement", "illegal_parameter", businessLogger.fields(
                        "userId", null == requestDTO ? null : requestDTO.getUserId(),
                        "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<SettlementSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillOrderEntity entity = seckillService.settlementSeckillOrder(requestDTO.getUserId(), requestDTO.getOutTradeNo());
            businessLogger.info("seckill_settlement", "success", businessLogger.fields(
                    "userId", requestDTO.getUserId(),
                    "activityId", entity.getActivityId(),
                    "orderId", entity.getOrderId(),
                    "outTradeNo", requestDTO.getOutTradeNo(),
                    "status", entity.getStatus(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<SettlementSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseAssembler.toSettlementResponse(entity))
                    .build();
        } catch (AppException e) {
            businessLogger.warn("seckill_settlement", "business_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "code", e.getCode(),
                    "info", e.getInfo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<SettlementSeckillOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("settlement seckill order error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            businessLogger.error("seckill_settlement", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<SettlementSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
