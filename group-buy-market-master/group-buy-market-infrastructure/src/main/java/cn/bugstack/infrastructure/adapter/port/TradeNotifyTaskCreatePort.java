package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskCreatePort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.infrastructure.adapter.support.TradeNotifyTaskFactory;
import cn.bugstack.infrastructure.adapter.support.TradeNotifyTaskMapper;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TradeNotifyTaskCreatePort implements ITradeNotifyTaskCreatePort {

    @Resource
    private INotifyTaskDao notifyTaskDao;
    @Resource
    private TradeNotifyTaskFactory notifyTaskFactory;
    @Resource
    private TradeNotifyTaskMapper notifyTaskMapper;

    @Override
    public NotifyTaskEntity createSettlementTask(Long activityId, String teamId, NotifyConfigVO notifyConfigVO, List<String> outTradeNoList) {
        NotifyTask notifyTask = notifyTaskFactory.settlementTask(activityId, teamId, notifyConfigVO, outTradeNoList);
        notifyTaskDao.insert(notifyTask);
        return notifyTaskMapper.toEntity(notifyTask);
    }

    @Override
    public NotifyTaskEntity createRefundTask(TradeRefundOrderEntity tradeRefundOrderEntity, RefundTypeEnumVO refundTypeEnumVO, String notifyMQ) {
        NotifyTask notifyTask = notifyTaskFactory.refundTask(tradeRefundOrderEntity, refundTypeEnumVO, notifyMQ);
        notifyTaskDao.insert(notifyTask);
        return notifyTaskMapper.toEntity(notifyTask);
    }

}
