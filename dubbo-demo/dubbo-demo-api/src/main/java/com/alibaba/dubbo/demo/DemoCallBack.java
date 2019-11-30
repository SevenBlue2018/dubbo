package com.alibaba.dubbo.demo;

/**
 * @Author: qichenchen
 * @Date: 2019/11/22 2:48 下午
 * @Description
 */
public class DemoCallBack {

    public void oninvoke(String msg){
        System.out.println("oninvoke:" + msg);
    }
    public void onreturn(String msg) {
        System.out.println("onreturn:" + msg);
    }
    public void onthrow(Throwable e) {
        System.out.println("onthrow:" + e);
    }
}
