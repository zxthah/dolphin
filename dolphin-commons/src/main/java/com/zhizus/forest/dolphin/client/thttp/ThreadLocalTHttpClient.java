package com.zhizus.forest.dolphin.client.thttp;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientFactory;
import com.zhizus.forest.dolphin.annotation.THttpInject;
import com.zhizus.forest.dolphin.exception.DolphinFrameException;
import com.zhizus.forest.dolphin.utils.ThriftClientUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransportException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by dempezheng on 2017/8/17.
 */
public class ThreadLocalTHttpClient {

    private static ThreadLocal<Map<Object, Object>> thriftClientthreadLocal = new ThreadLocal<Map<Object, Object>>();

    public static Object  getOrMakeClient(Field field, THttpInject annotation, Object object) throws NoSuchMethodException, TTransportException, DolphinFrameException, IllegalAccessException, InstantiationException, InvocationTargetException {
        Map<Object, Object> map = thriftClientthreadLocal.get();

        if (map == null) {
            map = Maps.newHashMap();
            thriftClientthreadLocal.set(map);
        }
        Object client = map.get(object);
        if (client == null) {
            client = newClient(field, annotation);
            map.putIfAbsent(object, client);
        }
        return client;
    }


    public static Object newClient(Field field, THttpInject annotation) throws NoSuchMethodException, TTransportException, DolphinFrameException, IllegalAccessException, InvocationTargetException, InstantiationException {
        TBinaryProtocol tBinaryProtocol = makeProtocol(annotation);
        Class[] parameterTypes = {org.apache.thrift.protocol.TProtocol.class};
        Constructor constructor = field.getType().getConstructor(parameterTypes);
        Object client = constructor.newInstance(tBinaryProtocol);
        return client;

    }

    public static Object newProxyClient(Field field, THttpInject annotation) throws NoSuchMethodException, DolphinFrameException, TTransportException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Object client = newClient(field, annotation);
        return getProxyBean(field, annotation, client);
    }

    public static TBinaryProtocol makeProtocol(THttpInject annotation) throws NoSuchMethodException, DolphinFrameException, TTransportException {
        String path = annotation.path();
        String[] serverArr = annotation.serverArr();
        if (serverArr.length < 1) {
            throw new DolphinFrameException();
        }
        List<String> backupServers = Lists.newArrayList();
        for (String s : serverArr) {
            String url = "http://" + s + path;
            backupServers.add(url);
        }
        NFHttpClient defaultClient = NFHttpClientFactory.getDefaultClient();
        String serviceId = annotation.serviceName();
        DelegateLoadBalanceClient delegateLoadBalanceClient = new DelegateLoadBalanceClient(new SpringClientFactory(), defaultClient, serviceId, path, backupServers);
        THttpClient trans = new THttpClient(delegateLoadBalanceClient);
        return new TBinaryProtocol(trans);

    }

    public static Object getProxyBean(Field field, THttpInject annotation, Object bean) {
        ProxyFactoryBean proxyFactory = new ProxyFactoryBean();
        proxyFactory.setTarget(bean);
        proxyFactory.addAdvice(new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                String methodName = invocation.getMethod().getName();
                Set<String> interfaceMethodNames = ThriftClientUtils.getInterfaceMethodNames(field.getType());
                if (interfaceMethodNames.contains(methodName)) {
                    return invocation.proceed();
                }
                Object orMakeClient = getOrMakeClient(field, annotation, bean);
                Object object = invocation.getMethod().invoke(orMakeClient, invocation.getArguments());
                return object;

            }
        });
        return proxyFactory.getObject();
    }
}