package cn.bugstack.api;

import cn.bugstack.api.dto.LockSeckillOrderRequestDTO;
import cn.bugstack.api.dto.LockSeckillOrderResponseDTO;
import cn.bugstack.api.dto.QuerySeckillOrderResultRequestDTO;
import cn.bugstack.api.dto.RefundSeckillOrderRequestDTO;
import cn.bugstack.api.dto.RefundSeckillOrderResponseDTO;
import cn.bugstack.api.dto.SeckillMarketRequestDTO;
import cn.bugstack.api.dto.SeckillMarketResponseDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderRequestDTO;
import cn.bugstack.api.dto.SettlementSeckillOrderResponseDTO;
import cn.bugstack.api.response.Response;

/**
 * Seckill market service API.
 */
public interface ISeckillMarketService {

    Response<SeckillMarketResponseDTO> querySeckillMarketConfig(SeckillMarketRequestDTO requestDTO);

    Response<LockSeckillOrderResponseDTO> lockSeckillOrder(LockSeckillOrderRequestDTO requestDTO);

    Response<LockSeckillOrderResponseDTO> querySeckillOrderResult(QuerySeckillOrderResultRequestDTO requestDTO);

    Response<SettlementSeckillOrderResponseDTO> settlementSeckillOrder(SettlementSeckillOrderRequestDTO requestDTO);

    Response<RefundSeckillOrderResponseDTO> refundSeckillOrder(RefundSeckillOrderRequestDTO requestDTO);

}
