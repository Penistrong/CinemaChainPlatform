package com.penistrong.CinemaChainPlatform.online.redis;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RedisConfig {
    /**
     * 自定义序列化，使用FastJson进行key/value序列化
     * @author Penistrong
     * @param redisConnectionFactory: Redis连接工厂类
     * @return RedisTemplate<String, Object>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory){
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        //使用FastJson序列化
        final FastJsonRedisSerializer<?> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);
        //value采用FastJsonRedisSerializer
        template.setValueSerializer(fastJsonRedisSerializer);
        //TODO:不知为何使用fastJson作为序列化器时，反序列化hashMap里的字符串报syntax error，这里替换为StringRedisSerializer试一试
        template.setHashValueSerializer(new StringRedisSerializer());
        //key采用StringRedisSerializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        //开启事务
        template.setEnableTransactionSupport(true);
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }
}
