package com.penistrong.CinemaChainPlatform.online.redis;

import redis.clients.jedis.Jedis;

/**
 * 提供给DataManager等先于SpringBoot启动的数据加载类使用
 */
public class JedisClient {
    //singleton Jedis
    private static volatile Jedis redisClient;
    final static String REDIS_END_POINT = "66.42.66.135";
    final static int REDIS_PORT = 6379;
    final static String REDIS_PASSWORD = "chenliwei";

    private JedisClient(){
        redisClient = new Jedis(REDIS_END_POINT, REDIS_PORT);
        redisClient.auth(REDIS_PASSWORD);
    }

    public static Jedis getInstance(){
        if (null == redisClient){
            synchronized (JedisClient.class){
                if (null == redisClient){
                    redisClient = new Jedis(REDIS_END_POINT, REDIS_PORT);
                    redisClient.auth(REDIS_PASSWORD);
                }
            }
        }
        return redisClient;
    }
}
