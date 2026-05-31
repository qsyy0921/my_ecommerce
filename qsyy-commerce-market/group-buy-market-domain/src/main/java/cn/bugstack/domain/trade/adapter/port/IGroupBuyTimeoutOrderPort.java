package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;

import java.util.List;

public interface IGroupBuyTimeoutOrderPort {

    List<UserGroupBuyOrderDetailEntity> queryTimeoutUnpaidOrderList();

}
