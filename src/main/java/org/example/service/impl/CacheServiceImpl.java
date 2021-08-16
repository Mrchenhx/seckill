package org.example.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.example.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Richard
 * @Create: 2021/07/15 21:19:00
 * @Description: TODO
 */
@Service
public class CacheServiceImpl implements CacheService {

    private Cache<String, Object> commentCache = null;

    @PostConstruct
    public void init(){
        commentCache = CacheBuilder.newBuilder()
                // 设置初始容量值
                .initialCapacity(10)
                // 设置缓存中最大可以存储100个KEY,超过100个之后会按照LRU的策略移除缓存项
                .maximumSize(100)
                // 设置写缓存后多少秒过期
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void setCommentCache(String key, Object value) {
        commentCache.put(key, value);
    }

    @Override
    public Object getCommentCache(String key) {
        return commentCache.getIfPresent(key);
    }
}
