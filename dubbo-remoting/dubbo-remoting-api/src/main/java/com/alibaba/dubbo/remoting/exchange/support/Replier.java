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
package com.alibaba.dubbo.remoting.exchange.support;

import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.exchange.ExchangeChannel;

/**
 * Replier. (API, Prototype, ThreadSafe)
 * Request 对应统一的 ExchangeHandler 实现的对象。
 * 但是在一些场景下，我们希望实现，不同的数据类型，对应不同的处理器。Replier 就是来处理这种情况的。一个数据类型，对应一个 Replier 对象
 */
public interface Replier<T> {

    /**
     * reply.
     *
     * @param channel
     * @param request
     * @return response
     * @throws RemotingException
     */
    Object reply(ExchangeChannel channel, T request) throws RemotingException;

}