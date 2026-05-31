package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import cn.bugstack.infrastructure.dao.po.SeckillOrderOutbox;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps seckill order outbox PO and domain entity.
 */
@Component
public class SeckillOrderOutboxMapper {

    public SeckillOrderOutbox toPo(SeckillOrderOutboxEntity entity) {
        if (null == entity) {
            return null;
        }
        return SeckillOrderOutbox.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .routeKey(entity.getRouteKey())
                .topic(entity.getTopic())
                .messageBody(entity.getMessageBody())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .nextRetryTime(entity.getNextRetryTime())
                .errorMessage(entity.getErrorMessage())
                .traceId(entity.getTraceId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    public SeckillOrderOutboxEntity toEntity(SeckillOrderOutbox record) {
        if (null == record) {
            return null;
        }
        return SeckillOrderOutboxEntity.builder()
                .id(record.getId())
                .messageId(record.getMessageId())
                .routeKey(record.getRouteKey())
                .topic(record.getTopic())
                .messageBody(record.getMessageBody())
                .status(record.getStatus())
                .retryCount(record.getRetryCount())
                .nextRetryTime(record.getNextRetryTime())
                .errorMessage(record.getErrorMessage())
                .traceId(record.getTraceId())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }

    public List<SeckillOrderOutboxEntity> toEntityList(List<SeckillOrderOutbox> records) {
        List<SeckillOrderOutboxEntity> result = new ArrayList<>();
        if (null == records || records.isEmpty()) {
            return result;
        }
        for (SeckillOrderOutbox record : records) {
            result.add(toEntity(record));
        }
        return result;
    }

}
