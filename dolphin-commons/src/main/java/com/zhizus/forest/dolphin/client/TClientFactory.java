package com.zhizus.forest.dolphin.client;

/**
 * Created by dempezheng on 2017/8/4.
 */
public interface TClientFactory<T> {

    T iface(Class<T> ifaceClass, String serviceName) throws Exception;
}