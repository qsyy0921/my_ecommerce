package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;

import java.util.List;

public interface ITradeNotifyTaskPort {

    NotifyTaskEntity createSettlementTask(Long activityId, String teamId, NotifyConfigVO notifyConfigVO, List<String> outTradeNoList);

    NotifyTaskEntity createRefundTask(TradeRefundOrderEntity tradeRefundOrderEntity, RefundTypeEnumVO refundTypeEnumVO, String notifyMQ);

    List<NotifyTaskEntity> queryUnExecutedNotifyTaskList();

    List<NotifyTaskEntity> queryUnExecutedNotifyTaskList(String teamId);

    int updateNotifyTaskStatusSuccess(NotifyTaskEntity notifyTaskEntity);

    int updateNotifyTaskStatusError(NotifyTaskEntity notifyTaskEntity);

    int updateNotifyTaskStatusRetry(NotifyTaskEntity notifyTaskEntity);

}
