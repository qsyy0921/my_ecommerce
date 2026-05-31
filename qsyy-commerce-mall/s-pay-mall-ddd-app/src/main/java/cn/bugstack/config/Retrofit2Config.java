package cn.bugstack.config;

import cn.bugstack.infrastructure.gateway.IGroupBuyMarketService;
import cn.bugstack.infrastructure.gateway.IWeixinApiService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.MDC;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Configuration
public class Retrofit2Config {

    @Value("${app.config.group-buy-market.api-url}")
    private String groupBuyMarketApiUrl;
    @Value("${app.config.group-buy-market.app-id:s-pay-mall}")
    private String groupBuyMarketAppId;
    @Value("${app.config.group-buy-market.sign-secret:group-buy-market-dev-secret}")
    private String groupBuyMarketSignSecret;

    @Bean
    public IWeixinApiService weixinApiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.weixin.qq.com/")
                .addConverterFactory(JacksonConverterFactory.create()).build();

        return retrofit.create(IWeixinApiService.class);
    }

    @Bean
    public IGroupBuyMarketService groupBuyMarketService() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String nonce = UUID.randomUUID().toString().replace("-", "");
                    String path = request.url().encodedPath();
                    String sign = sign(request.method(), path, timestamp, nonce);

                    Request signedRequest = request.newBuilder()
                            .addHeader("x-gbm-app-id", groupBuyMarketAppId)
                            .addHeader("x-gbm-timestamp", timestamp)
                            .addHeader("x-gbm-nonce", nonce)
                            .addHeader("x-gbm-signature", sign)
                            .addHeader("trace-id", null == MDC.get("trace-id") ? "" : MDC.get("trace-id"))
                            .build();
                    return chain.proceed(signedRequest);
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(groupBuyMarketApiUrl)
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create()).build();

        return retrofit.create(IGroupBuyMarketService.class);
    }

    private String sign(String method, String path, String timestamp, String nonce) {
        try {
            String payload = method + "\n" + path + "\n" + timestamp + "\n" + nonce;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(groupBuyMarketSignSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("build group-buy-market signature failed", e);
        }
    }

}
