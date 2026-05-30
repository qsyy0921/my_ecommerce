package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.adapter.port.IPayPort;
import cn.bugstack.domain.order.adapter.port.IProductPort;
import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.aggregate.CreateOrderAggregate;
import cn.bugstack.domain.order.model.entity.MarketPayDiscountEntity;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.model.valobj.OrderStatusVO;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
public class OrderService extends AbstractOrderService {

    private final IDomainTaskExecutor domainTaskExecutor;
    private final IPayPort payPort;
    private final IPaymentFlowPort paymentFlowPort;
    private final IRefundFlowPort refundFlowPort;

    public OrderService(IOrderRepository repository,
                        IProductPort port,
                        IPayPort payPort,
                        IPaymentFlowPort paymentFlowPort,
                        IRefundFlowPort refundFlowPort,
                        IDomainTaskExecutor domainTaskExecutor) {
        super(repository, port);
        this.payPort = payPort;
        this.paymentFlowPort = paymentFlowPort;
        this.refundFlowPort = refundFlowPort;
        this.domainTaskExecutor = domainTaskExecutor;
    }

    @Override
    protected void doSaveOrder(CreateOrderAggregate orderAggregate) {
        repository.doSaveOrder(orderAggregate);
    }

    @Override
    protected MarketPayDiscountEntity lockMarketPayOrder(String userId, String teamId, Long activityId, String productId, String orderId) {
        return port.lockMarketPayOrder(userId, teamId, activityId, productId, orderId);
    }

    @Override
    protected MarketPayDiscountEntity lockSeckillPayOrder(String userId, Long activityId, String productId, String orderId) {
        return port.lockSeckillPayOrder(userId, activityId, productId, orderId);
    }

    @Override
    protected PayOrderEntity doPrepayOrder(String userId, String productId, String productName, String orderId, BigDecimal totalAmount, String payChannel) {
        return doPrepayOrder(userId, productId, productName, orderId, totalAmount, null, payChannel);
    }

    @Override
    protected PayOrderEntity doPrepayOrder(String userId, String productId, String productName, String orderId, BigDecimal totalAmount, MarketPayDiscountEntity marketPayDiscountEntity, String payChannel) {
        // 支付金额
        BigDecimal payAmount = null == marketPayDiscountEntity ? totalAmount : marketPayDiscountEntity.getPayPrice();

        String form = payPort.createPayForm(orderId, payAmount, productName, payChannel);

        PayOrderEntity payOrderEntity = new PayOrderEntity();
        payOrderEntity.setOrderId(orderId);
        payOrderEntity.setPayUrl(form);
        payOrderEntity.setOrderStatus(OrderStatusVO.PAY_WAIT);

        // 营销信息
        payOrderEntity.setMarketType(null == marketPayDiscountEntity ? MarketTypeVO.NO_MARKET.getCode() : marketPayDiscountEntity.getMarketType());
        payOrderEntity.setMarketDeductionAmount(null == marketPayDiscountEntity ? BigDecimal.ZERO : marketPayDiscountEntity.getDeductionPrice());
        payOrderEntity.setPayAmount(payAmount);

        repository.updateOrderPayInfo(payOrderEntity);

        return payOrderEntity;
    }

    @Override
    public void changeOrderPaySuccess(String orderId, Date payTime) {
        changeOrderPaySuccess(orderId, payTime, "unknown", null, null);
    }

    @Override
    public void changeOrderPaySuccess(String orderId, Date payTime, String payChannel, String channelTradeNo, String rawMessage) {
        OrderEntity orderEntity = repository.queryOrderByOrderId(orderId);
        if (null == orderEntity) return;

        if (MarketTypeVO.GROUP_BUY_MARKET.getCode().equals(orderEntity.getMarketType())) {
            boolean changed = repository.changeMarketOrderPaySuccess(orderId, payTime);
            paymentFlowPort.recordPaySuccess(orderEntity, payChannel, channelTradeNo, rawMessage, payTime);
            if (changed) {
                asyncSettlementMarketPayOrder(orderEntity, payTime);
            } else {
                log.info("payment callback idempotent hit, skip market settlement orderId:{}", orderId);
            }
        } else if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
            boolean changed = repository.changeMarketOrderPaySuccess(orderId, payTime);
            paymentFlowPort.recordPaySuccess(orderEntity, payChannel, channelTradeNo, rawMessage, payTime);
            if (changed) {
                asyncSettlementSeckillPayOrder(orderEntity, payTime);
            } else {
                log.info("payment callback idempotent hit, skip seckill settlement orderId:{}", orderId);
            }
        } else {
            boolean changed = repository.changeOrderPaySuccess(orderId, payTime);
            paymentFlowPort.recordPaySuccess(orderEntity, payChannel, channelTradeNo, rawMessage, payTime);
            if (!changed) {
                log.info("payment callback idempotent hit, skip normal order message orderId:{}", orderId);
            }
        }

    }

    private void asyncSettlementMarketPayOrder(OrderEntity orderEntity, Date payTime) {
        domainTaskExecutor.execute(() -> {
            try {
                port.settlementMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
            } catch (Exception e) {
                log.error("async market settlement failed, wait reconciliation job userId:{} orderId:{}",
                        orderEntity.getUserId(), orderEntity.getOrderId(), e);
            }
        });
    }

    private void asyncSettlementSeckillPayOrder(OrderEntity orderEntity, Date payTime) {
        domainTaskExecutor.execute(() -> {
            try {
                port.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                repository.changeOrderMarketSettlement(Collections.singletonList(orderEntity.getOrderId()));
            } catch (Exception e) {
                log.error("async seckill settlement failed, wait reconciliation job userId:{} orderId:{}",
                        orderEntity.getUserId(), orderEntity.getOrderId(), e);
            }
        });
    }

    @Override
    public List<String> queryNoPayNotifyOrder() {
        return repository.queryNoPayNotifyOrder();
    }

    @Override
    public List<String> queryTimeoutCloseOrderList() {
        return repository.queryTimeoutCloseOrderList();
    }

    @Override
    public boolean changeOrderClose(String orderId) {
        return repository.changeOrderClose(orderId);
    }

    @Override
    public void changeOrderMarketSettlement(List<String> outTradeNoList) {
        repository.changeOrderMarketSettlement(outTradeNoList);
    }

    @Override
    public boolean refundMarketOrder(String userId, String orderId) {
        // 1. 查询订单信息，验证订单是否存在且属于该用户
        OrderEntity orderEntity = repository.queryOrderByUserIdAndOrderId(userId, orderId);
        if (null == orderEntity) {
            log.warn("退单失败，订单不存在或不属于该用户 userId:{} orderId:{}", userId, orderId);
            return false;
        }

        // 2. 检查订单状态，只有create、pay_wait、pay_success、deal_done状态的订单可以退单
        String status = orderEntity.getOrderStatusVO().getCode();
        if (OrderStatusVO.CLOSE.getCode().equals(status)) {
            log.warn("退单失败，订单已关闭 userId:{} orderId:{} status:{}", userId, orderId, status);
            return false;
        }

        MarketTypeVO marketTypeVO = MarketTypeVO.valueOf(null == orderEntity.getMarketType() ? MarketTypeVO.NO_MARKET.getCode() : orderEntity.getMarketType());

        // 3. 对于营销类型的单子，先调用营销侧恢复对应库存和订单状态
        if (MarketTypeVO.GROUP_BUY_MARKET.equals(marketTypeVO)) {
            port.refundMarketPayOrder(userId, orderId);
        } else if (MarketTypeVO.SECKILL_MARKET.equals(marketTypeVO)) {
            port.refundSeckillPayOrder(userId, orderId);
        }

        // 4. 执行退单操作；CREATE 新创建订单，不需要退款
        if (OrderStatusVO.CREATE.getCode().equals(status) || OrderStatusVO.PAY_WAIT.getCode().equals(status)) {
            boolean result = repository.refundOrder(userId, orderId);
            if (result) {
                refundFlowPort.recordRefund(orderEntity, "SUCCESS", "refund order");
            }
            return result;
        } else if (MarketTypeVO.SECKILL_MARKET.equals(marketTypeVO)) {
            boolean applied = OrderStatusVO.WAIT_REFUND.getCode().equals(status) || repository.refundMarketOrder(userId, orderId);
            if (!applied) {
                log.warn("秒杀退单申请失败 userId:{} orderId:{}", userId, orderId);
                return false;
            }
            if (!OrderStatusVO.WAIT_REFUND.getCode().equals(status)) {
                refundFlowPort.recordRefund(orderEntity, "APPLY", "market refund order");
            }
            return refundPayOrder(userId, orderId);
        } else {
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

    @Override
    public boolean refundPayOrder(String userId, String orderId) {
        // 1. 查询订单信息，验证订单是否存在且属于该用户
        OrderEntity orderEntity = repository.queryOrderByUserIdAndOrderId(userId, orderId);
        if (null == orderEntity) {
            log.warn("退款失败，订单不存在或不属于该用户 userId:{} orderId:{}", userId, orderId);
            return false;
        }
        if (OrderStatusVO.CLOSE.equals(orderEntity.getOrderStatusVO())) {
            log.info("退款幂等返回，订单已关闭 userId:{} orderId:{}", userId, orderId);
            return true;
        }

        if (!payPort.refund(orderEntity.getOrderId(), orderEntity.getPayAmount())) return false;

        // 状态变更
        boolean result = repository.refundOrder(userId, orderId);
        if (result) {
            refundFlowPort.recordRefund(orderEntity, "SUCCESS", "refund order");
        }

        return true;
    }

}
