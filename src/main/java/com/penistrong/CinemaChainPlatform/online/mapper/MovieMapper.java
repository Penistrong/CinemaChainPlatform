package com.penistrong.CinemaChainPlatform.online.mapper;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MovieMapper {

    @Select("SELECT COUNT(*) FROM watchlist WHERE userId=#{userId} AND movieId=#{movieId}")
    Integer selectMovieInWatchList(Integer userId, Integer movieId);

    @Insert("INSERT INTO watchlist(userId, movieId) VALUES (#{userId}, #{movieId})")
    Integer insertMovieIntoWatchList(Integer userId, Integer movieId);

    @Delete("DELETE FROM watchlist WHERE userId=#{userId} AND movieId=#{movieId}")
    Integer deleteMovieFromWatchList(Integer userId, Integer movieId);

    //等值连接查询
    @Select("SELECT t1.* FROM movies t1, watchlist t2 " +
            "WHERE t1.movieId=t2.movieId AND t2.userId=#{userId} " +
            "ORDER BY t2.timestamp DESC " +
            "LIMIT #{size}")
    List<Movie> getWatchListByUserId(Integer userId, Integer size);

    @Delete("DELETE FROM watchlist WHERE userId=#{userId}")
    Integer removeAllFromWatchList(Integer userId);
}
