package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.mapper.CollectionMapper;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.service.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollectionServiceImpl implements CollectionService {

    @Autowired
    private CollectionMapper collectionMapper;
    
    @Override
    public PageInfo<Movie> getMoviesByGenre(String genre, Integer pageIndex, Integer pageSize) {
        PageHelper.startPage(pageIndex, pageSize);
        List<Movie> movieListTotal = DataManager.getInstance().getMoviesByGenre(genre);
        PageInfo<Movie> movieList = new PageInfo<Movie>(movieListTotal);

        return movieList;
    }
}
