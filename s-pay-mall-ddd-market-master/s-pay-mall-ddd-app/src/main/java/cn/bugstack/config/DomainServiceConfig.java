package cn.bugstack.config;

import cn.bugstack.domain.auth.adapter.port.ILoginPort;
import cn.bugstack.domain.auth.service.ILoginService;
import cn.bugstack.domain.auth.service.WeixinLoginService;
import cn.bugstack.domain.goods.adapter.repository.IGoodsRepository;
import cn.bugstack.domain.goods.service.GoodsService;
import cn.bugstack.domain.goods.service.IGoodsService;
import cn.bugstack.domain.message.adapter.repository.IMessageRecordRepository;
import cn.bugstack.domain.message.service.IMessageRecordService;
import cn.bugstack.domain.message.service.MessageRecordService;
import cn.bugstack.domain.order.adapter.event.PaySuccessMessageEvent;
import cn.bugstack.domain.order.adapter.port.IMarketOrderLockPort;
import cn.bugstack.domain.order.adapter.port.IMarketRefundPort;
import cn.bugstack.domain.order.adapter.port.IMarketSettlementPort;
import cn.bugstack.domain.order.adapter.port.IOrderPaySuccessMessagePort;
import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.adapter.port.IPayPort;
import cn.bugstack.domain.order.adapter.port.IProductQueryPort;
import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.domain.order.service.OrderService;
import cn.bugstack.domain.order.service.OrderReconcileService;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import com.google.common.cache.Cache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public IOrderService orderService(IOrderRepository orderRepository,
                                      IProductQueryPort productQueryPort,
                                      IMarketOrderLockPort marketOrderLockPort,
                                      IMarketSettlementPort marketSettlementPort,
                                      IMarketRefundPort marketRefundPort,
                                      IPayPort payPort,
                                      IPaymentFlowPort paymentFlowPort,
                                      IRefundFlowPort refundFlowPort,
                                      IOrderPaySuccessMessagePort orderPaySuccessMessagePort,
                                      IDomainTaskExecutor domainTaskExecutor) {
        return new OrderService(orderRepository, productQueryPort, marketOrderLockPort, marketSettlementPort, marketRefundPort, payPort, paymentFlowPort, refundFlowPort, orderPaySuccessMessagePort, domainTaskExecutor);
    }

    @Bean
    public IOrderReconcileService orderReconcileService(IOrderRepository orderRepository,
                                                        IOrderReconcileRepository orderReconcileRepository,
                                                        IMarketSettlementPort marketSettlementPort,
                                                        IOrderService orderService) {
        return new OrderReconcileService(orderRepository, orderReconcileRepository, marketSettlementPort, orderService);
    }

    @Bean
    public IGoodsService goodsService(IGoodsRepository goodsRepository) {
        return new GoodsService(goodsRepository);
    }

    @Bean
    public IMessageRecordService messageRecordService(IMessageRecordRepository messageRecordRepository) {
        return new MessageRecordService(messageRecordRepository);
    }

    @Bean
    public ILoginService loginService(ILoginPort loginPort,
                                      @Qualifier("openidToken") Cache<String, String> openidToken) {
        return new WeixinLoginService(loginPort, openidToken);
    }

    @Bean
    public PaySuccessMessageEvent paySuccessMessageEvent(@Value("${spring.rabbitmq.config.producer.topic_order_pay_success.routing_key}") String topicOrderPaySuccess) {
        return new PaySuccessMessageEvent(topicOrderPaySuccess);
    }

}
