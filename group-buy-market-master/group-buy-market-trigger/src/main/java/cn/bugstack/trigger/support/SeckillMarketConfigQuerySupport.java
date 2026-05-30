package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.SeckillMarketRequestDTO;
import cn.bugstack.api.dto.SeckillMarketResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillMarketConfigQuerySupport {

    @Resource
    private ISeckillService seckillService;

    @Resource
    private SeckillRequestValidator requestValidator;

    @Resource
    private SeckillResponseAssembler responseAssembler;

    public Response<SeckillMarketResponseDTO> query(SeckillMarketRequestDTO requestDTO) {
        try {
            log.debug("query seckill market config start requestDTO:{}", JSON.toJSONString(requestDTO));
            if (!requestValidator.validQueryMarketConfig(requestDTO)) {
                return Response.<SeckillMarketResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            SeckillActivityEntity seckillActivityEntity = seckillService.querySeckillActivity(
                    requestDTO.getActivityId(),
                    requestDTO.getSource(),
                    requestDTO.getChannel(),
                    requestDTO.getGoodsId());

            return Response.<SeckillMarketResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseAssembler.toMarketResponse(seckillActivityEntity))
                    .build();
        } catch (AppException e) {
            log.error("query seckill market config business error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<SeckillMarketResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query seckill market config error requestDTO:{}", JSON.toJSONString(requestDTO), e);
            return Response.<SeckillMarketResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
