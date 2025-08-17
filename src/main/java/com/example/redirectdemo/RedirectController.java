package com.example.redirectdemo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RedirectController {

    @GetMapping("/hello")
    public String hello() {
        return "hello, world";
    }

    @GetMapping("/jump")
    public ResponseEntity<Void> jump() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/hello")
                .build();
    }
}
