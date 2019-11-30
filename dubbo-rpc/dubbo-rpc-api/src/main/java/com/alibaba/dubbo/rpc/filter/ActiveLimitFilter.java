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
package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcStatus;

/**
 * ActiveLimitFilter
 * 实现 Filter 接口，每服务消费者每服务、每方法的最大可并行调用数限制的过滤器实现类
 */
@Activate(group = Constants.CONSUMER, value = Constants.ACTIVES_KEY)
public class ActiveLimitFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        String methodName = invocation.getMethodName();
        // 获得服务提供者每服务每方法最大可并行执行请求数
        int max = invoker.getUrl().getMethodParameter(methodName, Constants.ACTIVES_KEY, 0); // 获取消费端最大并发数
        // 获得 RpcStatus 对象，基于服务 URL + 方法维度
        RpcStatus count = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
        if (max > 0) {
            // 获得超时值
            long timeout = invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.TIMEOUT_KEY, 0); // 获取超时时间
            long start = System.currentTimeMillis();
            long remain = timeout; // 剩余可等待时间
            // 基于 RpcStatus.active 属性，判断当前正在调用中的服务的方法的次数来判断。因为，需要有等待超时的特性，所以不使用 RpcStatus.semaphore 信号量的方式来实现
            int active = count.getActive();
            if (active >= max) { // 当前方法并发数达到最大值
                synchronized (count) {
                    // 循环，等待可并行执行请求数
                    while ((active = count.getActive()) >= max) {
                        try {
                            count.wait(remain); // 等待方法的超时
                        } catch (InterruptedException e) {
                        }
                        // 判断是否没有剩余时长了，抛出 RpcException 异常
                        long elapsed = System.currentTimeMillis() - start; // 本地等待时长
                        remain = timeout - elapsed;
                        if (remain <= 0) { // 如果直到方法超时则抛出异常
                            throw new RpcException("Waiting concurrent invoke timeout in client-side for service:  "
                                    + invoker.getInterface().getName() + ", method: "
                                    + invocation.getMethodName() + ", elapsed: " + elapsed
                                    + ", timeout: " + timeout + ". concurrent invokes: " + active
                                    + ". max concurrent invoke limit: " + max);
                        }
                    }
                }
            }
        }
        try {
            long begin = System.currentTimeMillis();
            // 调用开始的计数
            RpcStatus.beginCount(url, methodName); // 方法执行前消费端并发数加1
            try {
                // 服务调用
                Result result = invoker.invoke(invocation);
                // 调用结束的计数（成功）
                RpcStatus.endCount(url, methodName, System.currentTimeMillis() - begin, true); // 方法执行完消费端并发数减1
                return result;
            } catch (RuntimeException t) {
                // 调用结束的计数（失败）
                RpcStatus.endCount(url, methodName, System.currentTimeMillis() - begin, false); // 方法执行完消费端并发数减1
                throw t;
            }
        } finally {
            if (max > 0) {
                // 唤醒等待的相同服务的相同方法的请求
                synchronized (count) { // 当一个线程执行完该方法，就应该释放当前阻塞在该条件的线程【如果线程1执行时刚好达到最大并发数，则wait等待。当线程2执行完会将最大并发数减1，此时唤醒线程1再去判断则可以执行】
                    count.notify();
                }
            }
        }
    }

}
