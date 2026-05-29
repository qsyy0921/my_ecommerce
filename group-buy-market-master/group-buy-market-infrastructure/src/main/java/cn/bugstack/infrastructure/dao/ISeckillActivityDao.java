package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Seckill activity DAO.
 */
@Mapper
public interface ISeckillActivityDao {

    SeckillActivity querySeckillActivity(SeckillActivity seckillActivity);

    int updateOccupyStock(Long activityId);

    int updateReleaseStock(Long activityId);

    List<Long> queryStockSyncActivityIds();

    int syncStockByOrderCount(Long activityId);

    int syncStockByActiveCount(@Param("activityId") Long activityId, @Param("activeCount") Integer activeCount);

    List<SeckillActivity> queryPrewarmActivities(@Param("beforeMinutes") Integer beforeMinutes, @Param("limit") Integer limit);

}
