package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.LockMarketPayOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

@Slf4j
@Component
public class GroupBuyLockOrderSupport {

    @Resource
    private IIndexGroupBuyMarketService indexGroupBuyMarketService;

    @Resource
    private ITradeLockOrderService tradeOrderService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    @Resource
    private GroupBuyTradeRequestValidator requestValidator;

    @Resource
    private GroupBuyTradeCommandAssembler commandAssembler;

    @Resource
    private GroupBuyTradeResponseAssembler responseAssembler;

    public Response<LockMarketPayOrderResponseDTO> lock(LockMarketPayOrderRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("group_buy_lock_order", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return illegalParameter();
            }

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
                return illegalParameter();
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
                return illegalParameter();
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
                return illegalParameter();
            }

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
                return success(responseAssembler.toLockResponse(marketPayOrderEntity));
            }

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

            TrialBalanceEntity trialBalanceEntity = indexGroupBuyMarketService.indexMarketTrial(commandAssembler.toMarketProduct(requestDTO));
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
            return success(responseAssembler.toLockResponse(marketPayOrderEntity));
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

    private Response<LockMarketPayOrderResponseDTO> illegalParameter() {
        return Response.<LockMarketPayOrderResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

    private Response<LockMarketPayOrderResponseDTO> success(LockMarketPayOrderResponseDTO responseDTO) {
        return Response.<LockMarketPayOrderResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responseDTO)
                .build();
    }

}
