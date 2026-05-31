package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;

public interface ITradeLockRequestPort {

    MarketPayOrderEntity queryLockResult(String userId, String outTradeNo);

    boolean tryAcquireLockRequest(String userId, String outTradeNo, Integer validTime);

    void releaseLockRequest(String userId, String outTradeNo);

    void cacheLockResult(String userId, String outTradeNo, MarketPayOrderEntity marketPayOrderEntity, Integer validTime);

    void removeLockResult(String userId, String outTradeNo);

}
