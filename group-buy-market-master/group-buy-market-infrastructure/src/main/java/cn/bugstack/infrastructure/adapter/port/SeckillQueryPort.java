package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillQueryPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderShardRouter;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.dao.po.Sku;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeckillQueryPort implements ISeckillQueryPort {

    @Value("${app.seckill.activity-cache-ttl-millis:3000}")
    private Long activityCacheTtlMillis;

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private ISkuDao skuDao;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;

    private final ConcurrentHashMap<String, ActivityCacheEntry> activityCache = new ConcurrentHashMap<>();

    @Override
    public SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId) {
        String cacheKey = activityCacheKey(activityId, source, channel, goodsId);
        ActivityCacheEntry cacheEntry = activityCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (null != cacheEntry && cacheEntry.getExpireTime() > now) {
            return cacheEntry.getSeckillActivityEntity();
        }

        SeckillActivity seckillActivityReq = SeckillActivity.builder()
                .activityId(activityId)
                .source(source)
                .channel(channel)
                .goodsId(goodsId)
                .build();
        SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivity(seckillActivityReq);
        if (null == seckillActivity) return null;

        Sku sku = skuDao.querySkuByGoodsId(seckillActivity.getGoodsId());
        if (null == sku) return null;

        SeckillActivityEntity seckillActivityEntity = SeckillActivityEntity.builder()
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
        if (activityCacheTtlMillis > 0) {
            activityCache.put(cacheKey, new ActivityCacheEntry(seckillActivityEntity, now + activityCacheTtlMillis));
        }
        return seckillActivityEntity;
    }

    @Override
    public SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo) {
        SeckillOrder seckillOrderReq = SeckillOrder.builder()
                .userId(userId)
                .outTradeNo(outTradeNo)
                .build();
        SeckillOrder seckillOrder;
        if (seckillOrderShardRouter.useSharding()) {
            seckillOrder = seckillOrderDao.querySeckillOrderByOutTradeNoFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrderReq);
        } else {
            seckillOrder = seckillOrderDao.querySeckillOrderByOutTradeNo(seckillOrderReq);
        }
        return buildSeckillOrderEntity(seckillOrder);
    }

    @Override
    public SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo) {
        SeckillOrderEntity result = seckillResultCachePort.query(activityId, userId, outTradeNo);
        if (null != result) {
            return result;
        }

        SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(userId, outTradeNo);
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
            return existsOrder;
        }

        return SeckillOrderEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .outTradeNo(outTradeNo)
                .resultStatus(SeckillOrderEntity.RESULT_NOT_FOUND)
                .message("seckill order not found")
                .build();
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
