package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillStockSnapshotSupport {

    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;

    public Integer queryInitializedStock(Long activityId) {
        if (seckillSoldOutCache.isSoldOut(activityId)) {
            return 0;
        }
        if (!seckillStockReservationPort.isStockInitialized(activityId)) {
            return null;
        }
        int stock = seckillStockReservationPort.queryStock(activityId);
        refreshSoldOut(activityId, stock);
        return stock;
    }

    public void refreshSoldOut(Long activityId, Integer stock) {
        if (null == stock || stock <= 0) {
            seckillSoldOutCache.markSoldOut(activityId);
            return;
        }
        seckillSoldOutCache.clear(activityId);
    }

}
