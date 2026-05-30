package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.QueryOrderListRequestDTO;
import cn.bugstack.api.dto.QueryOrderListResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class MallOrderQuerySupport {

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Resource
    private IOrderService orderService;

    @Resource
    private OrderListResponseAssembler orderListResponseAssembler;

    public Response<QueryOrderListResponseDTO> queryUserOrderList(QueryOrderListRequestDTO request) {
        try {
            if (null == request) {
                return illegalParameter();
            }

            String userId = request.getUserId();
            Long lastId = request.getLastId();
            int pageSize = normalizePageSize(request.getPageSize());
            log.info("查询用户订单列表开始 userId:{} lastId:{} pageSize:{}", userId, lastId, pageSize);

            List<OrderEntity> orderList = orderService.queryUserOrderList(userId, lastId, pageSize + 1);
            boolean hasMore = orderList.size() > pageSize;
            if (hasMore) {
                orderList = orderList.subList(0, pageSize);
            }

            QueryOrderListResponseDTO responseDTO = orderListResponseAssembler.assemble(orderList, hasMore);
            log.info("查询用户订单列表完成 userId:{} 返回订单数量:{} hasMore:{}", userId, responseDTO.getOrderList().size(), hasMore);
            return Response.<QueryOrderListResponseDTO>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("查询用户订单列表失败 userId:{}", null == request ? null : request.getUserId(), e);
            return Response.<QueryOrderListResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (null == pageSize || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return pageSize;
    }

    private Response<QueryOrderListResponseDTO> illegalParameter() {
        return Response.<QueryOrderListResponseDTO>builder()
                .code(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(Constants.ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

}
