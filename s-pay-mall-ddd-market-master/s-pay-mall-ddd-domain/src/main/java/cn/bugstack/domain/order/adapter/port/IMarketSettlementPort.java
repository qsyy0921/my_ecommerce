package cn.bugstack.domain.order.adapter.port;

import java.util.Date;

public interface IMarketSettlementPort {

    void settlementGroupBuyMarketPayOrder(String userId, String orderId, Date orderTime);

    void settlementSeckillPayOrder(String userId, String orderId, Date orderTime);

}
