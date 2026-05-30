package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.QueryOrderListResponseDTO;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderListResponseAssembler {

    public QueryOrderListResponseDTO assemble(List<OrderEntity> orderList, boolean hasMore) {
        QueryOrderListResponseDTO responseDTO = new QueryOrderListResponseDTO();
        responseDTO.setOrderList(orderList.stream().map(this::toOrderInfo).collect(Collectors.toList()));
        responseDTO.setHasMore(hasMore);
        responseDTO.setLastId(orderList.isEmpty() ? null : orderList.get(orderList.size() - 1).getId());
        return responseDTO;
    }

    private QueryOrderListResponseDTO.OrderInfo toOrderInfo(OrderEntity order) {
        QueryOrderListResponseDTO.OrderInfo orderInfo = new QueryOrderListResponseDTO.OrderInfo();
        orderInfo.setId(order.getId());
        orderInfo.setUserId(order.getUserId());
        orderInfo.setProductId(order.getProductId());
        orderInfo.setProductName(order.getProductName());
        orderInfo.setOrderId(order.getOrderId());
        orderInfo.setOrderTime(order.getOrderTime());
        orderInfo.setTotalAmount(order.getTotalAmount());
        orderInfo.setStatus(order.getOrderStatusVO() != null ? order.getOrderStatusVO().getCode() : null);
        orderInfo.setPayUrl(order.getPayUrl());
        orderInfo.setMarketType(order.getMarketType());
        orderInfo.setMarketDeductionAmount(order.getMarketDeductionAmount());
        orderInfo.setPayAmount(order.getPayAmount());
        orderInfo.setPayTime(order.getPayTime());
        return orderInfo;
    }

}
