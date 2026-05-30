package cn.bugstack.test.trigger.mq;

import cn.bugstack.api.dto.MqFailedMessageResponseDTO;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.trigger.support.MqOpsResponseAssembler;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MqOpsResponseAssemblerUnitTest {

    @Test
    public void toFailedMessageResponsesShouldMapDomainRecordToApiDto() {
        MqOpsResponseAssembler assembler = new MqOpsResponseAssembler();
        MessageRecordEntity entity = MessageRecordEntity.builder()
                .messageId("msg_001")
                .exchangeName("market.exchange")
                .queueName("market.queue")
                .messageBody("{\"orderId\":\"001\"}")
                .status(MessageRecordEntity.STATUS_FAIL)
                .retryCount(3)
                .errorMessage("timeout")
                .build();

        List<MqFailedMessageResponseDTO> result = assembler.toFailedMessageResponses(Arrays.asList(entity));

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("msg_001", result.get(0).getMessageId());
        Assert.assertEquals("market.exchange", result.get(0).getExchangeName());
        Assert.assertEquals("market.queue", result.get(0).getQueueName());
        Assert.assertEquals("{\"orderId\":\"001\"}", result.get(0).getMessageBody());
        Assert.assertEquals(Integer.valueOf(MessageRecordEntity.STATUS_FAIL), result.get(0).getStatus());
        Assert.assertEquals(Integer.valueOf(3), result.get(0).getRetryCount());
        Assert.assertEquals("timeout", result.get(0).getErrorMessage());
    }

    @Test
    public void toFailedMessageResponsesShouldReturnEmptyListForNullInput() {
        MqOpsResponseAssembler assembler = new MqOpsResponseAssembler();

        List<MqFailedMessageResponseDTO> result = assembler.toFailedMessageResponses(null);

        Assert.assertTrue(result.isEmpty());
    }

}
