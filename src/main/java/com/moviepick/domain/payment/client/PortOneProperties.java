package com.moviepick.domain.payment.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 portone.* 설정. 값은 환경변수 또는 application-local.yml로 주입한다.
 * 비밀값이 로그에 찍히지 않도록 toString에서 가린다.
 */
@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String baseUrl,
        String apiSecret,
        String storeId,
        String channelKey,
        String webhookSecret
) {

    @Override
    public String toString() {
        return "PortOneProperties[baseUrl=" + baseUrl + ", storeId=" + storeId
                + ", channelKey=" + channelKey + ", apiSecret=***, webhookSecret=***]";
    }
}
