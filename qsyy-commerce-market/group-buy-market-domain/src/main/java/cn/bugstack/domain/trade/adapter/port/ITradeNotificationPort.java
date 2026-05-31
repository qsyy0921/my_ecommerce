package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;

/**
 * 拼团通知发送端口。
 */
public interface ITradeNotificationPort {

    String notify(NotifyTaskEntity notifyTask) throws Exception;

}
