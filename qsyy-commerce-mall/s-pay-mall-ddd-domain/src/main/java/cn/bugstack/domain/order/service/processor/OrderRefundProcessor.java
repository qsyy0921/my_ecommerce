package cn.bugstack.domain.order.service.processor;

import cn.bugstack.domain.order.adapter.port.IMarketRefundPort;
import cn.bugstack.domain.order.adapter.port.IPayPort;
import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.model.valobj.OrderStatusVO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderRefundProcessor {

    private final IOrderRepository repository;
    private final IMarketRefundPort marketRefundPort;
    private final IRefundFlowPort refundFlowPort;
    private final IPayPort payPort;

    public OrderRefundProcessor(IOrderRepository repository,
                                IMarketRefundPort marketRefundPort,
                                IRefundFlowPort refundFlowPort,
                                IPayPort payPort) {
        this.repository = repository;
        this.marketRefundPort = marketRefundPort;
        this.refundFlowPort = refundFlowPort;
        this.payPort = payPort;
    }

    public boolean refundMarketOrder(String userId, String orderId) {
        OrderEntity orderEntity = queryOwnedOrder(userId, orderId, "退单");
        if (null == orderEntity) {
            return false;
        }

        String status = orderEntity.getOrderStatusVO().getCode();
        if (OrderStatusVO.CLOSE.getCode().equals(status)) {
            log.warn("退单失败，订单已关闭 userId:{} orderId:{} status:{}", userId, orderId, status);
            return false;
        }

        MarketTypeVO marketTypeVO = marketTypeOf(orderEntity);
        refundMarketSideIfNecessary(marketTypeVO, userId, orderId);

        if (OrderStatusVO.CREATE.getCode().equals(status) || OrderStatusVO.PAY_WAIT.getCode().equals(status)) {
            return closeUnpaidOrder(userId, orderId, orderEntity);
        }
        if (MarketTypeVO.SECKILL_MARKET.equals(marketTypeVO)) {
            return refundSeckillPaidOrder(userId, orderId, orderEntity, status);
        }
        return applyMarketRefund(userId, orderId, orderEntity);
    }

    public boolean refundPayOrder(String userId, String orderId) {
        OrderEntity orderEntity = queryOwnedOrder(userId, orderId, "退款");
        if (null == orderEntity) {
            return false;
        }
        if (OrderStatusVO.CLOSE.equals(orderEntity.getOrderStatusVO())) {
            log.info("退款幂等返回，订单已关闭 userId:{} orderId:{}", userId, orderId);
            return true;
        }

        if (!payPort.refund(orderEntity.getOrderId(), orderEntity.getPayAmount())) {
            return false;
        }

        boolean result = repository.refundOrder(userId, orderId);
        if (result) {
            refundFlowPort.recordRefund(orderEntity, "SUCCESS", "refund order");
        }
        return true;
    }

    private OrderEntity queryOwnedOrder(String userId, String orderId, String operation) {
        OrderEntity orderEntity = repository.queryOrderByUserIdAndOrderId(userId, orderId);
        if (null == orderEntity) {
            log.warn("{}失败，订单不存在或不属于该用户 userId:{} orderId:{}", operation, userId, orderId);
        }
        return orderEntity;
    }

    private MarketTypeVO marketTypeOf(OrderEntity orderEntity) {
        return MarketTypeVO.valueOf(null == orderEntity.getMarketType() ? MarketTypeVO.NO_MARKET.getCode() : orderEntity.getMarketType());
    }

    private void refundMarketSideIfNecessary(MarketTypeVO marketTypeVO, String userId, String orderId) {
        if (MarketTypeVO.GROUP_BUY_MARKET.equals(marketTypeVO)) {
            marketRefundPort.refundGroupBuyMarketPayOrder(userId, orderId);
        } else if (MarketTypeVO.SECKILL_MARKET.equals(marketTypeVO)) {
            marketRefundPort.refundSeckillPayOrder(userId, orderId);
        }
    }

    private boolean closeUnpaidOrder(String userId, String orderId, OrderEntity orderEntity) {
        boolean result = repository.refundOrder(userId, orderId);
        if (result) {
            refundFlowPort.recordRefund(orderEntity, "SUCCESS", "refund order");
        }
        return result;
    }

    private boolean refundSeckillPaidOrder(String userId, String orderId, OrderEntity orderEntity, String status) {
        boolean applied = OrderStatusVO.WAIT_REFUND.getCode().equals(status) || repository.refundMarketOrder(userId, orderId);
        if (!applied) {
            log.warn("秒杀退单申请失败 userId:{} orderId:{}", userId, orderId);
            return false;
        }
        if (!OrderStatusVO.WAIT_REFUND.getCode().equals(status)) {
            refundFlowPort.recordRefund(orderEntity, "APPLY", "market refund order");
        }
        return refundPayOrder(userId, orderId);
    }

    private boolean applyMarketRefund(String userId, String orderId, OrderEntity orderEntity) {
        boolean result = repository.refundMarketOrder(userId, orderId);
        if (result) {
            refundFlowPort.recordRefund(orderEntity, "APPLY", "market refund order");
            log.info("退单成功 userId:{} orderId:{}", userId, orderId);
        } else {
            log.warn("退单失败 userId:{} orderId:{}", userId, orderId);
        }
        return result;
    }

}
