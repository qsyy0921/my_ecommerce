package cn.bugstack.test.domain;

import cn.bugstack.domain.order.adapter.port.IMarketSettlementPort;
import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.aggregate.CreateOrderAggregate;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.domain.order.model.entity.ShopCartEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.model.valobj.ReconcileCaseStatusVO;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.domain.order.service.OrderReconcileService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class OrderReconcileServiceReplayContractTest {

    private static final String USER_ID = "user_001";
    private static final String ORDER_ID = "order_001";
    private static final String MESSAGE_ID = "message_001";
    private static final String OPERATOR = "tester";

    @Test
    public void marketSettlementTimeoutReplayShouldCallGroupBuySettlementAndConfirmCase() {
        Fixture fixture = new Fixture();
        fixture.orderRepository.orderEntity = order(MarketTypeVO.GROUP_BUY_MARKET.getCode());

        boolean result = fixture.service.replayReconcileCase("MARKET_SETTLEMENT_TIMEOUT:" + ORDER_ID, OPERATOR);

        Assert.assertTrue(result);
        Assert.assertEquals(1, fixture.marketSettlementPort.groupBuySettlementCalls);
        Assert.assertEquals(0, fixture.marketSettlementPort.seckillSettlementCalls);
        Assert.assertEquals(1, fixture.reconcileRepository.confirmCalls);
        Assert.assertEquals(ReconcileCaseStatusVO.CONFIRMED.getCode(), fixture.reconcileRepository.lastHandledStatus);
    }

    @Test
    public void marketSettlementTimeoutReplayShouldCallSeckillSettlementAndMarkMallSettlement() {
        Fixture fixture = new Fixture();
        fixture.orderRepository.orderEntity = order(MarketTypeVO.SECKILL_MARKET.getCode());

        boolean result = fixture.service.replayReconcileCase("MARKET_SETTLEMENT_TIMEOUT:" + ORDER_ID, OPERATOR);

        Assert.assertTrue(result);
        Assert.assertEquals(0, fixture.marketSettlementPort.groupBuySettlementCalls);
        Assert.assertEquals(1, fixture.marketSettlementPort.seckillSettlementCalls);
        Assert.assertEquals(1, fixture.orderService.marketSettlementCalls);
        Assert.assertEquals(1, fixture.reconcileRepository.confirmCalls);
    }

    @Test
    public void payWaitTimeoutReplayShouldCloseMallOrderAndConfirmCase() {
        Fixture fixture = new Fixture();

        boolean result = fixture.service.replayReconcileCase("PAY_WAIT_TIMEOUT:" + ORDER_ID, OPERATOR);

        Assert.assertTrue(result);
        Assert.assertEquals(1, fixture.orderRepository.closeCalls);
        Assert.assertEquals(1, fixture.reconcileRepository.confirmCalls);
    }

    @Test
    public void refundTimeoutReplayShouldCallRefundPayOrderAndConfirmCase() {
        Fixture fixture = new Fixture();
        fixture.orderRepository.orderEntity = order(MarketTypeVO.GROUP_BUY_MARKET.getCode());

        boolean result = fixture.service.replayReconcileCase("REFUND_TIMEOUT:" + ORDER_ID, OPERATOR);

        Assert.assertTrue(result);
        Assert.assertEquals(1, fixture.orderService.refundPayOrderCalls);
        Assert.assertEquals(1, fixture.reconcileRepository.confirmCalls);
    }

    @Test
    public void mqConsumeFailReplayShouldReplayOriginalMqMessageAndConfirmCase() {
        Fixture fixture = new Fixture();

        boolean result = fixture.service.replayReconcileCase("MQ_CONSUME_FAIL:" + MESSAGE_ID, OPERATOR);

        Assert.assertTrue(result);
        Assert.assertEquals(1, fixture.reconcileRepository.mqReplayCalls);
        Assert.assertEquals(MESSAGE_ID, fixture.reconcileRepository.lastMessageId);
        Assert.assertEquals(1, fixture.reconcileRepository.confirmCalls);
    }

    @Test
    public void nonOpenCaseShouldNotReplay() {
        Fixture fixture = new Fixture();
        fixture.reconcileRepository.caseStatus = ReconcileCaseStatusVO.CLOSED.getCode();

        boolean result = fixture.service.replayReconcileCase("MARKET_SETTLEMENT_TIMEOUT:" + ORDER_ID, OPERATOR);

        Assert.assertFalse(result);
        Assert.assertEquals(0, fixture.marketSettlementPort.groupBuySettlementCalls);
        Assert.assertEquals(0, fixture.reconcileRepository.confirmCalls);
    }

    @Test
    public void replayFailureShouldRemarkCaseWithoutConfirming() {
        Fixture fixture = new Fixture();
        fixture.orderRepository.orderEntity = order(MarketTypeVO.GROUP_BUY_MARKET.getCode());
        fixture.marketSettlementPort.throwOnSettlement = true;

        boolean result = fixture.service.replayReconcileCase("MARKET_SETTLEMENT_TIMEOUT:" + ORDER_ID, OPERATOR);

        Assert.assertFalse(result);
        Assert.assertEquals(1, fixture.reconcileRepository.remarkCalls);
        Assert.assertEquals(0, fixture.reconcileRepository.confirmCalls);
        Assert.assertTrue(fixture.reconcileRepository.lastRemark.contains("replay failed"));
    }

    private static OrderEntity order(Integer marketType) {
        return OrderEntity.builder()
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .marketType(marketType)
                .payTime(new Date())
                .build();
    }

    private static class Fixture {
        private final FakeOrderRepository orderRepository = new FakeOrderRepository();
        private final FakeOrderReconcileRepository reconcileRepository = new FakeOrderReconcileRepository();
        private final FakeMarketSettlementPort marketSettlementPort = new FakeMarketSettlementPort();
        private final FakeOrderService orderService = new FakeOrderService();
        private final OrderReconcileService service = new OrderReconcileService(orderRepository, reconcileRepository, marketSettlementPort, orderService);
    }

    private static class FakeOrderRepository implements IOrderRepository {
        private OrderEntity orderEntity = order(MarketTypeVO.GROUP_BUY_MARKET.getCode());
        private int closeCalls;
        private int marketSettlementCalls;

        @Override
        public void doSaveOrder(CreateOrderAggregate orderAggregate) {
        }

        @Override
        public OrderEntity queryUnPayOrder(ShopCartEntity shopCartEntity) {
            return null;
        }

        @Override
        public void updateOrderPayInfo(PayOrderEntity payOrderEntity) {
        }

        @Override
        public boolean changeOrderPaySuccess(String orderId, Date payTime) {
            return false;
        }

        @Override
        public boolean changeMarketOrderPaySuccess(String orderId) {
            return false;
        }

        @Override
        public boolean changeMarketOrderPaySuccess(String orderId, Date payTime) {
            return false;
        }

        @Override
        public List<String> queryNoPayNotifyOrder() {
            return Collections.emptyList();
        }

        @Override
        public List<String> queryTimeoutCloseOrderList() {
            return Collections.emptyList();
        }

        @Override
        public boolean changeOrderClose(String orderId) {
            closeCalls++;
            return true;
        }

        @Override
        public void changeOrderMarketSettlement(List<String> outTradeNoList) {
            marketSettlementCalls++;
        }

        @Override
        public OrderEntity queryOrderByOrderId(String orderId) {
            return orderEntity;
        }

        @Override
        public List<OrderEntity> queryUserOrderList(String userId, Long lastId, Integer pageSize) {
            return Collections.emptyList();
        }

        @Override
        public OrderEntity queryOrderByUserIdAndOrderId(String userId, String orderId) {
            return null;
        }

        @Override
        public boolean refundOrder(String userId, String orderId) {
            return false;
        }

        @Override
        public boolean refundMarketOrder(String userId, String orderId) {
            return false;
        }
    }

    private static class FakeOrderReconcileRepository implements IOrderReconcileRepository {
        private Integer caseStatus = ReconcileCaseStatusVO.OPEN.getCode();
        private int confirmCalls;
        private int remarkCalls;
        private int mqReplayCalls;
        private Integer lastHandledStatus;
        private String lastRemark;
        private String lastMessageId;

        @Override
        public List<OrderEntity> queryStaleMarketSettlementOrderList() {
            return Collections.emptyList();
        }

        @Override
        public int scanReconcileCases() {
            return 0;
        }

        @Override
        public List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize) {
            return Collections.emptyList();
        }

        @Override
        public ReconcileCaseEntity queryReconcileCase(String caseNo) {
            return ReconcileCaseEntity.builder()
                    .caseNo(caseNo)
                    .caseStatus(caseStatus)
                    .build();
        }

        @Override
        public boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote) {
            lastHandledStatus = caseStatus;
            if (ReconcileCaseStatusVO.CONFIRMED.getCode().equals(caseStatus)) {
                confirmCalls++;
            }
            return true;
        }

        @Override
        public boolean remarkReconcileCase(String caseNo, String handler, String handleNote) {
            remarkCalls++;
            lastRemark = handleNote;
            return true;
        }

        @Override
        public boolean replayMqFailure(String messageId) {
            mqReplayCalls++;
            lastMessageId = messageId;
            return true;
        }

        @Override
        public void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result) {
        }

        @Override
        public List<ReconcileOperationLogEntity> queryReconcileOperationLogList(String bizId, Long lastId, Integer pageSize) {
            return Collections.emptyList();
        }

        @Override
        public int importThirdPartyBillCsv(String csvText) {
            return 0;
        }
    }

    private static class FakeMarketSettlementPort implements IMarketSettlementPort {
        private int groupBuySettlementCalls;
        private int seckillSettlementCalls;
        private boolean throwOnSettlement;

        @Override
        public void settlementGroupBuyMarketPayOrder(String userId, String orderId, Date orderTime) {
            groupBuySettlementCalls++;
            if (throwOnSettlement) {
                throw new RuntimeException("settlement failed");
            }
        }

        @Override
        public void settlementSeckillPayOrder(String userId, String orderId, Date orderTime) {
            seckillSettlementCalls++;
            if (throwOnSettlement) {
                throw new RuntimeException("settlement failed");
            }
        }
    }

    private static class FakeOrderService implements IOrderService {
        private int refundPayOrderCalls;
        private int marketSettlementCalls;

        @Override
        public PayOrderEntity createOrder(ShopCartEntity shopCartEntity) {
            return null;
        }

        @Override
        public void changeOrderPaySuccess(String orderId, Date orderTime) {
        }

        @Override
        public void changeOrderPaySuccess(String orderId, Date orderTime, String payChannel, String channelTradeNo, String rawMessage) {
        }

        @Override
        public List<String> queryNoPayNotifyOrder() {
            return Collections.emptyList();
        }

        @Override
        public List<String> queryTimeoutCloseOrderList() {
            return Collections.emptyList();
        }

        @Override
        public boolean changeOrderClose(String orderId) {
            return false;
        }

        @Override
        public void changeOrderMarketSettlement(List<String> outTradeNoList) {
            marketSettlementCalls++;
        }

        @Override
        public List<OrderEntity> queryUserOrderList(String userId, Long lastId, Integer pageSize) {
            return Collections.emptyList();
        }

        @Override
        public boolean refundMarketOrder(String userId, String orderId) {
            return false;
        }

        @Override
        public boolean refundPayOrder(String userId, String orderId) {
            refundPayOrderCalls++;
            return true;
        }
    }
}
