package cn.bugstack.domain.order.service.processor;

import cn.bugstack.domain.order.adapter.port.IMarketSettlementPort;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.service.IOrderService;

import java.util.Collections;
import java.util.Date;

public class MarketSettlementReconcileProcessor {

    private final IMarketSettlementPort marketSettlementPort;
    private final IOrderService orderService;

    public MarketSettlementReconcileProcessor(IMarketSettlementPort marketSettlementPort,
                                              IOrderService orderService) {
        this.marketSettlementPort = marketSettlementPort;
        this.orderService = orderService;
    }

    public void settle(OrderEntity orderEntity) {
        Date payTime = null == orderEntity.getPayTime() ? new Date() : orderEntity.getPayTime();
        if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
            marketSettlementPort.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
            orderService.changeOrderMarketSettlement(Collections.singletonList(orderEntity.getOrderId()));
            return;
        }
        marketSettlementPort.settlementGroupBuyMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
    }

}
