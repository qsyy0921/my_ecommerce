package cn.bugstack.domain.seckill.adapter.port;

public interface ISeckillStockAvailabilityPort {

    Integer queryAvailableStock(Long activityId);

}
