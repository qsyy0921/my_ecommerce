package cn.bugstack.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ dead-letter queue declaration.
 */
@Configuration
public class RabbitDlxConfig {

    @Bean("sPayMallDlxExchange")
    public DirectExchange dlxExchange(@Value("${spring.rabbitmq.config.dlx.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean("sPayMallDlxQueue")
    public Queue dlxQueue(@Value("${spring.rabbitmq.config.dlx.queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean("sPayMallDlxBinding")
    public Binding dlxBinding(@Value("${spring.rabbitmq.config.dlx.routing_key}") String routingKey,
                              Queue sPayMallDlxQueue,
                              DirectExchange sPayMallDlxExchange) {
        return BindingBuilder.bind(sPayMallDlxQueue)
                .to(sPayMallDlxExchange)
                .with(routingKey);
    }

}
