package com.matoe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache configuration with Redis as the primary backing store and
 * an in-memory fallback for environments without Redis (local dev).
 *
 * <p>Cache names:
 * <ul>
 *   <li>{@code itineraries} — saved itinerary lookups (TTL: 30 min)</li>
 *   <li>{@code llm-responses} — LLM call results keyed by prompt hash (TTL: 1 hour)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // Verify Redis is reachable before committing to it
            connectionFactory.getConnection().ping();

            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                .disableCachingNullValues();

            RedisCacheConfiguration llmConfig = defaultConfig.entryTtl(Duration.ofHours(1));

            log.info("Redis reachable — using RedisCacheManager");
            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("llm-responses", llmConfig)
                .build();

        } catch (Exception e) {
            log.warn("Redis not reachable — falling back to in-memory cache. Cause: {}", e.getMessage());
            return new ConcurrentMapCacheManager("itineraries", "llm-responses");
        }
    }
}
