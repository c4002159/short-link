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

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.nageoffer.shortlink.project.common.constant.RedisKeyConstant.CLICK_COUNT_SHORT_LINK_KEY;
import static com.nageoffer.shortlink.project.common.constant.RedisKeyConstant.CLICK_LIMIT_SHORT_LINK_KEY;

/**
 * 短链接点击上限：Redis 原子计数，计数器首次以数据库中的历史 PV 作为初值
 */
@Component
@RequiredArgsConstructor
public class ShortLinkClickLimiter {

    /**
     * 先判断再自增，计数器只统计放行的点击：计数器不存在返回 -1（需要回源重新初始化），
     * 已达上限返回 0，放行并自增返回 1
     */
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('GET', KEYS[1]) "
                    + "if not c then return -1 end "
                    + "if tonumber(c) >= tonumber(ARGV[1]) then return 0 end "
                    + "redis.call('INCR', KEYS[1]) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 写入点击上限，并在计数器不存在时用已点击次数初始化
     *
     * @param clickLimit  点击上限，0 表示不限制
     * @param usedClicks  已点击次数，仅在计数器不存在时作为初值
     * @param ttlMillis   与跳转缓存保持一致的存活时间
     */
    public void warmUp(String fullShortUrl, int clickLimit, int usedClicks, long ttlMillis) {
        stringRedisTemplate.opsForValue().set(
                String.format(CLICK_LIMIT_SHORT_LINK_KEY, fullShortUrl), String.valueOf(clickLimit),
                ttlMillis, TimeUnit.MILLISECONDS);
        if (clickLimit > 0) {
            stringRedisTemplate.opsForValue().setIfAbsent(
                    String.format(CLICK_COUNT_SHORT_LINK_KEY, fullShortUrl), String.valueOf(usedClicks),
                    ttlMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 删除上限缓存，下次访问会回源数据库重新写入；计数器保留，避免已有点击被清零
     */
    public void clearLimit(String fullShortUrl) {
        stringRedisTemplate.delete(String.format(CLICK_LIMIT_SHORT_LINK_KEY, fullShortUrl));
    }

    /**
     * 解析 Redis 中缓存的点击上限，缓存不存在返回 null
     */
    public static Integer parseLimit(String cachedLimit) {
        return StrUtil.isBlank(cachedLimit) ? null : Integer.valueOf(cachedLimit);
    }

    /**
     * 尝试占用一次点击
     *
     * @return TRUE 允许跳转；FALSE 已达上限；null 计数器丢失，需回源重新初始化
     */
    public Boolean tryAcquire(String fullShortUrl, int clickLimit) {
        if (clickLimit <= 0) {
            return Boolean.TRUE;
        }
        Long result = stringRedisTemplate.execute(ACQUIRE_SCRIPT,
                List.of(String.format(CLICK_COUNT_SHORT_LINK_KEY, fullShortUrl)), String.valueOf(clickLimit));
        if (result == null || result == -1L) {
            return null;
        }
        return result == 1L;
    }
}
