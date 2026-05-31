package cn.bugstack.trigger.listener;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Dead-letter queue observer.
 */
@Slf4j
@Component
public class DeadLetterTopicListener {

    @RabbitListener(queues = "${spring.rabbitmq.config.dlx.queue}")
    public void listener(Message amqpMessage, Channel channel) throws Exception {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            log.error("dead letter message received messageId:{} exchange:{} routingKey:{} xDeath:{} body:{}",
                    amqpMessage.getMessageProperties().getMessageId(),
                    amqpMessage.getMessageProperties().getReceivedExchange(),
                    amqpMessage.getMessageProperties().getReceivedRoutingKey(),
                    amqpMessage.getMessageProperties().getHeaders().get("x-death"),
                    body);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("dead letter message observe failed", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

}
