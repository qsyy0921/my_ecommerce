package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.adapter.port.IPayPort;
import cn.bugstack.domain.order.adapter.port.IMarketOrderLockPort;
import cn.bugstack.domain.order.adapter.port.IProductQueryPort;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.aggregate.CreateOrderAggregate;
import cn.bugstack.domain.order.model.entity.MarketPayDiscountEntity;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.model.valobj.OrderStatusVO;
import cn.bugstack.domain.order.service.processor.OrderPaySuccessProcessor;
import cn.bugstack.domain.order.service.processor.OrderRefundProcessor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
public class OrderService extends AbstractOrderService {

    private final IPayPort payPort;
    private final OrderPaySuccessProcessor orderPaySuccessProcessor;
    private final OrderRefundProcessor orderRefundProcessor;

    public OrderService(IOrderRepository repository,
                        IProductQueryPort productQueryPort,
                        IMarketOrderLockPort marketOrderLockPort,
                        IPayPort payPort,
                        OrderPaySuccessProcessor orderPaySuccessProcessor,
                        OrderRefundProcessor orderRefundProcessor) {
        super(repository, productQueryPort, marketOrderLockPort);
        this.payPort = payPort;
        this.orderPaySuccessProcessor = orderPaySuccessProcessor;
        this.orderRefundProcessor = orderRefundProcessor;
    }

    @Override
    protected void doSaveOrder(CreateOrderAggregate orderAggregate) {
        repository.doSaveOrder(orderAggregate);
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
        orderPaySuccessProcessor.changeOrderPaySuccess(orderId, payTime, payChannel, channelTradeNo, rawMessage);
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
        orderPaySuccessProcessor.changeOrderMarketSettlement(outTradeNoList);
    }

    @Override
    public boolean refundMarketOrder(String userId, String orderId) {
        return orderRefundProcessor.refundMarketOrder(userId, orderId);
    }

    @Override
    public boolean refundPayOrder(String userId, String orderId) {
        return orderRefundProcessor.refundPayOrder(userId, orderId);
    }

}
