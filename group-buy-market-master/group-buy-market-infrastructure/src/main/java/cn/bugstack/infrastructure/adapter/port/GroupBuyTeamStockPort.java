package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.infrastructure.adapter.support.GroupBuyTeamStockRecoverySupport;
import cn.bugstack.infrastructure.adapter.support.GroupBuyTeamStockReservationSupport;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class GroupBuyTeamStockPort implements IGroupBuyTeamStockPort {

    @Resource
    private GroupBuyTeamStockReservationSupport groupBuyTeamStockReservationSupport;
    @Resource
    private GroupBuyTeamStockRecoverySupport groupBuyTeamStockRecoverySupport;

    @Override
    public long occupyTeamStock(String teamStockKey, String recoveryTeamStockKey, String userTeamOccupyKey, String outTradeNo, Integer target, Integer validTime) {
        return groupBuyTeamStockReservationSupport.occupy(teamStockKey, recoveryTeamStockKey, userTeamOccupyKey, outTradeNo, target, validTime);
    }

    @Override
    public void recoveryTeamStock(String recoveryTeamStockKey, Integer validTime) {
        groupBuyTeamStockRecoverySupport.recovery(recoveryTeamStockKey);
    }

    @Override
    public void releaseUserTeamOccupy(String userTeamOccupyKey) {
        groupBuyTeamStockReservationSupport.releaseUserOccupy(userTeamOccupyKey);
    }

    @Override
    public void refund2AddRecovery(String recoveryTeamStockKey, String orderId) {
        groupBuyTeamStockRecoverySupport.refundRecovery(recoveryTeamStockKey, orderId);
    }

}
