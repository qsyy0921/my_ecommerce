package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMaintenancePort;
import cn.bugstack.infrastructure.adapter.support.SeckillActivityPrewarmSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillActivityStockSyncSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillTimeoutUnpaidReleaseSupport;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillMaintenancePort implements ISeckillMaintenancePort {

    @Resource
    private SeckillActivityStockSyncSupport seckillActivityStockSyncSupport;
    @Resource
    private SeckillTimeoutUnpaidReleaseSupport seckillTimeoutUnpaidReleaseSupport;
    @Resource
    private SeckillActivityPrewarmSupport seckillActivityPrewarmSupport;

    @Override
    public void syncSeckillActivityStock() {
        seckillActivityStockSyncSupport.sync();
    }

    @Override
    public int releaseTimeoutUnpaidOrders() {
        return seckillTimeoutUnpaidReleaseSupport.release();
    }

    @Override
    public int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit) {
        return seckillActivityPrewarmSupport.prewarm(beforeMinutes, limit);
    }

}
