package com.marketplace.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Cache for data read on every page and rarely changed (category tree, best sellers).
 *  - CACHE_TYPE=redis: shared Redis cache (REDIS_URL, default redis://localhost:6379), values stored as JSON
 *    under "shoppi:<cache>::<key>" so several backend instances see the same data and the same evictions;
 *  - otherwise: in-memory Caffeine cache, nothing to install (local development, tests).
 * Evictions wait for the transaction to commit, so a reader never re-caches the old data.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORY_TREE = "categoryTree";
    public static final String TOP_SELLING = "topSelling";

    @Bean
    public CacheManager cacheManager(@Value("${app.cache.type:memory}") String type, ObjectProvider<RedisConnectionFactory> redis) {
        if ("redis".equalsIgnoreCase(type)) {
            ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // class names in the JSON so lists of DTOs come back as DTOs
            om.activateDefaultTyping(om.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
            RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                    .prefixCacheNameWith("shoppi:")
                    .entryTtl(Duration.ofMinutes(2))
                    .disableCachingNullValues()
                    .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(om)));
            log.info("Cache: Redis");
            return RedisCacheManager.builder(redis.getObject())
                    .cacheDefaults(base)
                    .withCacheConfiguration(CATEGORY_TREE, base.entryTtl(Duration.ofMinutes(30)))
                    .withCacheConfiguration(TOP_SELLING, base.entryTtl(Duration.ofMinutes(2)))
                    .transactionAware()
                    .build();
        }
        CaffeineCacheManager memory = new CaffeineCacheManager();
        memory.setCaffeine(Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(2)));
        log.info("Cache: in-memory (set CACHE_TYPE=redis to use Redis)");
        return new TransactionAwareCacheManagerProxy(memory);
    }
}
