package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradeNotificationPort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.adapter.support.TradeNotificationChannelDispatcher;
import cn.bugstack.infrastructure.adapter.support.TradeNotificationLockSupport;
import cn.bugstack.infrastructure.adapter.support.TradeNotificationLockSupport.TradeNotificationLock;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class TradeNotificationPort implements ITradeNotificationPort {

    @Resource
    private TradeNotificationLockSupport lockSupport;
    @Resource
    private TradeNotificationChannelDispatcher channelDispatcher;

    @Override
    public String notify(NotifyTaskEntity notifyTask) throws Exception {
        TradeNotificationLock lock = null;
        try {
            lock = lockSupport.tryLock(notifyTask);
            if (null == lock) {
                return NotifyTaskHTTPEnumVO.NULL.getCode();
            }
            return channelDispatcher.dispatch(notifyTask);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("拼团通知任务抢占锁被中断 teamId:{} uuid:{}", notifyTask.getTeamId(), notifyTask.getUuid(), e);
            return NotifyTaskHTTPEnumVO.ERROR.getCode();
        } catch (Exception e) {
            log.error("拼团通知任务发送失败 teamId:{} uuid:{} notifyType:{}", notifyTask.getTeamId(), notifyTask.getUuid(), notifyTask.getNotifyType(), e);
            return NotifyTaskHTTPEnumVO.ERROR.getCode();
        } finally {
            lockSupport.unlockIfHeld(lock);
        }
    }

}
