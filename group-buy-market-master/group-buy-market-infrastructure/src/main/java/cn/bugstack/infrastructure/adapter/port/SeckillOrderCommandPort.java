package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderCommandPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderShardRouter;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.event.SeckillStreamMetrics;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SeckillOrderCommandPort implements ISeckillOrderCommandPort {

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISeckillStockFlowPort seckillStockFlowPort;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;
    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;
    @Resource
    private SeckillFaultInjector seckillFaultInjector;

    @Transactional(timeout = 5)
    @Override
    public void createSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
            return;
        }

        try {
            insertSeckillOrder(buildSeckillOrder(seckillOrderEntity));
            seckillStockFlowPort.record(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        } catch (DuplicateKeyException e) {
            SeckillOrderEntity duplicateOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null != duplicateOrder) {
                seckillResultCachePort.cache(duplicateOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
                return;
            }
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "duplicate seckill order");
            rollbackReservation(seckillOrderEntity, false, "duplicate seckill order");
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_FAIL, e.getMessage());
            rollbackReservation(seckillOrderEntity, false, e.getMessage());
            throw e;
        }
    }

    @Transactional(timeout = 10)
    @Override
    public void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities) {
        if (null == seckillOrderEntities || seckillOrderEntities.isEmpty()) {
            return;
        }

        List<SeckillOrder> seckillOrders = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            seckillOrders.add(buildSeckillOrder(seckillOrderEntity));
        }

        seckillFaultInjector.beforeBatchInsert();
        long startNanos = System.nanoTime();
        try {
            insertSeckillOrders(seckillOrders);
        } finally {
            seckillStreamMetrics.recordBatchInsert(System.nanoTime() - startNanos, seckillOrderEntities.size());
        }

        List<SeckillStockFlowEntity> stockFlows = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null == existsOrder) {
                seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "order create ignored by unique constraint");
                rollbackReservation(seckillOrderEntity, false, "batch order create ignored by unique constraint");
                continue;
            }

            stockFlows.add(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        }
        if (!stockFlows.isEmpty()) {
            seckillStockFlowPort.recordBatch(stockFlows);
        }
    }

    @Transactional(timeout = 5)
    @Override
    public SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo) {
        SeckillOrder seckillOrder = querySeckillOrderPo(userId, outTradeNo);
        if (null == seckillOrder) {
            throw new AppException(ResponseCode.E0206);
        }

        SeckillOrderStatusEnumVO status = SeckillOrderStatusEnumVO.valueOf(seckillOrder.getStatus());
        if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            SeckillOrderEntity entity = buildSeckillOrderEntity(seckillOrder);
            seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order already paid");
            return entity;
        }
        if (!status.canPay()) {
            throw new AppException(ResponseCode.E0207);
        }

        int updated = seckillOrderShardRouter.useSharding()
                ? seckillOrderDao.paySuccessOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrder.getOrderId())
                : seckillOrderDao.paySuccessOrder(seckillOrder.getOrderId());
        if (updated <= 0) {
            SeckillOrder latest = querySeckillOrderPo(userId, outTradeNo);
            if (null != latest && SeckillOrderStatusEnumVO.COMPLETE.equals(SeckillOrderStatusEnumVO.valueOf(latest.getStatus()))) {
                SeckillOrderEntity entity = buildSeckillOrderEntity(latest);
                seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order already paid");
                return entity;
            }
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderPaid(
                seckillOrder.getOutTradeNo(),
                seckillOrder.getOrderId(),
                seckillOrder.getUserId(),
                MDC.get("trace-id")));
        SeckillOrder updatedOrder = querySeckillOrderPo(userId, outTradeNo);
        SeckillOrderEntity entity = buildSeckillOrderEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order paid");
        return entity;
    }

    @Transactional(timeout = 5)
    @Override
    public SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason) {
        SeckillOrder seckillOrder = querySeckillOrderPo(userId, outTradeNo);
        if (null == seckillOrder) {
            throw new AppException(ResponseCode.E0206);
        }

        SeckillOrderStatusEnumVO status = SeckillOrderStatusEnumVO.valueOf(seckillOrder.getStatus());
        if (SeckillOrderStatusEnumVO.REFUND.equals(status) || SeckillOrderStatusEnumVO.CLOSE.equals(status)) {
            SeckillOrderEntity entity = buildSeckillOrderEntity(seckillOrder);
            seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund already handled");
            return entity;
        }

        if (SeckillOrderStatusEnumVO.CREATE.equals(status)) {
            int updated = seckillOrderShardRouter.useSharding()
                    ? seckillOrderDao.closeUnpaidOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrder.getOrderId())
                    : seckillOrderDao.closeUnpaidOrder(seckillOrder.getOrderId());
            if (updated <= 0) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            String message = null == refundReason ? "unpaid seckill order canceled" : refundReason;
            releaseSeckillStock(seckillOrder, SeckillStockFlowEntity.ROLLBACK_CANCEL, 1, message);
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillUnpaidCanceled(
                    seckillOrder.getOutTradeNo(),
                    seckillOrder.getOrderId(),
                    seckillOrder.getUserId(),
                    MDC.get("trace-id"),
                    message));
        } else if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            int updated = seckillOrderShardRouter.useSharding()
                    ? seckillOrderDao.refundPaidOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrder.getOrderId())
                    : seckillOrderDao.refundPaidOrder(seckillOrder.getOrderId());
            if (updated <= 0) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            String message = null == refundReason ? "paid seckill order refunded" : refundReason;
            releaseSeckillStock(seckillOrder, SeckillStockFlowEntity.ROLLBACK_REFUND, 1, message);
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderRefunded(
                    seckillOrder.getOutTradeNo(),
                    seckillOrder.getOrderId(),
                    seckillOrder.getUserId(),
                    MDC.get("trace-id"),
                    message));
        } else {
            throw new AppException(ResponseCode.E0207);
        }

        SeckillOrder updatedOrder = querySeckillOrderPo(userId, outTradeNo);
        SeckillOrderEntity entity = buildSeckillOrderEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund handled");
        return entity;
    }

    private SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo) {
        return buildSeckillOrderEntity(querySeckillOrderPo(userId, outTradeNo));
    }

    private SeckillOrder querySeckillOrderPo(String userId, String outTradeNo) {
        SeckillOrder seckillOrderReq = SeckillOrder.builder()
                .userId(userId)
                .outTradeNo(outTradeNo)
                .build();
        if (seckillOrderShardRouter.useSharding()) {
            return seckillOrderDao.querySeckillOrderByOutTradeNoFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrderReq);
        }
        return seckillOrderDao.querySeckillOrderByOutTradeNo(seckillOrderReq);
    }

    private void releaseSeckillStock(SeckillOrder order, String changeType, int changeCount, String message) {
        seckillActivityDao.updateReleaseStock(order.getActivityId());
        SeckillOrderEntity entity = buildSeckillOrderEntity(order);
        entity.setTraceId(MDC.get("trace-id"));
        seckillStockReservationPort.release(entity, changeCount);
        seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(entity, changeType, changeCount, message));
        seckillSoldOutCache.clear(order.getActivityId());
    }

    private void rollbackReservation(SeckillOrderEntity seckillOrderEntity, boolean removeResult, String reason) {
        seckillStockReservationPort.rollback(seckillOrderEntity, removeResult);
        seckillSoldOutCache.clear(seckillOrderEntity.getActivityId());
        try {
            seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(seckillOrderEntity, SeckillStockFlowEntity.ROLLBACK, 1, reason));
        } catch (Exception e) {
            log.error("record seckill stock rollback flow failed activityId:{} userId:{} outTradeNo:{}",
                    seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo(), e);
        }
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

    private SeckillOrder buildSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        return SeckillOrder.builder()
                .userId(seckillOrderEntity.getUserId())
                .activityId(seckillOrderEntity.getActivityId())
                .activityName(seckillOrderEntity.getActivityName())
                .goodsId(seckillOrderEntity.getGoodsId())
                .goodsName(seckillOrderEntity.getGoodsName())
                .source(seckillOrderEntity.getSource())
                .channel(seckillOrderEntity.getChannel())
                .orderId(seckillOrderEntity.getOrderId())
                .outTradeNo(seckillOrderEntity.getOutTradeNo())
                .originalPrice(seckillOrderEntity.getOriginalPrice())
                .seckillPrice(seckillOrderEntity.getSeckillPrice())
                .status(seckillOrderEntity.getStatus())
                .build();
    }

    private void insertSeckillOrder(SeckillOrder seckillOrder) {
        if (seckillOrderShardRouter.useSharding()) {
            seckillOrderDao.insertToTable(seckillOrderShardRouter.tableName(seckillOrder.getUserId(), seckillOrder.getOutTradeNo()), seckillOrder);
            return;
        }
        seckillOrderDao.insert(seckillOrder);
    }

    private void insertSeckillOrders(List<SeckillOrder> seckillOrders) {
        if (!seckillOrderShardRouter.useSharding()) {
            seckillOrderDao.insertIgnoreBatch(seckillOrders);
            return;
        }

        Map<String, List<SeckillOrder>> orderMap = seckillOrderShardRouter.groupByTable(seckillOrders);
        for (Map.Entry<String, List<SeckillOrder>> entry : orderMap.entrySet()) {
            seckillOrderDao.insertIgnoreShardBatch(entry.getKey(), entry.getValue());
        }
    }

}
