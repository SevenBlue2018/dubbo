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

import java.util.concurrent.atomic.AtomicInteger;

class StatItem {
    /**
     * 统计名，目前使用服务名
     */
    private String name;
    /**
     * 最后重置时间
     */
    private long lastResetTime;
    /**
     * 周期
     */
    private long interval;
    /**
     * 当前周期，剩余种子数
     */
    private AtomicInteger token;
    /**
     * 限制大小
     */
    private int rate;

    StatItem(String name, int rate, long interval) {
        this.name = name;
        this.rate = rate;
        this.interval = interval;
        this.lastResetTime = System.currentTimeMillis();
        this.token = new AtomicInteger(rate);
    }

    public boolean isAllowable() {
        long now = System.currentTimeMillis();
        if (now > lastResetTime + interval) { // 超过了刷新时间则重新分配令牌
            token.set(rate); // 个数为指定rate值
            lastResetTime = now;
        }

        int value = token.get();
        boolean flag = false;
        while (value > 0 && !flag) { // 还存在令牌则进行获取
            flag = token.compareAndSet(value, value - 1); // CAS方法减一成功则说明申请到令牌
            value = token.get();
        }

        return flag;
    }

    long getLastResetTime() {
        return lastResetTime;
    }

    int getToken() {
        return token.get();
    }

    @Override
    public String toString() {
        return new StringBuilder(32).append("StatItem ")
                .append("[name=").append(name).append(", ")
                .append("rate = ").append(rate).append(", ")
                .append("interval = ").append(interval).append("]")
                .toString();
    }

}
