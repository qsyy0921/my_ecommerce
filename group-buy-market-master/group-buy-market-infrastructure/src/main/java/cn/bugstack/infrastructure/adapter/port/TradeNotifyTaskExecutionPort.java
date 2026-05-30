package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskExecutionPort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.adapter.support.TradeNotifyTaskMapper;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@Service
public class TradeNotifyTaskExecutionPort implements ITradeNotifyTaskExecutionPort {

    @Resource
    private INotifyTaskDao notifyTaskDao;
    @Resource
    private TradeNotifyTaskMapper notifyTaskMapper;

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList() {
        return notifyTaskMapper.toEntities(notifyTaskDao.queryUnExecutedNotifyTaskList());
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList(String teamId) {
        NotifyTask notifyTask = notifyTaskDao.queryUnExecutedNotifyTaskByTeamId(teamId);
        if (null == notifyTask) {
            return Collections.emptyList();
        }
        return Collections.singletonList(notifyTaskMapper.toEntity(notifyTask));
    }

    @Override
    public int updateNotifyTaskStatusSuccess(NotifyTaskEntity notifyTaskEntity) {
        return notifyTaskDao.updateNotifyTaskStatusSuccess(notifyTaskMapper.toKey(notifyTaskEntity));
    }

    @Override
    public int updateNotifyTaskStatusError(NotifyTaskEntity notifyTaskEntity) {
        return notifyTaskDao.updateNotifyTaskStatusError(notifyTaskMapper.toKey(notifyTaskEntity));
    }

    @Override
    public int updateNotifyTaskStatusRetry(NotifyTaskEntity notifyTaskEntity) {
        return notifyTaskDao.updateNotifyTaskStatusRetry(notifyTaskMapper.toKey(notifyTaskEntity));
    }

}
