# The Surprising Redirect Behavior of Spring's RestClient

## TL;DR

Spring Boot's `RestClient` has inconsistent redirect behavior depending on how you create it:

- `RestClient.create()` → **Does NOT follow redirects**
- Injected `RestClient.Builder.build()` → **DOES follow redirects**

This subtle difference can cause unexpected behavior in production applications.

## The Discovery

While testing Spring Boot's REST clients, I discovered that `RestClient` behaves differently regarding HTTP redirects depending on how you instantiate it. This behavior is not documented and can lead to confusing bugs.

Here's a simple test that demonstrates the problem:

```java
@Test
void restClient_redirect_behavior_differs() {
    // This does NOT follow redirects
    RestClient created = RestClient.create();
    
    // This DOES follow redirects  
    RestClient built = restClientBuilder.build(); // injected builder
    
    // Same endpoint, different behaviors!
}
```

## The Root Cause Investigation

Both methods appear to do the same thing at first glance:

```java
// RestClient.java
static RestClient create() {
    return new DefaultRestClientBuilder().build();
}

static RestClient.Builder builder() {
    return new DefaultRestClientBuilder();
}
```

However, the difference lies in Spring Boot's auto-configuration system.

### Path 1: `RestClient.create()` - No Redirects

When you call `RestClient.create()`:

1. Creates a `DefaultRestClientBuilder`
2. Uses default `JdkClientHttpRequestFactory()`
3. Calls `HttpClient.newHttpClient()` 
4. **JDK default**: `HttpClient.Redirect.NEVER`

```java
// JdkClientHttpRequestFactory.java
public JdkClientHttpRequestFactory() {
    this(HttpClient.newHttpClient()); // Uses JDK defaults
}
```

### Path 2: Injected `RestClient.Builder` - Follows Redirects

When you inject a `RestClient.Builder`:

1. Spring Boot's `RestClientAutoConfiguration` kicks in
2. Uses `RestClientBuilderConfigurer` 
3. Applies `ClientHttpRequestFactorySettings.defaults()`
4. **Spring Boot default**: `Redirects.FOLLOW_WHEN_POSSIBLE`

```java
// RestClientAutoConfiguration.java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
    return configurer.configure(RestClient.builder());
}
```

```java
// ClientHttpRequestFactorySettings.java  
public ClientHttpRequestFactorySettings {
    redirects = (redirects != null) ? redirects : Redirects.FOLLOW_WHEN_POSSIBLE;
}
```

The Spring Boot setting `FOLLOW_WHEN_POSSIBLE` maps to `HttpClient.Redirect.NORMAL`:

```java
// JdkHttpClientBuilder.java
private Redirect asHttpClientRedirect(HttpRedirects redirects) {
    return switch (redirects) {
        case FOLLOW_WHEN_POSSIBLE, FOLLOW -> Redirect.NORMAL;
        case DONT_FOLLOW -> Redirect.NEVER;
    };
}
```

## Demonstrating the Behavior

Here's a complete test suite that proves this behavior:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedirectClientTests {

    @Autowired
    @Qualifier("created")
    RestClient defaultRestClientCreated;

    @Autowired 
    @Qualifier("built")
    RestClient defaultRestClientBuilt;

    @Test
    void default_restClient_created_does_not_follow_redirects() {
        ResponseEntity<String> entity = defaultRestClientCreated
            .get().uri(url("/jump")).retrieve().toEntity(String.class);
            
        assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(entity.getHeaders().getFirst("Location")).isEqualTo("/hello");
        assertThat(entity.getBody()).isNull();
    }

    @Test
    void default_restClient_built_follows_redirects() {
        ResponseEntity<String> entity = defaultRestClientBuilt
            .get().uri(url("/jump")).retrieve().toEntity(String.class);
            
        assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(entity.getHeaders().getFirst("Location")).isNull();
        assertThat(entity.getBody()).isEqualTo("hello, world");
    }
}
```

Configuration:

```java
@Configuration
class HttpClientConfig {

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
}
```

## Implications for Developers

This inconsistency can cause subtle bugs:

1. **Local development**: You might use `RestClient.create()` in a quick test
2. **Production**: Your application uses dependency injection
3. **Bug**: Different redirect behavior between environments

## Recommendations

### Be Explicit About Redirect Behavior

Always be explicit about your redirect requirements:

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

If you're using Spring Boot, prefer dependency injection for consistent behavior:

```java
@Component
public class MyService {
    private final RestClient restClient;
    
    public MyService(RestClient.Builder builder) {
        this.restClient = builder.build(); // Uses Spring Boot defaults
    }
}
```

## References

- **Spring Boot Source**: `RestClientAutoConfiguration.java`
- **Default Settings**: `ClientHttpRequestFactorySettings.java` 
- **HTTP Client Mapping**: `JdkHttpClientBuilder.java`
- **RestClient Implementation**: `DefaultRestClientBuilder.java`
- **JDK HTTP Client**: `HttpClient.newHttpClient()` documentation

## Conclusion

This subtle difference in redirect behavior highlights the importance of understanding how framework auto-configuration affects your applications. While Spring Boot's defaults are generally sensible (following redirects is usually what you want), the inconsistency between static factory methods and dependency injection can be surprising.

When in doubt, be explicit about your HTTP client configuration rather than relying on implicit defaults.

---

*This investigation was conducted using Spring Boot 3.5.3 and Java 21. The behavior may vary in different versions.*