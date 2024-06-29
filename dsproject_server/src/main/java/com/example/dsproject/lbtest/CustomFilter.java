package com.example.dsproject.lbtest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CustomFilter extends AbstractGatewayFilterFactory<CustomFilter.Config> {

    public CustomFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            log.info("Custom Pre Filter : request id -> {}", exchange.getRequest().getId());
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                log.info("Custom Post Filter : response code -> {}", exchange.getResponse().getStatusCode());
            }));
        };
    }

    public static class Config {
        // Put the configuration properties
    }
}
