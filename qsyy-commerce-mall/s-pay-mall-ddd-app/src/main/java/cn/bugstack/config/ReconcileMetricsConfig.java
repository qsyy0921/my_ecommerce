package cn.bugstack.config;

import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.IOrderDao;
import cn.bugstack.infrastructure.dao.IPaymentFlowDao;
import cn.bugstack.infrastructure.dao.IReconcileCaseDao;
import cn.bugstack.infrastructure.dao.IRefundFlowDao;
import cn.bugstack.infrastructure.dao.IThirdPartyBillDao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Configuration
public class ReconcileMetricsConfig {

    @Resource
    private MeterRegistry meterRegistry;
    @Resource
    private IOrderDao orderDao;
    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;
    @Resource
    private IReconcileCaseDao reconcileCaseDao;
    @Resource
    private IPaymentFlowDao paymentFlowDao;
    @Resource
    private IRefundFlowDao refundFlowDao;
    @Resource
    private IThirdPartyBillDao thirdPartyBillDao;

    @PostConstruct
    public void init() {
        gauge("mall_reconcile_open_cases", "ALL", () -> reconcileCaseDao.countOpenCase(null));
        gauge("mall_reconcile_open_cases", "MARKET_SETTLEMENT_TIMEOUT", () -> reconcileCaseDao.countOpenCase("MARKET_SETTLEMENT_TIMEOUT"));
        gauge("mall_reconcile_open_cases", "REFUND_TIMEOUT", () -> reconcileCaseDao.countOpenCase("REFUND_TIMEOUT"));
        gauge("mall_reconcile_open_cases", "PAY_WAIT_TIMEOUT", () -> reconcileCaseDao.countOpenCase("PAY_WAIT_TIMEOUT"));
        gauge("mall_reconcile_open_cases", "MQ_CONSUME_FAIL", () -> reconcileCaseDao.countOpenCase("MQ_CONSUME_FAIL"));
        gauge("mall_reconcile_sla_timeout_cases", "ALL", reconcileCaseDao::countSlaTimeoutOpenCase);
        gauge("mall_order_intermediate_anomaly", "PAY_WAIT_TIMEOUT", orderDao::countStalePayWaitOrder);
        gauge("mall_order_intermediate_anomaly", "MARKET_SETTLEMENT_TIMEOUT", orderDao::countStaleMarketSettlementOrder);
        gauge("mall_order_intermediate_anomaly", "REFUND_TIMEOUT", orderDao::countStaleWaitRefundOrder);
        gauge("mall_mq_consume_failed_messages", "ALL", mqMessageRecordDao::countFailedMessages);
        gauge("mall_bill_reconcile_anomaly", "PAY_FLOW_MISS_BILL", paymentFlowDao::countMissThirdPartyBill);
        gauge("mall_bill_reconcile_anomaly", "REFUND_FLOW_MISS_BILL", refundFlowDao::countMissThirdPartyBill);
        gauge("mall_bill_reconcile_anomaly", "BILL_MISS_LOCAL_FLOW", thirdPartyBillDao::countUnmatchedBill);
    }

    private void gauge(String name, String type, CountSupplier supplier) {
        Gauge.builder(name, supplier, CountSupplier::count)
                .tag("type", type)
                .register(meterRegistry);
    }

    private interface CountSupplier {
        int count();
    }

}
