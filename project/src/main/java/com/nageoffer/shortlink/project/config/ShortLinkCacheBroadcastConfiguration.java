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

package com.nageoffer.shortlink.project.config;

import com.nageoffer.shortlink.project.cache.ShortLinkLocalCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

import static com.nageoffer.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_LOCAL_CACHE_INVALIDATE_CHANNEL;

/**
 * 短链接本地缓存失效广播订阅配置
 */
@Configuration
public class ShortLinkCacheBroadcastConfiguration {

    @Bean
    public RedisMessageListenerContainer shortLinkCacheInvalidateListenerContainer(
            RedisConnectionFactory redisConnectionFactory, ShortLinkLocalCache shortLinkLocalCache) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                (message, pattern) -> shortLinkLocalCache.invalidate(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(SHORT_LINK_LOCAL_CACHE_INVALIDATE_CHANNEL));
        return container;
    }
}
