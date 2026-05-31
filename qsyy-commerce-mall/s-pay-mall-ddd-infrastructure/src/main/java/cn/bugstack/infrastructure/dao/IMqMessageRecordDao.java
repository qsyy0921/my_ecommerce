package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MQ consume idempotency record DAO.
 */
@Mapper
public interface IMqMessageRecordDao {

    void insert(MqMessageRecord mqMessageRecord);

    MqMessageRecord queryByMessageId(String messageId);

    int updateProcessing(String messageId);

    int updateSuccess(String messageId);

    int updateFail(MqMessageRecord mqMessageRecord);

    List<MqMessageRecord> queryFailedMessageList();

    List<MqMessageRecord> queryProducerFailedMessageList(@Param("limit") Integer limit);

    int countFailedMessages();

}
