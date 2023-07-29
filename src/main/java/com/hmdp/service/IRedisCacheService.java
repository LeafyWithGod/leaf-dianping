package com.hmdp.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface IRedisCacheService {
    void set(String key, Object value, Long time, TimeUnit unit);
    void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit);
    <R,ID> R getObject_null(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit);
    <R,ID> R getObject_Mutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit);
    <R,ID> R getObject_Expire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit);
}
