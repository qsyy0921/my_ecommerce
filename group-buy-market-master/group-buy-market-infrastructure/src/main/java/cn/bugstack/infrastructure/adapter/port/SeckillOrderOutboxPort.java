package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderOutboxPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderOutboxMapper;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderOutboxRetrySupport;
import cn.bugstack.infrastructure.dao.ISeckillOrderOutboxDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

/**
 * Reliable outbox adapter for seckill order creation messages.
 */
@Repository
public class SeckillOrderOutboxPort implements ISeckillOrderOutboxPort {

    @Resource
    private ISeckillOrderOutboxDao seckillOrderOutboxDao;
    @Resource
    private SeckillOrderOutboxMapper seckillOrderOutboxMapper;
    @Resource
    private SeckillOrderOutboxRetrySupport retrySupport;

    @Override
    public boolean recordInit(SeckillOrderCreateMessageEntity message, String topic, String messageBody) {
        SeckillOrderOutboxEntity outbox = SeckillOrderOutboxEntity.init(message, topic, messageBody);
        seckillOrderOutboxDao.insertIgnore(seckillOrderOutboxMapper.toPo(outbox));
        return true;
    }

    @Override
    public void markSent(String messageId) {
        seckillOrderOutboxDao.markSent(messageId);
    }

    @Override
    public void markFailed(String messageId, String errorMessage) {
        retrySupport.markFailed(messageId, StringUtils.left(errorMessage, 512));
    }

    @Override
    public int retryDueMessages(int limit) {
        return retrySupport.retryDueMessages(limit);
    }

    @Override
    public int retryManualMessages(int limit) {
        return retrySupport.retryManualMessages(limit);
    }

    @Override
    public List<SeckillOrderOutboxEntity> queryMessages(Integer status, int limit) {
        return seckillOrderOutboxMapper.toEntityList(seckillOrderOutboxDao.queryMessageList(status, limit));
    }

    @Override
    public int countMessages(Integer status) {
        return seckillOrderOutboxDao.countByStatus(status);
    }

}
