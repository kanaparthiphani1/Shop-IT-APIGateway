package com.example.apigateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Configuration
public class RouteConfigs {

    @Bean
    public RouteLocator eazyBankRouteConfig(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route(p -> p
                        .path("/productservice/**")
                        .filters( f -> f.rewritePath("/productservice/(?<segment>.*)","/${segment}")
                                .circuitBreaker(config -> config.setName("product-service-cb").setFallbackUri("forward:/contactSupport"))
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))

                        .uri("lb://PRODUCTSERVICE"))
                .route(p -> p
                        .path("/userservice/**")
                        .filters( f -> f.rewritePath("/userservice/(?<segment>.*)","/${segment}")
                                .retry(retryConfig -> retryConfig.setRetries(3).setMethods(HttpMethod.GET).setBackoff(Duration.ofMillis(100),Duration.ofMillis(1000),2,true))
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))
                        .uri("lb://USERSERVICE"))
                .route(p -> p
                        .path("/orderservice/**")
                        .filters( f -> f.rewritePath("/orderservice/(?<segment>.*)","/${segment}")
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                .requestRateLimiter(config -> config.setRateLimiter(redisRateLimiter()).setKeyResolver(userKeyResolver())))
                        .uri("lb://ORDERSERVICE"))
                .route(p -> p
                        .path("/cartservice/**")
                        .filters( f -> f.rewritePath("/cartservice/(?<segment>.*)","/${segment}")
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))
                        .uri("lb://CARTSERVICE"))
                .build();


    }

    @Bean
    public RedisRateLimiter redisRateLimiter(){
        return new RedisRateLimiter(1,1,1);
    }

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("Authorization"))
                .defaultIfEmpty("anonymous");
    }
}
