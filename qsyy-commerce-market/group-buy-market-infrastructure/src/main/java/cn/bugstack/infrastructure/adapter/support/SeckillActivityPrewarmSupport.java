package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillQueryPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class SeckillActivityPrewarmSupport {

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillQueryPort seckillQueryPort;
    @Resource
    private ISeckillStockAvailabilityPort seckillStockAvailabilityPort;

    public int prewarm(Integer beforeMinutes, Integer limit) {
        int safeBeforeMinutes = Math.max(0, null == beforeMinutes ? 10 : beforeMinutes);
        int safeLimit = Math.max(1, Math.min(null == limit ? 50 : limit, 500));
        List<SeckillActivity> activities = seckillActivityDao.queryPrewarmActivities(safeBeforeMinutes, safeLimit);
        if (null == activities || activities.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SeckillActivity activity : activities) {
            try {
                if (prewarmActivity(activity)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("prewarm seckill activity failed activityId:{}", activity.getActivityId(), e);
            }
        }
        return count;
    }

    private boolean prewarmActivity(SeckillActivity activity) {
        SeckillActivityEntity activityEntity = seckillQueryPort.querySeckillActivity(
                activity.getActivityId(),
                activity.getSource(),
                activity.getChannel(),
                activity.getGoodsId());
        if (null == activityEntity) {
            return false;
        }
        seckillStockAvailabilityPort.queryAvailableStock(activity.getActivityId());
        return true;
    }

}
