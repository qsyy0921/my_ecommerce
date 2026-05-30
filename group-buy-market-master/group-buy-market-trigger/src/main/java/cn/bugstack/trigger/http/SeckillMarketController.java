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
import cn.bugstack.trigger.support.SeckillLockOrderSupport;
import cn.bugstack.trigger.support.SeckillMarketConfigQuerySupport;
import cn.bugstack.trigger.support.SeckillOrderResultQuerySupport;
import cn.bugstack.trigger.support.SeckillRefundSupport;
import cn.bugstack.trigger.support.SeckillSettlementSupport;
import cn.bugstack.wrench.rate.limiter.types.annotations.RateLimiterAccessInterceptor;
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
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/seckill/")
public class SeckillMarketController implements ISeckillMarketService {

    @Resource
    private SeckillMarketConfigQuerySupport marketConfigQuerySupport;

    @Resource
    private SeckillLockOrderSupport lockOrderSupport;

    @Resource
    private SeckillOrderResultQuerySupport orderResultQuerySupport;

    @Resource
    private SeckillSettlementSupport settlementSupport;

    @Resource
    private SeckillRefundSupport refundSupport;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @RequestMapping(value = "query_seckill_market_config", method = RequestMethod.POST)
    @Override
    public Response<SeckillMarketResponseDTO> querySeckillMarketConfig(@RequestBody SeckillMarketRequestDTO requestDTO) {
        return marketConfigQuerySupport.query(requestDTO);
    }

    @RateLimiterAccessInterceptor(key = "userId", fallbackMethod = "lockSeckillOrderFallBack", permitsPerSecond = 2.0d, blacklistCount = 3)
    @RequestMapping(value = "lock_seckill_order", method = RequestMethod.POST)
    @Override
    public Response<LockSeckillOrderResponseDTO> lockSeckillOrder(@RequestBody LockSeckillOrderRequestDTO requestDTO) {
        return lockOrderSupport.lock(requestDTO, httpServletRequest);
    }

    public Response<LockSeckillOrderResponseDTO> lockSeckillOrderFallBack(@RequestBody LockSeckillOrderRequestDTO requestDTO) {
        return lockOrderSupport.fallback(requestDTO);
    }

    @RequestMapping(value = "query_seckill_order_result", method = RequestMethod.POST)
    @Override
    public Response<LockSeckillOrderResponseDTO> querySeckillOrderResult(@RequestBody QuerySeckillOrderResultRequestDTO requestDTO) {
        return orderResultQuerySupport.query(requestDTO);
    }

    @RequestMapping(value = "settlement_seckill_order", method = RequestMethod.POST)
    @Override
    public Response<SettlementSeckillOrderResponseDTO> settlementSeckillOrder(@RequestBody SettlementSeckillOrderRequestDTO requestDTO) {
        return settlementSupport.settlement(requestDTO);
    }

    @RequestMapping(value = "refund_seckill_order", method = RequestMethod.POST)
    @Override
    public Response<RefundSeckillOrderResponseDTO> refundSeckillOrder(@RequestBody RefundSeckillOrderRequestDTO requestDTO) {
        return refundSupport.refund(requestDTO);
    }

}
