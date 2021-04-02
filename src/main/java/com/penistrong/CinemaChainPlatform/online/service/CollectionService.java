package com.penistrong.CinemaChainPlatform.online.service;

import com.github.pagehelper.PageInfo;
import com.penistrong.CinemaChainPlatform.online.model.Movie;

import java.util.List;

public interface CollectionService {

    PageInfo<Movie> getMoviesByGenre(String genre, Integer pageIndex, Integer pageSize);
}
