package cn.bugstack.domain.order.service.processor;

import cn.bugstack.domain.order.adapter.port.IMarketSettlementPort;
import cn.bugstack.domain.order.adapter.port.IOrderPaySuccessMessagePort;
import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
public class OrderPaySuccessProcessor {

    private final IOrderRepository repository;
    private final IMarketSettlementPort marketSettlementPort;
    private final IPaymentFlowPort paymentFlowPort;
    private final IOrderPaySuccessMessagePort orderPaySuccessMessagePort;
    private final IDomainTaskExecutor domainTaskExecutor;

    public OrderPaySuccessProcessor(IOrderRepository repository,
                                    IMarketSettlementPort marketSettlementPort,
                                    IPaymentFlowPort paymentFlowPort,
                                    IOrderPaySuccessMessagePort orderPaySuccessMessagePort,
                                    IDomainTaskExecutor domainTaskExecutor) {
        this.repository = repository;
        this.marketSettlementPort = marketSettlementPort;
        this.paymentFlowPort = paymentFlowPort;
        this.orderPaySuccessMessagePort = orderPaySuccessMessagePort;
        this.domainTaskExecutor = domainTaskExecutor;
    }

    public void changeOrderPaySuccess(String orderId, Date payTime, String payChannel, String channelTradeNo, String rawMessage) {
        OrderEntity orderEntity = repository.queryOrderByOrderId(orderId);
        if (null == orderEntity) {
            return;
        }

        if (MarketTypeVO.GROUP_BUY_MARKET.getCode().equals(orderEntity.getMarketType())) {
            changeGroupBuyPaySuccess(orderEntity, payTime, payChannel, channelTradeNo, rawMessage);
        } else if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
            changeSeckillPaySuccess(orderEntity, payTime, payChannel, channelTradeNo, rawMessage);
        } else {
            changeNormalPaySuccess(orderEntity, payTime, payChannel, channelTradeNo, rawMessage);
        }
    }

    public void changeOrderMarketSettlement(List<String> outTradeNoList) {
        repository.changeOrderMarketSettlement(outTradeNoList);
        orderPaySuccessMessagePort.publishAll(outTradeNoList);
    }

    private void changeGroupBuyPaySuccess(OrderEntity orderEntity, Date payTime, String payChannel, String channelTradeNo, String rawMessage) {
        boolean changed = repository.changeMarketOrderPaySuccess(orderEntity.getOrderId(), payTime);
        paymentFlowPort.recordPaySuccess(orderEntity, payChannel, channelTradeNo, rawMessage, payTime);
        if (changed) {
            asyncSettlementMarketPayOrder(orderEntity, payTime);
        } else {
            log.info("payment callback idempotent hit, skip market settlement orderId:{}", orderEntity.getOrderId());
        }
    }

    private void changeSeckillPaySuccess(OrderEntity orderEntity, Date payTime, String payChannel, String channelTradeNo, String rawMessage) {
        boolean changed = repository.changeMarketOrderPaySuccess(orderEntity.getOrderId(), payTime);
        paymentFlowPort.recordPaySuccess(orderEntity, payChannel, channelTradeNo, rawMessage, payTime);
        if (changed) {
            asyncSettlementSeckillPayOrder(orderEntity, payTime);
        } else {
            log.info("payment callback idempotent hit, skip seckill settlement orderId:{}", orderEntity.getOrderId());
        }
    }

    private void changeNormalPaySuccess(OrderEntity orderEntity, Date payTime, String payChannel, String channelTradeNo, String rawMessage) {
        boolean changed = repository.changeOrderPaySuccess(orderEntity.getOrderId(), payTime);
        if (changed) {
            orderPaySuccessMessagePort.publish(orderEntity.getOrderId());
        }
        paymentFlowPort.recordPaySuccess(orderEntity, payChannel, channelTradeNo, rawMessage, payTime);
        if (!changed) {
            log.info("payment callback idempotent hit, skip normal order message orderId:{}", orderEntity.getOrderId());
        }
    }

    private void asyncSettlementMarketPayOrder(OrderEntity orderEntity, Date payTime) {
        domainTaskExecutor.execute(() -> {
            try {
                marketSettlementPort.settlementGroupBuyMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
            } catch (Exception e) {
                log.error("async market settlement failed, wait reconciliation job userId:{} orderId:{}",
                        orderEntity.getUserId(), orderEntity.getOrderId(), e);
            }
        });
    }

    private void asyncSettlementSeckillPayOrder(OrderEntity orderEntity, Date payTime) {
        domainTaskExecutor.execute(() -> {
            try {
                marketSettlementPort.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                changeOrderMarketSettlement(Collections.singletonList(orderEntity.getOrderId()));
            } catch (Exception e) {
                log.error("async seckill settlement failed, wait reconciliation job userId:{} orderId:{}",
                        orderEntity.getUserId(), orderEntity.getOrderId(), e);
            }
        });
    }

}
