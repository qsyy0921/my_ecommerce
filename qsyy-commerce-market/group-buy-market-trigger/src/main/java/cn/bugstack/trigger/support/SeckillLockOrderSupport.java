package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockSeckillOrderRequestDTO;
import cn.bugstack.api.dto.LockSeckillOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillRateLimitPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class SeckillLockOrderSupport {

    @Resource
    private ISeckillService seckillService;

    @Resource
    private ISeckillRateLimitPort seckillRateLimitPort;

    @Resource
    private ISeckillMetricsPort seckillMetricsPort;

    @Resource
    private StructuredBusinessLogger businessLogger;

    @Resource
    private SeckillRequestValidator requestValidator;

    @Resource
    private SeckillResponseAssembler responseAssembler;

    @Resource
    private ClientIpResolver clientIpResolver;

    public Response<LockSeckillOrderResponseDTO> lock(LockSeckillOrderRequestDTO requestDTO, HttpServletRequest httpServletRequest) {
        long startMillis = System.currentTimeMillis();
        try {
            log.debug("lock seckill order start requestDTO:{}", JSON.toJSONString(requestDTO));
            if (!requestValidator.validLockOrder(requestDTO)) {
                businessLogger.warn("seckill_lock_order", "illegal_parameter", businessLogger.fields(
                        "userId", null == requestDTO ? null : requestDTO.getUserId(),
                        "activityId", null == requestDTO ? null : requestDTO.getActivityId(),
                        "goodsId", null == requestDTO ? null : requestDTO.getGoodsId(),
                        "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillOrderEntity existsOrder = seckillService.querySeckillOrderByOutTradeNo(requestDTO.getUserId(), requestDTO.getOutTradeNo());
            if (null != existsOrder) {
                businessLogger.info("seckill_lock_order", "idempotent_db_hit", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "activityId", requestDTO.getActivityId(),
                        "goodsId", requestDTO.getGoodsId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "orderId", existsOrder.getOrderId(),
                        "resultStatus", existsOrder.getResultStatus(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(responseAssembler.toLockResponse(existsOrder))
                        .build();
            }

            SeckillOrderEntity existsResult = seckillService.querySeckillResult(requestDTO.getUserId(), requestDTO.getActivityId(), requestDTO.getOutTradeNo());
            if (null != existsResult && !SeckillOrderEntity.RESULT_NOT_FOUND.equals(existsResult.getResultStatus())) {
                businessLogger.info("seckill_lock_order", "idempotent_cache_hit", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "activityId", requestDTO.getActivityId(),
                        "goodsId", requestDTO.getGoodsId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "orderId", existsResult.getOrderId(),
                        "resultStatus", existsResult.getResultStatus(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(responseAssembler.toLockResponse(existsResult))
                        .build();
            }

            long lockStartNanos = System.nanoTime();
            String clientIp = clientIpResolver.resolve(httpServletRequest);
            if (!seckillRateLimitPort.tryAcquire(requestDTO.getActivityId(), requestDTO.getUserId(), clientIp)) {
                seckillMetricsPort.recordRateLimited();
                seckillMetricsPort.recordLock(System.nanoTime() - lockStartNanos, "rate_limited");
                businessLogger.warn("seckill_lock_order", "rate_limited", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "activityId", requestDTO.getActivityId(),
                        "goodsId", requestDTO.getGoodsId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "clientIp", clientIp,
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.RATE_LIMITER.getCode())
                        .info(ResponseCode.RATE_LIMITER.getInfo())
                        .build();
            }

            SeckillOrderEntity seckillOrderEntity;
            try {
                seckillOrderEntity = seckillService.lockSeckillOrder(
                        requestDTO.getUserId(),
                        requestDTO.getActivityId(),
                        requestDTO.getSource(),
                        requestDTO.getChannel(),
                        requestDTO.getGoodsId(),
                        requestDTO.getOutTradeNo());
                seckillMetricsPort.recordLock(System.nanoTime() - lockStartNanos, "success");
            } catch (AppException e) {
                seckillMetricsPort.recordLock(System.nanoTime() - lockStartNanos, "business_error");
                throw e;
            } catch (Exception e) {
                seckillMetricsPort.recordLock(System.nanoTime() - lockStartNanos, "system_error");
                throw e;
            }

            businessLogger.info("seckill_lock_order", "success", businessLogger.fields(
                    "userId", requestDTO.getUserId(),
                    "activityId", requestDTO.getActivityId(),
                    "goodsId", requestDTO.getGoodsId(),
                    "outTradeNo", requestDTO.getOutTradeNo(),
                    "orderId", seckillOrderEntity.getOrderId(),
                    "resultStatus", seckillOrderEntity.getResultStatus(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseAssembler.toLockResponse(seckillOrderEntity))
                    .build();
        } catch (AppException e) {
            log.error("lock seckill order business error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            businessLogger.warn("seckill_lock_order", "business_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "activityId", null == requestDTO ? null : requestDTO.getActivityId(),
                    "goodsId", null == requestDTO ? null : requestDTO.getGoodsId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "code", e.getCode(),
                    "info", e.getInfo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("lock seckill order error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            businessLogger.error("seckill_lock_order", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "activityId", null == requestDTO ? null : requestDTO.getActivityId(),
                    "goodsId", null == requestDTO ? null : requestDTO.getGoodsId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    public Response<LockSeckillOrderResponseDTO> fallback(LockSeckillOrderRequestDTO requestDTO) {
        log.warn("lock seckill order rate limited userId:{} activityId:{}",
                null == requestDTO ? null : requestDTO.getUserId(),
                null == requestDTO ? null : requestDTO.getActivityId());
        return Response.<LockSeckillOrderResponseDTO>builder()
                .code(ResponseCode.RATE_LIMITER.getCode())
                .info(ResponseCode.RATE_LIMITER.getInfo())
                .build();
    }

}
