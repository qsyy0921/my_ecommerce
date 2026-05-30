package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class SeckillTimeoutUnpaidReleaseSupport {

    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;
    @Resource
    private SeckillStockReleaseSupport seckillStockReleaseSupport;

    public int release() {
        if (!seckillOrderShardRouter.useSharding()) {
            return releaseTimeoutUnpaidOrders(seckillOrderDao.queryTimeoutUnpaidOrders(), null);
        }

        int count = 0;
        for (int shardIndex = 0; shardIndex < seckillOrderShardRouter.shardCount(); shardIndex++) {
            String tableName = seckillOrderShardRouter.tableName(shardIndex);
            count += releaseTimeoutUnpaidOrders(seckillOrderDao.queryTimeoutUnpaidOrdersFromTable(tableName), tableName);
        }
        return count;
    }

    private int releaseTimeoutUnpaidOrders(List<SeckillOrder> timeoutOrders, String tableName) {
        if (null == timeoutOrders || timeoutOrders.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SeckillOrder order : timeoutOrders) {
            int updated = null == tableName
                    ? seckillOrderDao.closeTimeoutUnpaidOrder(order.getOrderId())
                    : seckillOrderDao.closeTimeoutUnpaidOrderFromTable(tableName, order.getOrderId());
            if (updated <= 0) {
                continue;
            }
            seckillStockReleaseSupport.releaseByOrder(order, SeckillStockFlowEntity.ROLLBACK_TIMEOUT, 1, "timeout unpaid released");
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillTimeoutClosed(
                    order.getOutTradeNo(),
                    order.getOrderId(),
                    order.getUserId(),
                    MDC.get("trace-id")));
            count++;
        }
        return count;
    }

}
