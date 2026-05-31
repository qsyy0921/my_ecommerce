package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.SeckillOrderOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Seckill order reliable outbox DAO.
 */
@Mapper
public interface ISeckillOrderOutboxDao {

    int insertIgnore(SeckillOrderOutbox seckillOrderOutbox);

    SeckillOrderOutbox queryByMessageId(String messageId);

    int markSent(String messageId);

    int markFailed(@Param("messageId") String messageId,
                   @Param("errorMessage") String errorMessage,
                   @Param("maxRetry") Integer maxRetry,
                   @Param("retryDelaySeconds") Integer retryDelaySeconds);

    List<SeckillOrderOutbox> queryDueMessageList(@Param("limit") Integer limit);

    List<SeckillOrderOutbox> queryManualRetryMessageList(@Param("limit") Integer limit);

}
