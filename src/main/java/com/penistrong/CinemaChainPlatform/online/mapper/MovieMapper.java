package com.penistrong.CinemaChainPlatform.online.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MovieMapper {

    @Select("SELECT COUNT(*) FROM watchlist WHERE userId=#{userId} AND movieId=#{movieId}")
    Integer selectMovieInWatchList(Integer userId, Integer movieId);

    @Insert("INSERT INTO watchlist(userId, movieId) VALUES (#{userId}, #{movieId})")
    Integer insertMovieIntoWatchList(Integer userId, Integer movieId);

    @Delete("DELETE FROM watchlist WHERE userId=#{userId} AND movieId=#{movieId}")
    Integer deleteMovieFromWatchList(Integer userId, Integer movieId);
}
