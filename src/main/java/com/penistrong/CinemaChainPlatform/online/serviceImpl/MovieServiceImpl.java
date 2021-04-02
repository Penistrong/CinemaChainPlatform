package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.mapper.MovieMapper;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class MovieServiceImpl implements MovieService {

    @Autowired
    private MovieMapper movieMapper;

    //从DataManager获得基础电影信息
    @Override
    public HashMap<String, List<Movie>> getMoviesFromDataManager(Integer pageIndex, Integer pageSize, Integer listSize) {
        //TODO:改写为PageInfo形式，可翻页
        return DataManager.getInstance().getMoviesByGenreNumbers(pageSize, listSize);
    }

    //根据电影id获取电影信息
    @Override
    public Movie getMovieFromDataManager(int movieId) {
        return DataManager.getInstance().getMovieById(movieId);
    }
}
