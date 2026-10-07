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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.nageoffer.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_LOCAL_CACHE_INVALIDATE_CHANNEL;

/**
 * 短链接本地缓存失效：先清本节点，再通过 Redis Pub/Sub 通知其他节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkCacheInvalidator {

    private final ShortLinkLocalCache shortLinkLocalCache;
    private final StringRedisTemplate stringRedisTemplate;

    public void invalidate(String fullShortUrl) {
        shortLinkLocalCache.invalidate(fullShortUrl);
        try {
            stringRedisTemplate.convertAndSend(SHORT_LINK_LOCAL_CACHE_INVALIDATE_CHANNEL, fullShortUrl);
        } catch (Exception ex) {
            // 广播失败不影响主流程，其他节点的本地缓存最多多存活一个 TTL
            log.warn("短链接本地缓存失效广播失败，fullShortUrl={}", fullShortUrl, ex);
        }
    }
}
