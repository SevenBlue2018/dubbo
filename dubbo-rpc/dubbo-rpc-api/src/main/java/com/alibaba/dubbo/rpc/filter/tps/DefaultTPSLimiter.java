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
package com.alibaba.dubbo.rpc.filter.tps;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 实现 TPSLimiter 接口，默认 TPS 限制器实现类，以服务为维度
 */
public class DefaultTPSLimiter implements TPSLimiter {

    /**
     * StatItem 集合
     * key为服务名
     */
    private final ConcurrentMap<String, StatItem> stats
            = new ConcurrentHashMap<String, StatItem>(); // key为serverKey，value为StatItem

    @Override
    public boolean isAllowable(URL url, Invocation invocation) {
        // 获得 TPS 大小配置项
        int rate = url.getParameter(Constants.TPS_LIMIT_RATE_KEY, -1); // 获取令牌数
        // 获得 TPS 周期配置项，默认 60 秒
        long interval = url.getParameter(Constants.TPS_LIMIT_INTERVAL_KEY,
                Constants.DEFAULT_TPS_LIMIT_INTERVAL); // 获取令牌刷新时间间隔
        String serviceKey = url.getServiceKey();
        if (rate > 0) {
            StatItem statItem = stats.get(serviceKey);
            if (statItem == null) {
                stats.putIfAbsent(serviceKey,
                        new StatItem(serviceKey, rate, interval));
                statItem = stats.get(serviceKey);
            }
            return statItem.isAllowable(); // 获取令牌
        } else { // 不进行限流了，则移除StatItem（默认不限流）
            StatItem statItem = stats.get(serviceKey);
            if (statItem != null) {
                stats.remove(serviceKey);
            }
        }

        return true;
    }

}
