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
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.Map;

/**
 * TokenInvokerFilter
 */
@Activate(group = Constants.PROVIDER, value = Constants.TOKEN_KEY)
public class TokenFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation inv)
            throws RpcException {
        // 获得服务提供者配置的 Token 值
        String token = invoker.getUrl().getParameter(Constants.TOKEN_KEY); // invoker是服务暴露时在本地保存的，url里是存在令牌的
        if (ConfigUtils.isNotEmpty(token)) {
            Class<?> serviceType = invoker.getInterface();
            // 获取服务消费者指定的Token值
            // 从隐式参数中，获得 Token 值。
            Map<String, String> attachments = inv.getAttachments();
            String remoteToken = attachments == null ? null : attachments.get(Constants.TOKEN_KEY); // 消费端构造invokercation的时候，会将服务提供者的令牌设置到附件中。
            // 对比，若不一致，抛出 RpcException 异常
            if (!token.equals(remoteToken)) { // 如果消费端跳过注册中心直接访问服务端，那么这个附件里就没有token，所以这里认证时就无法通过
                throw new RpcException("Invalid token! Forbid invoke remote service " + serviceType + " method " + inv.getMethodName() + "() from consumer " + RpcContext.getContext().getRemoteHost() + " to provider " + RpcContext.getContext().getLocalHost());
            }
        }
        return invoker.invoke(inv);
    }

}
