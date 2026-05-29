package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Seckill order DAO.
 */
@Mapper
public interface ISeckillOrderDao {

    void insert(SeckillOrder seckillOrder);

    void insertToTable(@Param("tableName") String tableName, @Param("item") SeckillOrder seckillOrder);

    int insertIgnoreBatch(@Param("list") List<SeckillOrder> seckillOrders);

    int insertIgnoreShardBatch(@Param("tableName") String tableName, @Param("list") List<SeckillOrder> seckillOrders);

    SeckillOrder querySeckillOrderByOutTradeNo(SeckillOrder seckillOrder);

    SeckillOrder querySeckillOrderByOutTradeNoFromTable(@Param("tableName") String tableName, @Param("item") SeckillOrder seckillOrder);

    int countActiveOrders(@Param("activityId") Long activityId);

    int countActiveOrdersFromTable(@Param("tableName") String tableName, @Param("activityId") Long activityId);

    List<SeckillOrder> queryTimeoutUnpaidOrders();

    List<SeckillOrder> queryTimeoutUnpaidOrdersFromTable(@Param("tableName") String tableName);

    int closeTimeoutUnpaidOrder(String orderId);

    int closeTimeoutUnpaidOrderFromTable(@Param("tableName") String tableName, @Param("orderId") String orderId);

}
