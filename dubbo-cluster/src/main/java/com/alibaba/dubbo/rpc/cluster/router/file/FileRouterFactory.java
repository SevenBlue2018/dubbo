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
package com.alibaba.dubbo.rpc.cluster.router.file;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.IOUtils;
import com.alibaba.dubbo.rpc.cluster.Router;
import com.alibaba.dubbo.rpc.cluster.RouterFactory;
import com.alibaba.dubbo.rpc.cluster.router.script.ScriptRouterFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileRouterFactory implements RouterFactory {

    public static final String NAME = "file";

    private RouterFactory routerFactory;

    public void setRouterFactory(RouterFactory routerFactory) {
        this.routerFactory = routerFactory;
    }

    @Override
    public Router getRouter(URL url) {
        try {
            // Transform File URL into Script Route URL, and Load
            // file:///d:/path/to/route.js?router=script ==> script:///d:/path/to/route.js?type=js&rule=<file-content>
            String protocol = url.getParameter(Constants.ROUTER_KEY, ScriptRouterFactory.NAME); // Replace original protocol (maybe 'file') with 'script'
            String type = null; // Use file suffix to config script type, e.g., js, groovy ...
            String path = url.getPath(); // 解析文件后缀名，后续用于匹配的是什么类型的脚本，如js、groovy等
            if (path != null) {
                int i = path.lastIndexOf('.');
                if (i > 0) {
                    type = path.substring(i + 1);
                }
            }
            String rule = IOUtils.read(new FileReader(new File(url.getAbsolutePath()))); // 读取文件

            boolean runtime = url.getParameter(Constants.RUNTIME_KEY, false); // 读取是否是运行时参数
            // 生成路由工厂可以识别的URL，并把参数设置进去
            URL script = url.setProtocol(protocol).addParameter(Constants.TYPE_KEY, type).addParameter(Constants.RUNTIME_KEY, runtime).addParameterAndEncoded(Constants.RULE_KEY, rule);

            return routerFactory.getRouter(script); // 再次调用路由工厂，因为前面将file转化为script，这里会使用脚本路由解析(文件路由最终使用的还是脚本路由)
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}
