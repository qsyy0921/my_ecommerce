package cn.bugstack.domain.order.adapter.port;

import cn.bugstack.domain.order.model.entity.MarketPayDiscountEntity;

public interface IMarketOrderLockPort {

    MarketPayDiscountEntity lockGroupBuyMarketPayOrder(String userId, String teamId, Long activityId, String productId, String orderId);

    MarketPayDiscountEntity lockSeckillPayOrder(String userId, Long activityId, String productId, String orderId);

}
