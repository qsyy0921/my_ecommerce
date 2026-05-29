package cn.bugstack.domain.shared.adapter.port;

import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;

public interface IOrderStateFlowPort {

    void record(OrderStateTransitionEntity orderStateTransitionEntity);

}
