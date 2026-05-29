package cn.bugstack.domain.seckill.adapter.port;

public interface ISeckillMaintenancePort {

    void syncSeckillActivityStock();

    int releaseTimeoutUnpaidOrders();

    int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit);

}
