/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.shortlink.project.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 短链接跳转一级缓存（Caffeine），二级缓存为 Redis
 */
@Component
public class ShortLinkLocalCache {

    private final boolean enabled;
    private final long maxTtlMillis;
    private final Cache<String, CachedLink> cache;

    public ShortLinkLocalCache(
            @Value("${short-link.local-cache.enable:true}") boolean enabled,
            @Value("${short-link.local-cache.maximum-size:10000}") long maximumSize,
            @Value("${short-link.local-cache.ttl-seconds:60}") long ttlSeconds) {
        this.enabled = enabled;
        this.maxTtlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<String, CachedLink>() {
                    @Override
                    public long expireAfterCreate(String key, CachedLink value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedLink value, long currentTime, long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, CachedLink value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 获取缓存的链接信息，未命中或未开启返回 null
     */
    public CachedLink get(String fullShortUrl) {
        if (!enabled) {
            return null;
        }
        return cache.getIfPresent(fullShortUrl);
    }

    /**
     * 写入本地缓存，存活时间取配置上限与链接剩余有效期的较小值
     *
     * @param clickLimit      点击上限，0 表示不限制
     * @param validTimeMillis 链接剩余有效期，毫秒；小于等于 0 表示已过期，不缓存
     */
    public void put(String fullShortUrl, String originUrl, int clickLimit, long validTimeMillis) {
        if (!enabled || originUrl == null || validTimeMillis <= 0) {
            return;
        }
        long ttlMillis = Math.min(maxTtlMillis, validTimeMillis);
        cache.put(fullShortUrl, new CachedLink(originUrl, clickLimit, TimeUnit.MILLISECONDS.toNanos(ttlMillis)));
    }

    public void invalidate(String fullShortUrl) {
        cache.invalidate(fullShortUrl);
    }

    /**
     * 本地缓存的链接信息
     *
     * @param originUrl 原始链接
     * @param clickLimit 点击上限，0 表示不限制
     * @param ttlNanos  本条缓存的存活时间
     */
    public record CachedLink(String originUrl, int clickLimit, long ttlNanos) {
    }
}
