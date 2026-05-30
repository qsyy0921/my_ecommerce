package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillStockAvailabilityPort implements ISeckillStockAvailabilityPort {

    @Value("${app.seckill.stock-init-lock-wait-millis:200}")
    private Long stockInitLockWaitMillis;

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;

    @Override
    public Integer queryAvailableStock(Long activityId) {
        if (seckillSoldOutCache.isSoldOut(activityId)) {
            return 0;
        }
        if (seckillStockReservationPort.isStockInitialized(activityId)) {
            int stock = seckillStockReservationPort.queryStock(activityId);
            if (stock <= 0) {
                seckillSoldOutCache.markSoldOut(activityId);
            } else {
                seckillSoldOutCache.clear(activityId);
            }
            return stock;
        }

        boolean locked = false;
        try {
            locked = seckillStockReservationPort.tryAcquireInitializationLock(activityId, stockInitLockWaitMillis, 10_000);
            if (!locked) {
                if (seckillStockReservationPort.isStockInitialized(activityId)) {
                    return seckillStockReservationPort.queryStock(activityId);
                }
                throw new AppException(ResponseCode.E0205);
            }
            if (seckillStockReservationPort.isStockInitialized(activityId)) {
                return seckillStockReservationPort.queryStock(activityId);
            }

            SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivity(SeckillActivity.builder().activityId(activityId).build());
            if (null == seckillActivity) {
                throw new AppException(ResponseCode.E0201);
            }
            seckillStockReservationPort.initializeStock(activityId, seckillActivity.getAvailableCount());
            if (seckillActivity.getAvailableCount() <= 0) {
                seckillSoldOutCache.markSoldOut(activityId);
            } else {
                seckillSoldOutCache.clear(activityId);
            }
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
