package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.infrastructure.adapter.support.SeckillStockInitializationSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillStockSnapshotSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillStockAvailabilityPort implements ISeckillStockAvailabilityPort {

    @Value("${app.seckill.stock-init-lock-wait-millis:200}")
    private Long stockInitLockWaitMillis;

    @Resource
    private SeckillStockSnapshotSupport seckillStockSnapshotSupport;
    @Resource
    private SeckillStockInitializationSupport seckillStockInitializationSupport;

    @Override
    public Integer queryAvailableStock(Long activityId) {
        Integer stock = seckillStockSnapshotSupport.queryInitializedStock(activityId);
        if (null != stock) {
            return stock;
        }
        return seckillStockInitializationSupport.initializeAndQuery(activityId, stockInitLockWaitMillis);
    }

}
