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
package com.alibaba.dubbo.rpc.protocol.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.io.Bytes;
import com.alibaba.dubbo.common.io.UnsafeByteArrayInputStream;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.serialize.ObjectInput;
import com.alibaba.dubbo.common.serialize.ObjectOutput;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.Codec2;
import com.alibaba.dubbo.remoting.exchange.Request;
import com.alibaba.dubbo.remoting.exchange.Response;
import com.alibaba.dubbo.remoting.exchange.codec.ExchangeCodec;
import com.alibaba.dubbo.remoting.transport.CodecSupport;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;

import java.io.IOException;
import java.io.InputStream;

import static com.alibaba.dubbo.rpc.protocol.dubbo.CallbackServiceCodec.encodeInvocationArgument;

/**
 * Dubbo codec.
 * 实现 Codec2 接口，继承 ExchangeCodec 类，Dubbo 编解码器实现类。
 */
public class DubboCodec extends ExchangeCodec implements Codec2 {

    public static final String NAME = "dubbo";
    public static final String DUBBO_VERSION = Version.getProtocolVersion();
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    public static final byte RESPONSE_VALUE = 1;
    public static final byte RESPONSE_NULL_VALUE = 2;
    public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Logger log = LoggerFactory.getLogger(DubboCodec.class);

    /**
     * 解码消息体
     * @param channel
     * @param is
     * @param header
     * @return
     * @throws IOException
     */
    @Override
    protected Object  decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        // get request id.
        long id = Bytes.bytes2long(header, 4);
        if ((flag & FLAG_REQUEST) == 0) { // flag为第3字节(第16位到第23位)，& 0x80 表示请求响应的标记，结果是0则表示是响应数据
            // decode response.
            Response res = new Response(id);
            // 若是心跳事件，进行设置
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(Response.HEARTBEAT_EVENT);
            }
            // get status.
            // 设置状态
            byte status = header[3];
            res.setStatus(status);
            try {
                ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                // 正常响应状态
                if (status == Response.OK) {
                    Object data;
                    // 解码心跳事件
                    if (res.isHeartbeat()) {
                        data = decodeHeartbeatData(channel, in);
                    } else if (res.isEvent()) { // 解码其它事件
                        data = decodeEventData(channel, in);
                    } else { // 解码普通响应
                        DecodeableRpcResult result;
                        // 在通信框架（例如，Netty）的 IO 线程，解码
                        if (channel.getUrl().getParameter(
                                Constants.DECODE_IN_IO_THREAD_KEY,
                                Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            result.decode();
                        } else { // 在 Dubbo ThreadPool 线程，解码，使用 DecodeHandler
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    // 设置结果
                    res.setResult(data);
                } else { // 异常响应状态
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode response failed: " + t.getMessage(), t);
                }
                res.setStatus(Response.CLIENT_ERROR);
                res.setErrorMessage(StringUtils.toString(t));
            }
            return res;
        } else { // flag为第3字节(第16位到第23位)，& 0x80 表示请求响应的标记，结果是1则表示是请求数据
            // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getProtocolVersion());
            // 是否需要响应
            req.setTwoWay((flag & FLAG_TWOWAY) != 0); // flag为第3字节(第16位到第23位)，& 0x40 表示需要往返标记
            // 若是心跳事件，进行设置
            if ((flag & FLAG_EVENT) != 0) { // flag为第3字节(第16位到第23位)，& 0x20 表示事件标记
                req.setEvent(Request.HEARTBEAT_EVENT);
            }
            try {
                Object data;
                ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                if (req.isHeartbeat()) { // 解码心跳事件
                    data = decodeHeartbeatData(channel, in);
                } else if (req.isEvent()) { // 解码其它事件
                    data = decodeEventData(channel, in);
                } else { // 解码普通请求
                    // 在通信框架（例如，Netty）的 IO 线程，解码
                    DecodeableRpcInvocation inv;
                    if (channel.getUrl().getParameter(
                            Constants.DECODE_IN_IO_THREAD_KEY,
                            Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto); // 在I/O线程中直接解码
                        inv.decode();
                    } else { // 在 Dubbo ThreadPool 线程，解码，使用 DecodeHandler
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto); // 交给dubbo业务线程池解码
                    }
                    data = inv;
                }
                req.setData(data); // 将RpcInvocation作为Request的数据域
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true); // 编码失败，先做标记并存储异常·
                req.setData(t);
            }
            return req;
        }
    }

    private byte[] readMessageData(InputStream is) throws IOException {
        if (is.available() > 0) {
            byte[] result = new byte[is.available()];
            is.read(result);
            return result;
        }
        return new byte[]{};
    }

    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeRequestData(channel, out, data, DUBBO_VERSION);
    }

    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeResponseData(channel, out, data, DUBBO_VERSION);
    }

    /**
     * 编码请求数据
     * @param channel
     * @param out
     * @param data
     * @param version
     * @throws IOException
     */
    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;

        out.writeUTF(version); // 写入框架版本号
        out.writeUTF(inv.getAttachment(Constants.PATH_KEY)); // 写入调用接口
        out.writeUTF(inv.getAttachment(Constants.VERSION_KEY)); // 写入接口指定的版本，默认为0.0.0.0

        out.writeUTF(inv.getMethodName()); // 写入方法名称
        out.writeUTF(ReflectUtils.getDesc(inv.getParameterTypes())); // 写入方法参数类型
        Object[] args = inv.getArguments();
        if (args != null)
            for (int i = 0; i < args.length; i++) {
                out.writeObject(encodeInvocationArgument(channel, inv, i)); // 写入方法参数值
            }
        out.writeObject(inv.getAttachments()); // 写入隐式参数
    }

    /**
     * 编码响应数据
     * @param channel
     * @param out
     * @param data
     * @param version
     * @throws IOException
     */
    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        Result result = (Result) data;
        // currently, the version value in Response records the version of Request
        boolean attach = Version.isSupportResponseAttatchment(version); // 判断客户端请求的版本是否支持服务端参数返回
        Throwable th = result.getException();
        if (th == null) {
            Object ret = result.getValue(); // 提取正常返回结果
            if (ret == null) {
                out.writeByte(attach ? RESPONSE_NULL_VALUE_WITH_ATTACHMENTS : RESPONSE_NULL_VALUE); // 在编码前，先写一个字节标记
            } else {
                out.writeByte(attach ? RESPONSE_VALUE_WITH_ATTACHMENTS : RESPONSE_VALUE); // 在编码前，先写一个字节标记
                out.writeObject(ret); // 写入调用结果
            }
        } else {
            out.writeByte(attach ? RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS : RESPONSE_WITH_EXCEPTION); // 在编码前，先写一个字节标记
            out.writeObject(th); // 写入异常
        }

        if (attach) {
            // returns current version of Response to consumer side.
            result.getAttachments().put(Constants.DUBBO_VERSION_KEY, Version.getProtocolVersion()); // 记录服务端Dubbo版本
            out.writeObject(result.getAttachments()); // 写入服务端隐式参数
        }
    }
}
