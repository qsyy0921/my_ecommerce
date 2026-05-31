package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.infrastructure.adapter.support.GroupBuyLockRequestSupport;
import cn.bugstack.infrastructure.adapter.support.GroupBuyLockResultCacheSupport;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class TradeLockRequestPort implements ITradeLockRequestPort {

    @Resource
    private GroupBuyLockRequestSupport groupBuyLockRequestSupport;
    @Resource
    private GroupBuyLockResultCacheSupport groupBuyLockResultCacheSupport;

    @Override
    public MarketPayOrderEntity queryLockResult(String userId, String outTradeNo) {
        return groupBuyLockResultCacheSupport.query(userId, outTradeNo);
    }

    @Override
    public boolean tryAcquireLockRequest(String userId, String outTradeNo, Integer validTime) {
        return groupBuyLockRequestSupport.tryAcquire(userId, outTradeNo, validTime);
    }

    @Override
    public void releaseLockRequest(String userId, String outTradeNo) {
        groupBuyLockRequestSupport.release(userId, outTradeNo);
    }

    @Override
    public void cacheLockResult(String userId, String outTradeNo, MarketPayOrderEntity marketPayOrderEntity, Integer validTime) {
        groupBuyLockResultCacheSupport.cache(userId, outTradeNo, marketPayOrderEntity, validTime);
    }

    @Override
    public void removeLockResult(String userId, String outTradeNo) {
        groupBuyLockResultCacheSupport.remove(userId, outTradeNo);
    }

}
