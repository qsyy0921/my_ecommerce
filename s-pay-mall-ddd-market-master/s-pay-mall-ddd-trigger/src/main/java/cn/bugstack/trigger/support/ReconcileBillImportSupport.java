package cn.bugstack.trigger.support;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ReconcileBillImportSupport {

    @Resource
    private IOrderReconcileService orderReconcileService;

    @Resource
    private ReconcileAdminSupport adminSupport;

    public Response<Integer> importBill(String csvText, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            int count = orderReconcileService.importThirdPartyBillCsv(csvText);
            adminSupport.audit(operator, "IMPORT_BILL", "THIRD_PARTY_BILL", adminSupport.preview(csvText, 1024), "count=" + count);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("import third party bill failed", e);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
