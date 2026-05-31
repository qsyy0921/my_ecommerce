package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IProductQueryPort;
import cn.bugstack.domain.order.model.entity.ProductEntity;
import cn.bugstack.infrastructure.gateway.ProductRPC;
import cn.bugstack.infrastructure.gateway.dto.ProductDTO;
import org.springframework.stereotype.Component;

@Component
public class ProductPort implements IProductQueryPort {

    private final ProductRPC productRPC;

    public ProductPort(ProductRPC productRPC) {
        this.productRPC = productRPC;
    }

    @Override
    public ProductEntity queryProductByProductId(String productId) {
        ProductDTO productDTO = productRPC.queryProductByProductId(productId);
        return ProductEntity.builder()
                .productId(productDTO.getProductId())
                .productName(productDTO.getProductName())
                .productDesc(productDTO.getProductDesc())
                .price(productDTO.getPrice())
                .build();
    }

}
