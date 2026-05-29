package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskPort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.domain.trade.model.valobj.TaskNotifyCategoryEnumVO;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@Service
public class TradeNotifyTaskPort implements ITradeNotifyTaskPort {

    @Resource
    private INotifyTaskDao notifyTaskDao;

    @Override
    public NotifyTaskEntity createSettlementTask(Long activityId, String teamId, NotifyConfigVO notifyConfigVO, List<String> outTradeNoList) {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("teamId", teamId);
        parameter.put("outTradeNoList", outTradeNoList);

        NotifyTask notifyTask = NotifyTask.builder()
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

        notifyTaskDao.insert(notifyTask);
        return buildNotifyTaskEntity(notifyTask);
    }

    @Override
    public NotifyTaskEntity createRefundTask(TradeRefundOrderEntity tradeRefundOrderEntity, RefundTypeEnumVO refundTypeEnumVO, String notifyMQ) {
        TaskNotifyCategoryEnumVO notifyCategoryEnumVO = notifyCategory(refundTypeEnumVO);

        Map<String, Object> parameter = new HashMap<>();
        parameter.put("type", refundTypeEnumVO.getCode());
        parameter.put("userId", tradeRefundOrderEntity.getUserId());
        parameter.put("teamId", tradeRefundOrderEntity.getTeamId());
        parameter.put("orderId", tradeRefundOrderEntity.getOrderId());
        parameter.put("outTradeNo", tradeRefundOrderEntity.getOutTradeNo());
        parameter.put("activityId", tradeRefundOrderEntity.getActivityId());

        NotifyTask notifyTask = NotifyTask.builder()
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

        notifyTaskDao.insert(notifyTask);
        return buildNotifyTaskEntity(notifyTask);
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList() {
        List<NotifyTask> notifyTaskList = notifyTaskDao.queryUnExecutedNotifyTaskList();
        if (null == notifyTaskList || notifyTaskList.isEmpty()) {
            return new ArrayList<>();
        }

        List<NotifyTaskEntity> notifyTaskEntities = new ArrayList<>(notifyTaskList.size());
        for (NotifyTask notifyTask : notifyTaskList) {
            notifyTaskEntities.add(buildNotifyTaskEntity(notifyTask));
        }
        return notifyTaskEntities;
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList(String teamId) {
        NotifyTask notifyTask = notifyTaskDao.queryUnExecutedNotifyTaskByTeamId(teamId);
        if (null == notifyTask) {
            return new ArrayList<>();
        }
        return Collections.singletonList(buildNotifyTaskEntity(notifyTask));
    }

    @Override
    public int updateNotifyTaskStatusSuccess(NotifyTaskEntity notifyTaskEntity) {
        return notifyTaskDao.updateNotifyTaskStatusSuccess(buildNotifyTaskKey(notifyTaskEntity));
    }

    @Override
    public int updateNotifyTaskStatusError(NotifyTaskEntity notifyTaskEntity) {
        return notifyTaskDao.updateNotifyTaskStatusError(buildNotifyTaskKey(notifyTaskEntity));
    }

    @Override
    public int updateNotifyTaskStatusRetry(NotifyTaskEntity notifyTaskEntity) {
        return notifyTaskDao.updateNotifyTaskStatusRetry(buildNotifyTaskKey(notifyTaskEntity));
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

    private NotifyTask buildNotifyTaskKey(NotifyTaskEntity notifyTaskEntity) {
        return NotifyTask.builder()
                .teamId(notifyTaskEntity.getTeamId())
                .uuid(notifyTaskEntity.getUuid())
                .build();
    }

    private NotifyTaskEntity buildNotifyTaskEntity(NotifyTask notifyTask) {
        return NotifyTaskEntity.builder()
                .teamId(notifyTask.getTeamId())
                .notifyType(notifyTask.getNotifyType())
                .notifyMQ(notifyTask.getNotifyMQ())
                .notifyUrl(notifyTask.getNotifyUrl())
                .notifyCount(notifyTask.getNotifyCount())
                .parameterJson(notifyTask.getParameterJson())
                .uuid(notifyTask.getUuid())
                .build();
    }

}
