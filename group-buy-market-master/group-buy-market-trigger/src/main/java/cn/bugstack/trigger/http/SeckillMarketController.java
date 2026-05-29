package cn.bugstack.trigger.http;

import cn.bugstack.api.ISeckillMarketService;
import cn.bugstack.api.dto.LockSeckillOrderRequestDTO;
import cn.bugstack.api.dto.LockSeckillOrderResponseDTO;
import cn.bugstack.api.dto.QuerySeckillOrderResultRequestDTO;
import cn.bugstack.api.dto.RefundSeckillOrderRequestDTO;
import cn.bugstack.api.dto.RefundSeckillOrderResponseDTO;
import cn.bugstack.api.dto.SeckillMarketRequestDTO;
import cn.bugstack.api.dto.SeckillMarketResponseDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderRequestDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillRateLimitPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import cn.bugstack.trigger.support.StructuredBusinessLogger;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import cn.bugstack.wrench.rate.limiter.types.annotations.RateLimiterAccessInterceptor;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * Seckill market HTTP API.
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/seckill/")
public class SeckillMarketController implements ISeckillMarketService {

    @Resource
    private ISeckillService seckillService;
    @Resource
    private ISeckillRateLimitPort seckillRateLimitPort;
    @Resource
    private ISeckillMetricsPort seckillMetricsPort;
    @Resource
    private StructuredBusinessLogger businessLogger;
    @Autowired
    private HttpServletRequest httpServletRequest;

    @RequestMapping(value = "query_seckill_market_config", method = RequestMethod.POST)
    @Override
    public Response<SeckillMarketResponseDTO> querySeckillMarketConfig(@RequestBody SeckillMarketRequestDTO requestDTO) {
        try {
            log.debug("query seckill market config start requestDTO:{}", JSON.toJSONString(requestDTO));
            if (null == requestDTO
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || StringUtils.isBlank(requestDTO.getSource())
                    || StringUtils.isBlank(requestDTO.getChannel())
                    || StringUtils.isBlank(requestDTO.getGoodsId())
                    || null == requestDTO.getActivityId()) {
                return Response.<SeckillMarketResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillActivityEntity seckillActivityEntity = seckillService.querySeckillActivity(
                    requestDTO.getActivityId(),
                    requestDTO.getSource(),
                    requestDTO.getChannel(),
                    requestDTO.getGoodsId());

            SeckillMarketResponseDTO responseDTO = SeckillMarketResponseDTO.builder()
                    .activityId(seckillActivityEntity.getActivityId())
                    .activityName(seckillActivityEntity.getActivityName())
                    .goodsId(seckillActivityEntity.getGoodsId())
                    .goodsName(seckillActivityEntity.getGoodsName())
                    .originalPrice(seckillActivityEntity.getOriginalPrice())
                    .seckillPrice(seckillActivityEntity.getSeckillPrice())
                    .totalCount(seckillActivityEntity.getTotalCount())
                    .availableCount(seckillActivityEntity.getAvailableCount())
                    .lockCount(seckillActivityEntity.getLockCount())
                    .status(seckillActivityEntity.getStatus())
                    .startTime(seckillActivityEntity.getStartTime())
                    .endTime(seckillActivityEntity.getEndTime())
                    .build();

            return Response.<SeckillMarketResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("query seckill market config business error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<SeckillMarketResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query seckill market config error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<SeckillMarketResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RateLimiterAccessInterceptor(key = "userId", fallbackMethod = "lockSeckillOrderFallBack", permitsPerSecond = 2.0d, blacklistCount = 3)
    @RequestMapping(value = "lock_seckill_order", method = RequestMethod.POST)
    @Override
    public Response<LockSeckillOrderResponseDTO> lockSeckillOrder(@RequestBody LockSeckillOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            log.debug("lock seckill order start requestDTO:{}", JSON.toJSONString(requestDTO));
            if (null == requestDTO
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || StringUtils.isBlank(requestDTO.getSource())
                    || StringUtils.isBlank(requestDTO.getChannel())
                    || StringUtils.isBlank(requestDTO.getGoodsId())
                    || null == requestDTO.getActivityId()
                    || StringUtils.isBlank(requestDTO.getOutTradeNo())) {
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
                        .data(buildLockSeckillOrderResponse(existsOrder))
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
                        .data(buildLockSeckillOrderResponse(existsResult))
                        .build();
            }

            long lockStartNanos = System.nanoTime();
            if (!seckillRateLimitPort.tryAcquire(requestDTO.getActivityId(), requestDTO.getUserId(), getClientIp())) {
                seckillMetricsPort.recordRateLimited();
                seckillMetricsPort.recordLock(System.nanoTime() - lockStartNanos, "rate_limited");
                businessLogger.warn("seckill_lock_order", "rate_limited", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "activityId", requestDTO.getActivityId(),
                        "goodsId", requestDTO.getGoodsId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "clientIp", getClientIp(),
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
                    .data(buildLockSeckillOrderResponse(seckillOrderEntity))
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

    public Response<LockSeckillOrderResponseDTO> lockSeckillOrderFallBack(@RequestBody LockSeckillOrderRequestDTO requestDTO) {
        log.warn("lock seckill order rate limited userId:{} activityId:{}",
                null == requestDTO ? null : requestDTO.getUserId(),
                null == requestDTO ? null : requestDTO.getActivityId());
        return Response.<LockSeckillOrderResponseDTO>builder()
                .code(ResponseCode.RATE_LIMITER.getCode())
                .info(ResponseCode.RATE_LIMITER.getInfo())
                .build();
    }

    @RequestMapping(value = "query_seckill_order_result", method = RequestMethod.POST)
    @Override
    public Response<LockSeckillOrderResponseDTO> querySeckillOrderResult(@RequestBody QuerySeckillOrderResultRequestDTO requestDTO) {
        try {
            log.debug("query seckill order result start requestDTO:{}", JSON.toJSONString(requestDTO));
            if (null == requestDTO
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || null == requestDTO.getActivityId()
                    || StringUtils.isBlank(requestDTO.getOutTradeNo())) {
                return Response.<LockSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillOrderEntity seckillOrderEntity = seckillService.querySeckillResult(
                    requestDTO.getUserId(),
                    requestDTO.getActivityId(),
                    requestDTO.getOutTradeNo());

            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(buildLockSeckillOrderResponse(seckillOrderEntity))
                    .build();
        } catch (AppException e) {
            log.error("query seckill order result business error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query seckill order result error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "settlement_seckill_order", method = RequestMethod.POST)
    @Override
    public Response<SettlementSeckillOrderResponseDTO> settlementSeckillOrder(@RequestBody SettlementSeckillOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || StringUtils.isBlank(requestDTO.getOutTradeNo())) {
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
                    .data(SettlementSeckillOrderResponseDTO.builder()
                            .userId(entity.getUserId())
                            .activityId(entity.getActivityId())
                            .orderId(entity.getOrderId())
                            .outTradeNo(entity.getOutTradeNo())
                            .status(entity.getStatus())
                            .build())
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

    @RequestMapping(value = "refund_seckill_order", method = RequestMethod.POST)
    @Override
    public Response<RefundSeckillOrderResponseDTO> refundSeckillOrder(@RequestBody RefundSeckillOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || StringUtils.isBlank(requestDTO.getOutTradeNo())) {
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
                    .data(RefundSeckillOrderResponseDTO.builder()
                            .userId(entity.getUserId())
                            .activityId(entity.getActivityId())
                            .orderId(entity.getOrderId())
                            .outTradeNo(entity.getOutTradeNo())
                            .status(entity.getStatus())
                            .stockReleased(stockReleased)
                            .build())
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

    private LockSeckillOrderResponseDTO buildLockSeckillOrderResponse(SeckillOrderEntity seckillOrderEntity) {
        return LockSeckillOrderResponseDTO.builder()
                .orderId(seckillOrderEntity.getOrderId())
                .activityId(seckillOrderEntity.getActivityId())
                .goodsId(seckillOrderEntity.getGoodsId())
                .originalPrice(seckillOrderEntity.getOriginalPrice())
                .seckillPrice(seckillOrderEntity.getSeckillPrice())
                .status(seckillOrderEntity.getStatus())
                .resultStatus(seckillOrderEntity.getResultStatus())
                .message(seckillOrderEntity.getMessage())
                .build();
    }

    private String getClientIp() {
        String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = httpServletRequest.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return httpServletRequest.getRemoteAddr();
    }

}
