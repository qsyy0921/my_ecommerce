package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillStockInitializationSupport {

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillStockSnapshotSupport seckillStockSnapshotSupport;

    public Integer initializeAndQuery(Long activityId, Long stockInitLockWaitMillis) {
        boolean locked = false;
        try {
            locked = seckillStockReservationPort.tryAcquireInitializationLock(activityId, stockInitLockWaitMillis, 10_000);
            if (!locked) {
                Integer stock = seckillStockSnapshotSupport.queryInitializedStock(activityId);
                if (null != stock) {
                    return stock;
                }
                throw new AppException(ResponseCode.E0205);
            }

            Integer stock = seckillStockSnapshotSupport.queryInitializedStock(activityId);
            if (null != stock) {
                return stock;
            }

            SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivity(SeckillActivity.builder().activityId(activityId).build());
            if (null == seckillActivity) {
                throw new AppException(ResponseCode.E0201);
            }
            seckillStockReservationPort.initializeStock(activityId, seckillActivity.getAvailableCount());
            seckillStockSnapshotSupport.refreshSoldOut(activityId, seckillActivity.getAvailableCount());
            return seckillActivity.getAvailableCount();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ResponseCode.E0205);
        } finally {
            if (locked) {
                seckillStockReservationPort.releaseInitializationLock(activityId);
            }
        }
    }

}
