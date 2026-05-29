package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.IOrderStateFlowDao;
import cn.bugstack.infrastructure.dao.po.OrderStateFlow;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class OrderStateFlowPort implements IOrderStateFlowPort {

    @Resource
    private IOrderStateFlowDao orderStateFlowDao;

    @Override
    public void record(OrderStateTransitionEntity orderStateTransitionEntity) {
        if (null == orderStateTransitionEntity) {
            return;
        }
        orderStateFlowDao.insertIgnore(OrderStateFlow.builder()
                .flowNo(orderStateTransitionEntity.flowNo())
                .bizType(orderStateTransitionEntity.getBizType())
                .bizId(orderStateTransitionEntity.getBizId())
                .subBizId(orderStateTransitionEntity.getSubBizId())
                .fromStatus(orderStateTransitionEntity.getFromStatus())
                .toStatus(orderStateTransitionEntity.getToStatus())
                .event(orderStateTransitionEntity.getEvent())
                .operatorId(orderStateTransitionEntity.getOperatorId())
                .traceId(orderStateTransitionEntity.getTraceId())
                .sourceMessageId(orderStateTransitionEntity.getSourceMessageId())
                .message(orderStateTransitionEntity.getMessage())
                .build());
    }

}
