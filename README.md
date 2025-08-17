# Spring RestClient Redirect Behavior Demo

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Overview

This project demonstrates a surprising inconsistency in Spring Boot's `RestClient` redirect behavior:

- **`RestClient.create()`** → **Does NOT follow redirects** (uses JDK defaults)
- **`RestClient.Builder.build()`** (via DI) → **DOES follow redirects** (uses Spring Boot defaults)

This difference can cause unexpected behavior in production applications, particularly when integrating with APIs that use HTTP redirects (like Google's Veo 3 video generation API).

## Quick Start

### Prerequisites
- Java 21+
- No additional setup required (uses Gradle wrapper)

### Running the Tests

```bash
./gradlew test
```

The tests demonstrate both redirect behaviors:
- ✅ `RestClient.create()` returns 302 status codes
- ✅ `RestClient.Builder.build()` follows redirects and returns 200 with content

### Running the Application

```bash
./gradlew bootRun
```

Test endpoints:
- `GET /hello` → Returns "hello, world"
- `GET /jump` → 302 redirect to `/hello`

## The Problem

Both methods appear to do the same thing:

```java
// RestClient.java
static RestClient create() {
    return new DefaultRestClientBuilder().build();
}

static RestClient.Builder builder() {
    return new DefaultRestClientBuilder();
}
```

However, they behave differently regarding HTTP redirects due to Spring Boot's auto-configuration.

## Root Cause Analysis

### Path 1: `RestClient.create()` - No Redirects
1. Creates `DefaultRestClientBuilder`
2. Uses default `JdkClientHttpRequestFactory()`
3. Calls `HttpClient.newHttpClient()` with JDK defaults
4. **Result**: `HttpClient.Redirect.NEVER`

### Path 2: Injected `RestClient.Builder` - Follows Redirects
1. Spring Boot's `RestClientAutoConfiguration` applies
2. Uses `RestClientBuilderConfigurer` with `ClientHttpRequestFactorySettings`
3. Sets default `Redirects.FOLLOW_WHEN_POSSIBLE`
4. **Result**: Maps to `HttpClient.Redirect.NORMAL`

## Key Files

- **[RestClient-Redirect-Behavior.md](RestClient-Redirect-Behavior.md)** - Complete analysis with source code references
- **[RedirectClientTests.java](src/test/java/com/example/redirectdemo/RedirectClientTests.java)** - Test cases demonstrating both behaviors
- **[HttpClientConfig.java](src/main/java/com/example/redirectdemo/HttpClientConfig.java)** - Configuration showing explicit redirect control

## Real-World Context

This investigation was motivated by integrating with Google's Veo 3 video generation API, which returns redirect URLs for generated videos. The inconsistent redirect behavior caused failures when using `RestClient.create()` instead of dependency injection.

## Recommendations

### Be Explicit About Redirect Behavior

```java
// Explicitly disable redirects
RestClient noRedirects = RestClient.builder()
    .requestFactory(new JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()))
    .build();

// Explicitly enable redirects  
RestClient withRedirects = RestClient.builder()
    .requestFactory(new JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()))
    .build();
```

### Use Dependency Injection Consistently

```java
@Component
public class MyService {
    private final RestClient restClient;
    
    public MyService(RestClient.Builder builder) {
        this.restClient = builder.build(); // Uses Spring Boot defaults
    }
}
```

## Contributing

Found this useful? Consider:
- ⭐ Starring the repository
- 🐛 Reporting issues you encounter
- 💡 Suggesting improvements to Spring Boot's RestClient API
- 📝 Sharing your own experiences with RestClient redirect behavior

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Related Links

- [Spring Boot RestClient Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.rest-client)
- [Java HttpClient Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)
- [Google Veo 3 API Documentation](https://ai.google.dev/gemini-api/docs/video)

---

*This investigation was conducted using Spring Boot 3.5.3 and Java 21. Behavior may vary in different versions.*