package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.example.serializer.JodaDateTimeJsonDeserializer;
import org.example.serializer.JodaDateTimeJsonSerializer;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.stereotype.Component;

/**
 * @Author: Richard
 * @Create: 2021/07/13 21:45:00
 * @Description: TODO
 */
@Component
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class RedisConfig {
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory factory){
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(factory);

        // key 的序列化方式
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);
        // value 序列化
        Jackson2JsonRedisSerializer valueSerializer = new Jackson2JsonRedisSerializer(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(DateTime.class,new JodaDateTimeJsonSerializer());
        simpleModule.addDeserializer(DateTime.class,new JodaDateTimeJsonDeserializer());

        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(simpleModule);

        valueSerializer.setObjectMapper(objectMapper);

        redisTemplate.setValueSerializer(valueSerializer);
        return redisTemplate;
    }
}
