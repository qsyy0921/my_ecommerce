package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMaintenancePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillQueryPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderShardRouter;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class SeckillMaintenancePort implements ISeckillMaintenancePort {

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISeckillStockFlowPort seckillStockFlowPort;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private ISeckillQueryPort seckillQueryPort;
    @Resource
    private ISeckillStockAvailabilityPort seckillStockAvailabilityPort;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;

    @Override
    public void syncSeckillActivityStock() {
        List<Long> activityIds = seckillActivityDao.queryStockSyncActivityIds();
        if (null == activityIds || activityIds.isEmpty()) {
            return;
        }
        for (Long activityId : activityIds) {
            try {
                if (!seckillOrderShardRouter.useSharding()) {
                    seckillActivityDao.syncStockByOrderCount(activityId);
                    continue;
                }
                int activeCount = 0;
                for (int shardIndex = 0; shardIndex < seckillOrderShardRouter.shardCount(); shardIndex++) {
                    activeCount += seckillOrderDao.countActiveOrdersFromTable(seckillOrderShardRouter.tableName(shardIndex), activityId);
                }
                seckillActivityDao.syncStockByActiveCount(activityId, activeCount);
            } catch (Exception e) {
                log.error("sync seckill activity stock failed activityId:{}", activityId, e);
            }
        }
    }

    @Override
    public int releaseTimeoutUnpaidOrders() {
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

    @Override
    public int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit) {
        int safeBeforeMinutes = Math.max(0, null == beforeMinutes ? 10 : beforeMinutes);
        int safeLimit = Math.max(1, Math.min(null == limit ? 50 : limit, 500));
        List<SeckillActivity> activities = seckillActivityDao.queryPrewarmActivities(safeBeforeMinutes, safeLimit);
        if (null == activities || activities.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SeckillActivity activity : activities) {
            try {
                SeckillActivityEntity activityEntity = seckillQueryPort.querySeckillActivity(
                        activity.getActivityId(),
                        activity.getSource(),
                        activity.getChannel(),
                        activity.getGoodsId());
                if (null == activityEntity) {
                    continue;
                }
                seckillStockAvailabilityPort.queryAvailableStock(activity.getActivityId());
                count++;
            } catch (Exception e) {
                log.error("prewarm seckill activity failed activityId:{}", activity.getActivityId(), e);
            }
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
            seckillActivityDao.updateReleaseStock(order.getActivityId());
            SeckillOrderEntity entity = buildSeckillOrderEntity(order);
            seckillStockReservationPort.release(entity, 1);
            seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(entity, SeckillStockFlowEntity.ROLLBACK_TIMEOUT, 1, "timeout unpaid released"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillTimeoutClosed(
                    order.getOutTradeNo(),
                    order.getOrderId(),
                    order.getUserId(),
                    MDC.get("trace-id")));
            seckillSoldOutCache.clear(order.getActivityId());
            count++;
        }
        return count;
    }

    private SeckillOrderEntity buildSeckillOrderEntity(SeckillOrder seckillOrder) {
        if (null == seckillOrder) return null;
        return SeckillOrderEntity.builder()
                .userId(seckillOrder.getUserId())
                .activityId(seckillOrder.getActivityId())
                .activityName(seckillOrder.getActivityName())
                .goodsId(seckillOrder.getGoodsId())
                .goodsName(seckillOrder.getGoodsName())
                .source(seckillOrder.getSource())
                .channel(seckillOrder.getChannel())
                .orderId(seckillOrder.getOrderId())
                .outTradeNo(seckillOrder.getOutTradeNo())
                .originalPrice(seckillOrder.getOriginalPrice())
                .seckillPrice(seckillOrder.getSeckillPrice())
                .status(seckillOrder.getStatus())
                .resultStatus(SeckillOrderEntity.RESULT_SUCCESS)
                .message("order created")
                .createTime(seckillOrder.getCreateTime())
                .build();
    }

}
