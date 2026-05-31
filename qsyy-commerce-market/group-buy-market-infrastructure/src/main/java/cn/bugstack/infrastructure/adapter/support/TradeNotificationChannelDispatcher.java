package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.gateway.GroupBuyNotifyService;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class TradeNotificationChannelDispatcher {

    @Resource
    private GroupBuyNotifyService groupBuyNotifyService;
    @Resource
    private EventPublisher publisher;

    public String dispatch(NotifyTaskEntity notifyTask) throws Exception {
        if (NotifyTypeEnumVO.HTTP.getCode().equals(notifyTask.getNotifyType())) {
            if (invalidNotifyUrl(notifyTask.getNotifyUrl())) {
                return NotifyTaskHTTPEnumVO.SUCCESS.getCode();
            }
            groupBuyNotifyService.groupBuyNotify(notifyTask.getNotifyUrl(), notifyTask.getParameterJson());
            return NotifyTaskHTTPEnumVO.SUCCESS.getCode();
        }

        if (NotifyTypeEnumVO.MQ.getCode().equals(notifyTask.getNotifyType())) {
            publisher.publish(notifyTask.getNotifyMQ(), notifyTask.getParameterJson());
            return NotifyTaskHTTPEnumVO.SUCCESS.getCode();
        }

        return NotifyTaskHTTPEnumVO.NULL.getCode();
    }

    private boolean invalidNotifyUrl(String notifyUrl) {
        return StringUtils.isBlank(notifyUrl) || "暂无".equals(notifyUrl);
    }

}
