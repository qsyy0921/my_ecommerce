package cn.bugstack.trigger.http;

import cn.bugstack.api.IMarketTradeService;
import cn.bugstack.api.dto.*;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.domain.trade.service.ITradeRefundOrderService;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.trigger.support.GroupBuyTradeCommandAssembler;
import cn.bugstack.trigger.support.GroupBuyTradeRequestValidator;
import cn.bugstack.trigger.support.GroupBuyTradeResponseAssembler;
import cn.bugstack.trigger.support.StructuredBusinessLogger;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 营销交易服务
 * @create 2025-01-11 14:01
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/trade/")
public class MarketTradeController implements IMarketTradeService {

    @Resource
    private IIndexGroupBuyMarketService indexGroupBuyMarketService;
    @Resource
    private ITradeLockOrderService tradeOrderService;
    @Resource
    private ITradeSettlementOrderService tradeSettlementOrderService;
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

    /**
     * 拼团营销锁单
     */
    @RequestMapping(value = "lock_market_pay_order", method = RequestMethod.POST)
    @Override
    public Response<LockMarketPayOrderResponseDTO> lockMarketPayOrder(@RequestBody LockMarketPayOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("group_buy_lock_order", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 参数
            String userId = requestDTO.getUserId();
            String source = requestDTO.getSource();
            String channel = requestDTO.getChannel();
            String goodsId = requestDTO.getGoodsId();
            Long activityId = requestDTO.getActivityId();
            String outTradeNo = requestDTO.getOutTradeNo();
            String teamId = requestDTO.getTeamId();
            LockMarketPayOrderRequestDTO.NotifyConfigVO notifyConfigVO = requestDTO.getNotifyConfigVO();

            log.info("营销交易锁单:{} LockMarketPayOrderRequestDTO:{}", userId, JSON.toJSONString(requestDTO));

            if (!requestValidator.validLock(requestDTO)) {
                businessLogger.warn("group_buy_lock_order", "illegal_parameter", businessLogger.fields(
                        "userId", userId,
                        "activityId", activityId,
                        "goodsId", goodsId,
                        "teamId", teamId,
                        "outTradeNo", outTradeNo,
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }
            NotifyTypeEnumVO notifyTypeEnumVO = requestValidator.resolveNotifyType(requestDTO);
            if (null == notifyTypeEnumVO) {
                businessLogger.warn("group_buy_lock_order", "illegal_notify_type", businessLogger.fields(
                        "userId", userId,
                        "activityId", activityId,
                        "goodsId", goodsId,
                        "teamId", teamId,
                        "outTradeNo", outTradeNo,
                        "notifyType", notifyConfigVO.getNotifyType(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }
            if (!requestValidator.validNotifyUrl(requestDTO, notifyTypeEnumVO)) {
                businessLogger.warn("group_buy_lock_order", "illegal_notify_url", businessLogger.fields(
                        "userId", userId,
                        "activityId", activityId,
                        "goodsId", goodsId,
                        "teamId", teamId,
                        "outTradeNo", outTradeNo,
                        "notifyType", notifyConfigVO.getNotifyType(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 查询 outTradeNo 是否已经存在交易记录
            MarketPayOrderEntity marketPayOrderEntity = tradeOrderService.queryNoPayMarketPayOrderByOutTradeNo(userId, outTradeNo);
            if (null != marketPayOrderEntity) {
                log.info("交易锁单记录(存在):{} marketPayOrderEntity:{}", userId, JSON.toJSONString(marketPayOrderEntity));
                businessLogger.info("group_buy_lock_order", "idempotent_hit", businessLogger.fields(
                        "userId", userId,
                        "activityId", activityId,
                        "goodsId", goodsId,
                        "teamId", marketPayOrderEntity.getTeamId(),
                        "outTradeNo", outTradeNo,
                        "orderId", marketPayOrderEntity.getOrderId(),
                        "status", marketPayOrderEntity.getTradeOrderStatusEnumVO().getCode(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(responseAssembler.toLockResponse(marketPayOrderEntity))
                        .build();
            }

            // 判断拼团锁单是否完成了目标
            if (requestValidator.hasTeamId(teamId)) {
                GroupBuyProgressVO groupBuyProgressVO = tradeOrderService.queryGroupBuyProgress(teamId);
                if (null != groupBuyProgressVO && Objects.equals(groupBuyProgressVO.getTargetCount(), groupBuyProgressVO.getLockCount())) {
                    log.info("交易锁单拦截-拼单目标已达成:{} {}", userId, teamId);
                    businessLogger.warn("group_buy_lock_order", "team_full", businessLogger.fields(
                            "userId", userId,
                            "activityId", activityId,
                            "goodsId", goodsId,
                            "teamId", teamId,
                            "outTradeNo", outTradeNo,
                            "targetCount", groupBuyProgressVO.getTargetCount(),
                            "lockCount", groupBuyProgressVO.getLockCount(),
                            "costMs", System.currentTimeMillis() - startMillis));
                    return Response.<LockMarketPayOrderResponseDTO>builder()
                            .code(ResponseCode.E0006.getCode())
                            .info(ResponseCode.E0006.getInfo())
                            .build();
                }
            }

            // 营销优惠试算
            TrialBalanceEntity trialBalanceEntity = indexGroupBuyMarketService.indexMarketTrial(commandAssembler.toMarketProduct(requestDTO));

            // 人群限定
            if (!trialBalanceEntity.getIsVisible() || !trialBalanceEntity.getIsEnable()) {
                businessLogger.warn("group_buy_lock_order", "activity_invisible", businessLogger.fields(
                        "userId", userId,
                        "activityId", activityId,
                        "goodsId", goodsId,
                        "teamId", teamId,
                        "outTradeNo", outTradeNo,
                        "visible", trialBalanceEntity.getIsVisible(),
                        "enable", trialBalanceEntity.getIsEnable(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.E0007.getCode())
                        .info(ResponseCode.E0007.getInfo())
                        .build();
            }

            GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = trialBalanceEntity.getGroupBuyActivityDiscountVO();

            // 营销优惠锁单
            marketPayOrderEntity = tradeOrderService.lockMarketPayOrder(
                    commandAssembler.toUser(userId),
                    commandAssembler.toPayActivity(teamId, activityId, groupBuyActivityDiscountVO),
                    commandAssembler.toPayDiscount(requestDTO, trialBalanceEntity, notifyTypeEnumVO));

            log.info("交易锁单记录(新):{} marketPayOrderEntity:{}", userId, JSON.toJSONString(marketPayOrderEntity));
            businessLogger.info("group_buy_lock_order", "success", businessLogger.fields(
                    "userId", userId,
                    "activityId", activityId,
                    "goodsId", goodsId,
                    "teamId", marketPayOrderEntity.getTeamId(),
                    "outTradeNo", outTradeNo,
                    "orderId", marketPayOrderEntity.getOrderId(),
                    "status", marketPayOrderEntity.getTradeOrderStatusEnumVO().getCode(),
                    "payPrice", marketPayOrderEntity.getPayPrice(),
                    "costMs", System.currentTimeMillis() - startMillis));

            // 返回结果
            return Response.<LockMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseAssembler.toLockResponse(marketPayOrderEntity))
                    .build();
        } catch (AppException e) {
            log.error("营销交易锁单业务异常:{} LockMarketPayOrderRequestDTO:{}", null == requestDTO ? null : requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            businessLogger.warn("group_buy_lock_order", "business_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "activityId", null == requestDTO ? null : requestDTO.getActivityId(),
                    "goodsId", null == requestDTO ? null : requestDTO.getGoodsId(),
                    "teamId", null == requestDTO ? null : requestDTO.getTeamId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "code", e.getCode(),
                    "info", e.getInfo(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<LockMarketPayOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("营销交易锁单服务失败:{} LockMarketPayOrderRequestDTO:{}", null == requestDTO ? null : requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            businessLogger.error("group_buy_lock_order", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "activityId", null == requestDTO ? null : requestDTO.getActivityId(),
                    "goodsId", null == requestDTO ? null : requestDTO.getGoodsId(),
                    "teamId", null == requestDTO ? null : requestDTO.getTeamId(),
                    "outTradeNo", null == requestDTO ? null : requestDTO.getOutTradeNo(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<LockMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "settlement_market_pay_order", method = RequestMethod.POST)
    @Override
    public Response<SettlementMarketPayOrderResponseDTO> settlementMarketPayOrder(@RequestBody SettlementMarketPayOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("group_buy_settlement", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<SettlementMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }
            log.info("营销交易组队结算开始:{} outTradeNo:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo());

            if (!requestValidator.validSettlement(requestDTO)) {
                businessLogger.warn("group_buy_settlement", "illegal_parameter", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<SettlementMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 1. 结算服务
            TradePaySettlementEntity tradePaySettlementEntity = tradeSettlementOrderService.settlementMarketPayOrder(commandAssembler.toTradePaySuccess(requestDTO));

            SettlementMarketPayOrderResponseDTO responseDTO = responseAssembler.toSettlementResponse(tradePaySettlementEntity);

            // 返回结果
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

    @RequestMapping(value = "refund_market_pay_order", method = RequestMethod.POST)
    @Override
    public Response<RefundMarketPayOrderResponseDTO> refundMarketPayOrder(@RequestBody RefundMarketPayOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("group_buy_refund", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<RefundMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }
            log.info("营销拼团退单开始:{} outTradeNo:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo());

            if (!requestValidator.validRefund(requestDTO)) {
                businessLogger.warn("group_buy_refund", "illegal_parameter", businessLogger.fields(
                        "userId", requestDTO.getUserId(),
                        "outTradeNo", requestDTO.getOutTradeNo(),
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<RefundMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 1. 退单服务
            TradeRefundBehaviorEntity tradeRefundBehaviorEntity = tradeRefundOrderService.refundOrder(commandAssembler.toTradeRefundCommand(requestDTO));

            RefundMarketPayOrderResponseDTO responseDTO = responseAssembler.toRefundResponse(tradeRefundBehaviorEntity);

            // 返回结果
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

}
