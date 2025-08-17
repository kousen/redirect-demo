package com.example.redirectdemo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

@Configuration
class HttpClientConfig {

    // ===== Defaults =====

    @Bean
    RestTemplate defaultRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @Qualifier("created")
    RestClient defaultRestClientCreated() {
        return RestClient.create(); // does NOT follow redirects
    }

    @Bean
    @Qualifier("built")
    RestClient defaultRestClientBuilt(RestClient.Builder builder) {
        return builder.build(); // follows redirects
    }

    // ===== Explicit configs =====

    @Bean
    RestTemplate followingRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    RestClient neverRedirectingRestClient(RestClient.Builder builder) {
        HttpClient jdk = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return builder.requestFactory(new JdkClientHttpRequestFactory(jdk)).build();
    }

    @Bean
    RestClient alwaysRedirectingRestClient(RestClient.Builder builder) {
        HttpClient jdk = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        return builder.requestFactory(new JdkClientHttpRequestFactory(jdk)).build();
    }
}
