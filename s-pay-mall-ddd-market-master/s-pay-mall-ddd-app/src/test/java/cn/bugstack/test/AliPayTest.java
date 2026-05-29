package cn.bugstack.test;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

@Slf4j
public class AliPayTest {

    // 「沙箱环境」应用ID - 您的APPID，收款账号既是你的APPID对应支付宝账号。获取地址；https://open.alipay.com/develop/sandbox/app
    public static String app_id = "9021000132689924";
    // 「沙箱环境」商户私钥，你的PKCS8格式RSA2私钥
    public static String merchant_private_key = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCm5vMsZacZu7ShMOoWwHpCojCSBIr5Ik0IruxcHxDkuF6c4fLyPSyIxA+wv8mwGcQH6T4xhP2X+q6HwP9OuhSpg4QGxZkpbspQ7VkDICfklE6v3XzSucAxfedWtMzw8GJuASSnkO142xbTmBgPXA75zXtzYcAr5YVhQZ/SndfNFy9B3qHXuI8e7NJ/qCAhtK/0xC/2AJZiw9TStwguFjkulDMzspvwA576Zy8q9pTO5QyY5uzvwkVuVbnMB9TiW945b/pmYznYY0EcXECNbVN9Bg8ibgOwwm3WEZheDOpJzoSXFPOESjENp/cRNgcpZzicRQI23556jiokUFbSl16JAgMBAAECggEAXAMCmL1JzBQBVsQCqZxfSpDjufwrKYHRejIFOBwgUheagV7HcuhqvtNn2wdAh4cM3Vem4+HvdHPM+CZcGejYL0PD63/QGllrzAWtht2ZYJiqcKXohLvGywIgIVr3iPTdLswOixqI8JHIGcrFqOIVl33Cyy9epFSana1yEMjYCcYSHT9+h/2ZvARAQuFWA0kcWwlbL96HjSiKvBZdB8pJXYveqrAt7efSHJVQPaqDPJE1izSI55F9Y9HyrGb6NlCA7Hwf6r7M+7FoSkcAK4HVkXUOHV3amtDQJ6Bu2DfJ5U/k4rcdrlTb7AegKNjFVocaW4vpMxHZ9kEHvFk/l9+UIQKBgQDuR8ucPNKWcwgNoWOpTnXY7P4glTSex6VpkHfEQtbsc/7UDOsejAwnWFU9gsGus+x2Pp4V+JbhDvS5eqQqRSS1bpjzo5rr5na9ejyFpyP/C/ZduJmZ1a1nQFUG69FX5I+YgAqyBFWFWsBRSjDZnISTG+6gX7YCd1LRtUrkEsFujQKBgQCzUE7TRe2DJ1Z2HJg1b4BtFijJxoEerX99zeGAcUDoM1rF2/p8rmxSWKAopeCTIE90fr7/RGnAiCO43XG75MYxcaAyHjy/+de9U2xHZuOA1JSFc6on16DmuauS33Vq0narc5dXCzJXX/Nk3uRQX6JRXPqQC5omCbNx9cuPVq2e7QKBgCewRbXMW7c7icfbbeIxc2eiceGzeIRJ9L9UlN5X07ymxXdEf5Ui9pNHH0uTj/cteOFpJKO8OEVcXF7YkqeHxMmDlm5efdt1E6oTxijs4g4FTA/oQ41gDfX4vTDd7oyZQWHBUga8uhHwP8BKf0B/1YU7Tc/goUxf26eabtL2XMmxAoGBAKvpoqkLsdq8LDiw0Xzpmcb8J77LgZ9X15NVf40VB5WXJr/rVwuSbmfEcEiIP/bOU17s5VD21iywWQ8zv4bUnu9ocJ2cP8IOVrg88uLB0fwB0yLe8ZOhN7H88HGvq3LutLdL6xyYr3r1kvRyfZJRHd3DtrXDadZuFox7it2xzGNBAoGASg0rCIauq273MoHIFoYQnDmGDm1Jr1Iivo6ggPTyhmu1YkeHIXTDc4jv80W0PefyaJ8AN2joZFiEZFJgBb0u82Qv6XYas/YbLceAtq9QMmnHgzZbN7K8L4UidM7/2iDHOJez/Px6qBcm8Ugee4rmEt41EojYvQTXpDERVjGLR5Q=";
    // 「沙箱环境」支付宝公钥
    public static String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmgtSO8GV5YSl01AWgGWe2xKJ9oDfjzf5vNtJS6SSc5klmGMToVdsi3gmLipN04yzmnEEchmFxsuxKLLhdQuC4d9V74I6CVIZPPHtaSuz/T3EZTHtQIGwF705Yrq1bd63l70iTfkrS0Ry9f72SDZEBBLllXfFo+otChwRRN+UXDd8X/bplV3/cbRncV5yWRnHHCgzQiwpH3ilS+sOmMfdfac0bi/xB7HIU6nUX04VCjAR7itSr0OmU8HC6p20Ubvjs45R6VuR7FMI+OahCd3LDe/ayelScfQ4zavruk4HGx3TDH4hLDA3N+xid5Cu5erLDPHtFXfnQHI4n/opQaXo5wIDAQAB";
    // 「沙箱环境」服务器异步通知回调地址
    public static String notify_url = "https://xfg.natapp.cn/api/v1/alipay/alipay_notify_url";
    // 「沙箱环境」页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    public static String return_url = "https://gaga.plus";
    // 「沙箱环境」
    public static String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    // 签名方式
    public static String sign_type = "RSA2";
    // 字符编码格式
    public static String charset = "utf-8";

    private AlipayClient alipayClient;

    @Before
    public void init() {
        this.alipayClient = new DefaultAlipayClient(gatewayUrl,
                app_id,
                merchant_private_key,
                "json",
                charset,
                alipay_public_key,
                sign_type);
    }

    @Test
    public void test_aliPay_pageExecute() throws AlipayApiException {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();  // 发送请求的 Request类
        request.setNotifyUrl(notify_url);
        request.setReturnUrl(return_url);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", "xfg000091001902");  // 我们自己生成的订单编号
        bizContent.put("total_amount", "0.01"); // 订单的总金额
        bizContent.put("subject", "测试商品");   // 支付的名称
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");  // 固定配置
        request.setBizContent(bizContent.toString());

        String form = alipayClient.pageExecute(request).getBody();
        log.info("测试结果：{}", form);

        /**
         * 会生成一个form表单；
         * <form name="punchout_form" method="post" action="https://openapi-sandbox.dl.alipaydev.com/gateway.do?charset=utf-8&method=alipay.trade.page.pay&sign=CAAYYDIbvUNRDvY%2B%2BF5vghx2dL9wovodww8CK0%2FferNP1KtyXdytBVLdZKssaFJV%2B8QksVuKlU3qneWhWUuI7atLDgzpussJlJhxTMYQ3GpAfOP4PEBYQFE%2FORemzA2XPjEn88HU7esdJdUxCs602kiFoZO8nMac9iqN6P8deoGWYO4UAwE0RCV65PKeJTcy8mzhOTgkz7V018N9yIL0%2BEBf5iQJaP9tGXM4ODWwFRxJ4l1Egx46FNfjLAMzysy7D14LvTwBi5uDXV4Y%2Bp4VCnkxh3Jhkp%2BDP9SXx6Ay7QaoerxHA09kwYyLQrZ%2FdMZgoQ%2BxSEOgklIZtYj%2FLbfx1A%3D%3D&return_url=https%3A%2F%2Fgaga.plus&notify_url=http%3A%2F%2Fngrok.sscai.club%2Falipay%2FaliPayNotify_url&version=1.0&app_id=9021000132689924&sign_type=RSA2&timestamp=2023-12-13+11%3A36%3A29&alipay_sdk=alipay-sdk-java-4.38.157.ALL&format=json">
         * <input type="hidden" name="biz_content" value="{&quot;out_trade_no&quot;:&quot;100001001&quot;,&quot;total_amount&quot;:&quot;1.00&quot;,&quot;subject&quot;:&quot;测试&quot;,&quot;product_code&quot;:&quot;FAST_INSTANT_TRADE_PAY&quot;}">
         * <input type="submit" value="立即支付" style="display:none" >
         * </form>
         * <script>document.forms[0].submit();</script>
         */
    }

    /**
     * 查询订单
     */
    @Test
    public void test_alipay_certificateExecute() throws AlipayApiException {

        AlipayTradeQueryModel bizModel = new AlipayTradeQueryModel();
        bizModel.setOutTradeNo("571486993823");

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(bizModel);

        String body = alipayClient.execute(request).getBody();
        log.info("测试结果：{}", body);
    }

    /**
     * 退款接口
     */
    @Test
    public void test_alipay_refund() throws AlipayApiException {
        AlipayTradeRefundRequest request =new AlipayTradeRefundRequest();
        AlipayTradeRefundModel refundModel =new AlipayTradeRefundModel();
        refundModel.setOutTradeNo("daniel82AAAA000032333361X03");
        refundModel.setRefundAmount("1.00");
        refundModel.setRefundReason("退款说明");
        request.setBizModel(refundModel);

        AlipayTradeRefundResponse execute = alipayClient.execute(request);
        log.info("测试结果：{}", execute.isSuccess());
    }

    public static void main(String[] args) {
        System.out.println(new BigDecimal("9.99").doubleValue());
    }

}