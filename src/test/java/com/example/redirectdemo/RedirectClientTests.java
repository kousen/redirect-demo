package com.example.redirectdemo;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedirectClientTests {

    @LocalServerPort
    int port;

    // RestClients

    @Autowired
    @Qualifier("built")
    RestClient defaultRestClientBuilt;

    @Autowired
    @Qualifier("created")
    RestClient defaultRestClientCreated;

    @Autowired
    RestClient neverRedirectingRestClient;

    @Autowired
    RestClient alwaysRedirectingRestClient;

    // RestTemplates

    @Autowired
    RestTemplate defaultRestTemplate;

    @Autowired
    RestTemplate followingRestTemplate;


    private String url() {
        return "http://localhost:%d/jump".formatted(port);
    }

    @Test
    void default_restTemplate_follows_redirects() {
        String body = defaultRestTemplate.getForObject(url(), String.class);
        assertThat(body).isEqualTo("hello, world");
    }

    @Test
    void default_restClient_created_does_not_follow_redirects() {
        ResponseEntity<String> entity =
                defaultRestClientCreated.get()
                        .uri(url())
                        .retrieve()
                        .toEntity(String.class);
        assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(entity.getHeaders().getFirst("Location")).isEqualTo("/hello");
        assertThat(entity.getBody()).isNull();
    }

    @Test
    void default_restClient_built_follows_redirects() {
        ResponseEntity<String> entity =
                defaultRestClientBuilt.get()
                        .uri(url())
                        .retrieve()
                        .toEntity(String.class);
        assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(entity.getHeaders().getFirst("Location")).isNull();
        assertThat(entity.getBody()).isEqualTo("hello, world");
    }

    @Test
    void explicitly_following_restTemplate_follows_redirects() {
        String body = followingRestTemplate.getForObject(url(), String.class);
        assertThat(body).isEqualTo("hello, world");
    }

    @Test
    void explicitly_non_redirecting_restClient_returns_302() {
        ResponseEntity<String> entity =
                neverRedirectingRestClient.get()
                        .uri(url())
                        .retrieve()
                        .toEntity(String.class);
        assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(entity.getHeaders().getFirst("Location")).isEqualTo("/hello");
        assertThat(entity.getBody()).isNull();
    }

    @Test
    void explicitly_redirecting_restClient_follows_redirects() {
        String body =
                alwaysRedirectingRestClient.get()
                        .uri(url())
                        .retrieve()
                        .body(String.class);
        assertThat(body).isEqualTo("hello, world");
    }
}
