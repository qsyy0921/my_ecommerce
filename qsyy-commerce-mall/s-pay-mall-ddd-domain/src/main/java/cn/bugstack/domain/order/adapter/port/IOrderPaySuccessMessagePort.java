package cn.bugstack.domain.order.adapter.port;

import java.util.List;

public interface IOrderPaySuccessMessagePort {

    void publish(String orderId);

    void publishAll(List<String> orderIdList);

}
