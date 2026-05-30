package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.trade.model.entity.TradePaySettlementEntity;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class GroupBuySettlementSupport {

    @Resource
    private ITradeSettlementOrderService tradeSettlementOrderService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    @Resource
    private GroupBuyTradeRequestValidator requestValidator;

    @Resource
    private GroupBuyTradeCommandAssembler commandAssembler;

    @Resource
    private GroupBuyTradeResponseAssembler responseAssembler;

    public Response<SettlementMarketPayOrderResponseDTO> settlement(SettlementMarketPayOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("group_buy_settlement", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }
            log.info("营销交易组队结算开始:{} outTradeNo:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo());

            if (!requestValidator.validSettlement(requestDTO)) {
                businessLogger.warn("group_buy_settlement", "illegal_parameter", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }

            TradePaySettlementEntity tradePaySettlementEntity = tradeSettlementOrderService.settlementMarketPayOrder(commandAssembler.toTradePaySuccess(requestDTO));
            SettlementMarketPayOrderResponseDTO responseDTO = responseAssembler.toSettlementResponse(tradePaySettlementEntity);
            Response<SettlementMarketPayOrderResponseDTO> response = Response.<SettlementMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();

            log.info("营销交易组队结算完成:{} outTradeNo:{} response:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo(), JSON.toJSONString(response));
            businessLogger.info("group_buy_settlement", "success", businessLogger.fields(
                    "userId", requestDTO.getUserId(),
                    "activityId", responseDTO.getActivityId(),
                    "teamId", responseDTO.getTeamId(),
                    "outTradeNo", responseDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return response;
        } catch (AppException e) {
            log.error("营销交易组队结算异常:{} LockMarketPayOrderRequestDTO:{}", null == requestDTO ? null : requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            businessLogger.warn("group_buy_settlement", "business_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "code", e.getCode(),
                    "info", e.getInfo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<SettlementMarketPayOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("营销交易组队结算失败:{} LockMarketPayOrderRequestDTO:{}", null == requestDTO ? null : requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            businessLogger.error("group_buy_settlement", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<SettlementMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private Response<SettlementMarketPayOrderResponseDTO> illegalParameter() {
        return Response.<SettlementMarketPayOrderResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

}
