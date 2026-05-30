package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.infrastructure.dao.po.Sku;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SeckillActivityQuerySupport {

    @Value("${app.seckill.activity-cache-ttl-millis:3000}")
    private Long activityCacheTtlMillis;

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISkuDao skuDao;

    private final ConcurrentHashMap<String, ActivityCacheEntry> activityCache = new ConcurrentHashMap<>();

    public SeckillActivityEntity query(Long activityId, String source, String channel, String goodsId) {
        String cacheKey = activityCacheKey(activityId, source, channel, goodsId);
        ActivityCacheEntry cacheEntry = activityCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (null != cacheEntry && cacheEntry.getExpireTime() > now) {
            return cacheEntry.getSeckillActivityEntity();
        }

        SeckillActivity seckillActivity = queryActivity(activityId, source, channel, goodsId);
        if (null == seckillActivity) return null;

        Sku sku = skuDao.querySkuByGoodsId(seckillActivity.getGoodsId());
        if (null == sku) return null;

        SeckillActivityEntity seckillActivityEntity = buildActivityEntity(seckillActivity, sku);
        if (activityCacheTtlMillis > 0) {
            activityCache.put(cacheKey, new ActivityCacheEntry(seckillActivityEntity, now + activityCacheTtlMillis));
        }
        return seckillActivityEntity;
    }

    private SeckillActivity queryActivity(Long activityId, String source, String channel, String goodsId) {
        SeckillActivity seckillActivityReq = SeckillActivity.builder()
                .activityId(activityId)
                .source(source)
                .channel(channel)
                .goodsId(goodsId)
                .build();
        return seckillActivityDao.querySeckillActivity(seckillActivityReq);
    }

    private SeckillActivityEntity buildActivityEntity(SeckillActivity seckillActivity, Sku sku) {
        return SeckillActivityEntity.builder()
                .activityId(seckillActivity.getActivityId())
                .activityName(seckillActivity.getActivityName())
                .source(seckillActivity.getSource())
                .channel(seckillActivity.getChannel())
                .goodsId(seckillActivity.getGoodsId())
                .goodsName(sku.getGoodsName())
                .originalPrice(sku.getOriginalPrice())
                .seckillPrice(seckillActivity.getSeckillPrice())
                .totalCount(seckillActivity.getTotalCount())
                .availableCount(seckillActivity.getAvailableCount())
                .lockCount(seckillActivity.getLockCount())
                .takeLimitCount(seckillActivity.getTakeLimitCount())
                .status(seckillActivity.getStatus())
                .startTime(seckillActivity.getStartTime())
                .endTime(seckillActivity.getEndTime())
                .build();
    }

    private String activityCacheKey(Long activityId, String source, String channel, String goodsId) {
        return String.valueOf(activityId) + ":" + source + ":" + channel + ":" + goodsId;
    }

    private static class ActivityCacheEntry {

        private final SeckillActivityEntity seckillActivityEntity;
        private final long expireTime;

        private ActivityCacheEntry(SeckillActivityEntity seckillActivityEntity, long expireTime) {
            this.seckillActivityEntity = seckillActivityEntity;
            this.expireTime = expireTime;
        }

        public SeckillActivityEntity getSeckillActivityEntity() {
            return seckillActivityEntity;
        }

        public long getExpireTime() {
            return expireTime;
        }
    }

}
