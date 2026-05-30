package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.IGroupBuyRefundPort;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyRefundAggregate;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.adapter.support.GroupBuyPaidFormedRefundProcessor;
import cn.bugstack.infrastructure.adapter.support.GroupBuyPaidUnformedRefundProcessor;
import cn.bugstack.infrastructure.adapter.support.GroupBuyUnpaidRefundProcessor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class GroupBuyRefundPort implements IGroupBuyRefundPort {

    @Resource
    private GroupBuyUnpaidRefundProcessor groupBuyUnpaidRefundProcessor;
    @Resource
    private GroupBuyPaidUnformedRefundProcessor groupBuyPaidUnformedRefundProcessor;
    @Resource
    private GroupBuyPaidFormedRefundProcessor groupBuyPaidFormedRefundProcessor;

    @Override
    public NotifyTaskEntity unpaid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        return groupBuyUnpaidRefundProcessor.refund(groupBuyRefundAggregate);
    }

    @Override
    public NotifyTaskEntity paid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        return groupBuyPaidUnformedRefundProcessor.refund(groupBuyRefundAggregate);
    }

    @Override
    public NotifyTaskEntity paidTeam2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        return groupBuyPaidFormedRefundProcessor.refund(groupBuyRefundAggregate);
    }

}
