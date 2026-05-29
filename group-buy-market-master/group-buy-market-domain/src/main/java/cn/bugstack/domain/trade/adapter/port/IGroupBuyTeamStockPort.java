package cn.bugstack.domain.trade.adapter.port;

public interface IGroupBuyTeamStockPort {

    long occupyTeamStock(String teamStockKey, String recoveryTeamStockKey, String userTeamOccupyKey, String outTradeNo, Integer target, Integer validTime);

    void recoveryTeamStock(String recoveryTeamStockKey, Integer validTime);

    void releaseUserTeamOccupy(String userTeamOccupyKey);

    void refund2AddRecovery(String recoveryTeamStockKey, String orderId);

}
