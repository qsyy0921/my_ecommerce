package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.LockSeckillOrderResponseDTO;
import cn.bugstack.api.dto.QuerySeckillOrderResultRequestDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillOrderResultQuerySupport {

    @Resource
    private ISeckillService seckillService;

    @Resource
    private SeckillRequestValidator requestValidator;

    @Resource
    private SeckillResponseAssembler responseAssembler;

    public Response<LockSeckillOrderResponseDTO> query(QuerySeckillOrderResultRequestDTO requestDTO) {
        try {
            log.debug("query seckill order result start requestDTO:{}", JSON.toJSONString(requestDTO));
            if (!requestValidator.validQueryOrderResult(requestDTO)) {
                return Response.<LockSeckillOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillOrderEntity seckillOrderEntity = seckillService.querySeckillResult(
                    requestDTO.getUserId(),
                    requestDTO.getActivityId(),
                    requestDTO.getOutTradeNo());

            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseAssembler.toLockResponse(seckillOrderEntity))
                    .build();
        } catch (AppException e) {
            log.error("query seckill order result business error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query seckill order result error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<LockSeckillOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
