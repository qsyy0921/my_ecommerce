package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TradeNotifyTaskMapper {

    public List<NotifyTaskEntity> toEntities(List<NotifyTask> notifyTaskList) {
        if (null == notifyTaskList || notifyTaskList.isEmpty()) {
            return new ArrayList<>();
        }
        List<NotifyTaskEntity> notifyTaskEntities = new ArrayList<>(notifyTaskList.size());
        for (NotifyTask notifyTask : notifyTaskList) {
            notifyTaskEntities.add(toEntity(notifyTask));
        }
        return notifyTaskEntities;
    }

    public NotifyTaskEntity toEntity(NotifyTask notifyTask) {
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

    public NotifyTask toKey(NotifyTaskEntity notifyTaskEntity) {
        return NotifyTask.builder()
                .teamId(notifyTaskEntity.getTeamId())
                .uuid(notifyTaskEntity.getUuid())
                .build();
    }

}
