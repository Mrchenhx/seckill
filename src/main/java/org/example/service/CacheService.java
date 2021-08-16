package org.example.service;

/**
 * @Author: Richard
 * @Create: 2021/07/15 21:17:00
 * @Description: TODO
 */
// 封装本地缓存操作类
public interface CacheService {
    // 存方法
    void setCommentCache(String key, Object value);
    // 取方法
    Object getCommentCache(String key);
}
