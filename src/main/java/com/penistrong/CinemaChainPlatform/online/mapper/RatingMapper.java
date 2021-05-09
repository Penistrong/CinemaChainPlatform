package com.penistrong.CinemaChainPlatform.online.mapper;

import com.penistrong.CinemaChainPlatform.online.model.Rating;
import org.apache.ibatis.annotations.*;

//使用MyBatis-plus的BaseMapper
@Mapper
public interface RatingMapper{
    @Select("SELECT * FROM ratings WHERE userId=#{userId} AND movieId=#{movieId}")
    Rating getUserRating(Integer userId, Integer movieId);

    @Insert("INSERT INTO ratings(userId, movieId, score, timestamp) VALUES(#{userId}, #{movieId}, #{score}, #{timestamp})")
    Integer insertUserRating(Rating rating);

    @Update("UPDATE ratings SET score=#{score}, timestamp=#{timestamp} WHERE userId=#{userId} AND movieId=#{movieId}")
    Integer updateUserRating(Rating rating);
}
