package com.payflow.gatewayclient;

import com.payflow.config.PayflowProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GatewayClientConfig {

    @Bean
    RestClient gatewayRestClient(PayflowProperties properties) {
        Duration connect = properties.getGateway().getConnectTimeout();
        Duration read = properties.getGateway().getReadTimeout();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder()
                .baseUrl(properties.getGateway().getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
