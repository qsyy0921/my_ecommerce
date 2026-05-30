package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.domain.trade.model.valobj.TaskNotifyCategoryEnumVO;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TradeNotifyTaskFactory {

    public NotifyTask settlementTask(Long activityId, String teamId, NotifyConfigVO notifyConfigVO, List<String> outTradeNoList) {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("teamId", teamId);
        parameter.put("outTradeNoList", outTradeNoList);

        return NotifyTask.builder()
                .activityId(activityId)
                .teamId(teamId)
                .notifyCategory(TaskNotifyCategoryEnumVO.TRADE_SETTLEMENT.getCode())
                .notifyType(notifyConfigVO.getNotifyType().getCode())
                .notifyMQ(NotifyTypeEnumVO.MQ.equals(notifyConfigVO.getNotifyType()) ? notifyConfigVO.getNotifyMQ() : null)
                .notifyUrl(NotifyTypeEnumVO.HTTP.equals(notifyConfigVO.getNotifyType()) ? notifyConfigVO.getNotifyUrl() : null)
                .notifyCount(0)
                .notifyStatus(0)
                .parameterJson(JSON.toJSONString(parameter))
                .uuid(teamId + Constants.UNDERLINE + TaskNotifyCategoryEnumVO.TRADE_SETTLEMENT.getCode())
                .build();
    }

    public NotifyTask refundTask(TradeRefundOrderEntity tradeRefundOrderEntity, RefundTypeEnumVO refundTypeEnumVO, String notifyMQ) {
        TaskNotifyCategoryEnumVO notifyCategoryEnumVO = notifyCategory(refundTypeEnumVO);
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("type", refundTypeEnumVO.getCode());
        parameter.put("userId", tradeRefundOrderEntity.getUserId());
        parameter.put("teamId", tradeRefundOrderEntity.getTeamId());
        parameter.put("orderId", tradeRefundOrderEntity.getOrderId());
        parameter.put("outTradeNo", tradeRefundOrderEntity.getOutTradeNo());
        parameter.put("activityId", tradeRefundOrderEntity.getActivityId());

        return NotifyTask.builder()
                .activityId(tradeRefundOrderEntity.getActivityId())
                .teamId(tradeRefundOrderEntity.getTeamId())
                .notifyCategory(notifyCategoryEnumVO.getCode())
                .notifyType(NotifyTypeEnumVO.MQ.getCode())
                .notifyMQ(notifyMQ)
                .notifyCount(0)
                .notifyStatus(0)
                .parameterJson(JSON.toJSONString(parameter))
                .uuid(tradeRefundOrderEntity.getTeamId() + Constants.UNDERLINE + notifyCategoryEnumVO.getCode() + Constants.UNDERLINE + tradeRefundOrderEntity.getOrderId())
                .build();
    }

    private TaskNotifyCategoryEnumVO notifyCategory(RefundTypeEnumVO refundTypeEnumVO) {
        switch (refundTypeEnumVO) {
            case UNPAID_UNLOCK:
                return TaskNotifyCategoryEnumVO.TRADE_UNPAID2REFUND;
            case PAID_UNFORMED:
                return TaskNotifyCategoryEnumVO.TRADE_PAID2REFUND;
            case PAID_FORMED:
                return TaskNotifyCategoryEnumVO.TRADE_PAID_TEAM2REFUND;
            default:
                throw new IllegalArgumentException("unsupported refund type: " + refundTypeEnumVO);
        }
    }

}
