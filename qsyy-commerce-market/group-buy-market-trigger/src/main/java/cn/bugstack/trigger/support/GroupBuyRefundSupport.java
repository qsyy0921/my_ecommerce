package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.RefundMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.RefundMarketPayOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.bugstack.domain.trade.service.ITradeRefundOrderService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class GroupBuyRefundSupport {

    @Resource
    private ITradeRefundOrderService tradeRefundOrderService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    @Resource
    private GroupBuyTradeRequestValidator requestValidator;

    @Resource
    private GroupBuyTradeCommandAssembler commandAssembler;

    @Resource
    private GroupBuyTradeResponseAssembler responseAssembler;

    public Response<RefundMarketPayOrderResponseDTO> refund(RefundMarketPayOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("group_buy_refund", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }
            log.info("营销拼团退单开始:{} outTradeNo:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo());

            if (!requestValidator.validRefund(requestDTO)) {
                businessLogger.warn("group_buy_refund", "illegal_parameter", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }

            TradeRefundBehaviorEntity tradeRefundBehaviorEntity = tradeRefundOrderService.refundOrder(commandAssembler.toTradeRefundCommand(requestDTO));
            RefundMarketPayOrderResponseDTO responseDTO = responseAssembler.toRefundResponse(tradeRefundBehaviorEntity);
            Response<RefundMarketPayOrderResponseDTO> response = Response.<RefundMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();

            log.info("营销拼团退单完成:{} outTradeNo:{} response:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo(), JSON.toJSONString(response));
            businessLogger.info("group_buy_refund", "success", businessLogger.fields(
                    "userId", requestDTO.getUserId(),
                    "orderId", responseDTO.getOrderId(),
                    "teamId", responseDTO.getTeamId(),
                    "outTradeNo", requestDTO.getOutTradeNo(),
                    "refundCode", responseDTO.getCode(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return response;
        } catch (AppException e) {
            log.error("营销拼团退单异常:{} RefundMarketPayOrderRequestDTO:{}", null == requestDTO ? null : requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            businessLogger.warn("group_buy_refund", "business_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "code", e.getCode(),
                    "info", e.getInfo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<RefundMarketPayOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("营销拼团退单失败:{} RefundMarketPayOrderRequestDTO:{}", null == requestDTO ? null : requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            businessLogger.error("group_buy_refund", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<RefundMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private Response<RefundMarketPayOrderResponseDTO> illegalParameter() {
        return Response.<RefundMarketPayOrderResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

}
