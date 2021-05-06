package com.penistrong.CinemaChainPlatform.online.mapper;

import com.penistrong.CinemaChainPlatform.online.model.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用于OAuth2实现登录验证用户信息，用户携带Token访问平台
 */
@Mapper
public interface UserMapper {
    @Select("SELECT COUNT(*) FROM users WHERE userId=#{userId}")
    Integer isUserExist(Integer userId);

    @Select("SELECT COUNT(*) FROM movies WHERE movieId=#{movieId}")
    Integer isMovieExist(Integer movieId);

    @Select("SELECT password FROM users WHERE username=#{username}")
    String queryUserPassword(String username);

    @Select("SELECT * FROM users WHERE username=#{username} AND password=#{password}")
    User selectUser(String username, String password);

    //user_id列已设置自增
    @Insert("INSERT INTO users(username, password, enabled) VALUES(#{username}, #{password}, 1)")
    Integer createUser(String username, String password);

    //user_id与username都是外键，参照表users中的两字段
    @Insert("INSERT INTO authorities(userId, username, authority) VALUES(#{userId}, #{username}, 'ROLE_user')")
    Integer createAuthority(Integer userId, String username);

    @Insert("INSERT INTO watchlist(userId, movieId) VALUES(#{userId}, #{movieId})")
    Integer insertWatchList(Integer userId, Integer movieId);

    @Delete("DELETE FROM watchlist WHERE userId=#{userId} AND movieId=#{movieId}")
    Integer deleteWatchList(Integer userId, Integer movieId);

    @Delete("DELETE FROM watchlist WHERE userId=#{userId}")
    Integer removeWatchList(Integer userId);
}
