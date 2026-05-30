package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class SeckillActivityStockSyncSupport {

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;

    public void sync() {
        List<Long> activityIds = seckillActivityDao.queryStockSyncActivityIds();
        if (null == activityIds || activityIds.isEmpty()) {
            return;
        }
        for (Long activityId : activityIds) {
            try {
                syncActivity(activityId);
            } catch (Exception e) {
                log.error("sync seckill activity stock failed activityId:{}", activityId, e);
            }
        }
    }

    private void syncActivity(Long activityId) {
        if (!seckillOrderShardRouter.useSharding()) {
            seckillActivityDao.syncStockByOrderCount(activityId);
            return;
        }
        int activeCount = 0;
        for (int shardIndex = 0; shardIndex < seckillOrderShardRouter.shardCount(); shardIndex++) {
            activeCount += seckillOrderDao.countActiveOrdersFromTable(seckillOrderShardRouter.tableName(shardIndex), activityId);
        }
        seckillActivityDao.syncStockByActiveCount(activityId, activeCount);
    }

}
