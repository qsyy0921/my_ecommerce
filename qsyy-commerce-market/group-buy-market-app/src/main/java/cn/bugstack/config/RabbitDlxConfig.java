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

    @Bean("groupBuyMarketDlxExchange")
    public DirectExchange dlxExchange(@Value("${spring.rabbitmq.config.dlx.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean("groupBuyMarketDlxQueue")
    public Queue dlxQueue(@Value("${spring.rabbitmq.config.dlx.queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean("groupBuyMarketDlxBinding")
    public Binding dlxBinding(@Value("${spring.rabbitmq.config.dlx.routing_key}") String routingKey,
                              Queue groupBuyMarketDlxQueue,
                              DirectExchange groupBuyMarketDlxExchange) {
        return BindingBuilder.bind(groupBuyMarketDlxQueue)
                .to(groupBuyMarketDlxExchange)
                .with(routingKey);
    }

}
