package cn.bugstack.domain.order.adapter.port;

import java.math.BigDecimal;

public interface IPayPort {

    String createPayForm(String orderId, BigDecimal payAmount, String productName, String payChannel);

    boolean refund(String orderId, BigDecimal payAmount);

}
