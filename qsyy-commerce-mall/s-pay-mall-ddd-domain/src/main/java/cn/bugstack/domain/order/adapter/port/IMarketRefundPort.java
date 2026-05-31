package cn.bugstack.domain.order.adapter.port;

public interface IMarketRefundPort {

    void refundGroupBuyMarketPayOrder(String userId, String orderId);

    void refundSeckillPayOrder(String userId, String orderId);

}
