package com.example.dsproject;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class WebClientConfiguration {

    @Bean
    public WebClient webClient(){
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000) // connection timeout // 100초
                .responseTimeout(Duration.ofMillis(100000)) // response timeout // 100초
                .doOnConnected(conn->{
                    conn.addHandlerLast(new ReadTimeoutHandler(100000, TimeUnit.MILLISECONDS)); // read timeout  // 100초
                    conn.addHandlerLast(new WriteTimeoutHandler(100000, TimeUnit.MILLISECONDS)); // write timeout // 100초
                });

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

    }
}
