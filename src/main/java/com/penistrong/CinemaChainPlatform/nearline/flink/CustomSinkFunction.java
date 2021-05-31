package com.penistrong.CinemaChainPlatform.nearline.flink;

import com.penistrong.CinemaChainPlatform.online.mapper.RatingMapper;
import com.penistrong.CinemaChainPlatform.online.redis.RedisUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Component;

@Component
public class CustomSinkFunction extends RichSinkFunction<Rating> {
    private static final Logger logger = LoggerFactory.getLogger(CustomSinkFunction.class);

    @Autowired
    private RatingMapper ratingMapper;

    @Autowired
    private RedisUtils redisUtils;

    //重写open方法，在此方法中建立连接，这样每次invoke时不需要重复建立与释放连接
    @Override
    public void open(Configuration parameters) throws Exception{
        super.open(parameters);
        //TODO:使用RedisUtils连接Redis或者使用Jedis
    }

    @Override
    public void close() throws Exception{
        super.close();
        //TODO:关闭连接
    }

    //数据下沉时，插入每一条数据都会调用一次invoke()方法
    @Override
    public void invoke(Rating value, Context context) throws Exception {
        logger.info("[Flink-QuasiRealTimeFeatureProcess] userId:" + value.userId +" latestMovieId:" + value.latestMovieId);
        //SinkFunction.super.invoke(value, context);
    }

}
