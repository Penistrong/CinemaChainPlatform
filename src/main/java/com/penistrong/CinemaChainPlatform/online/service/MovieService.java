package com.penistrong.CinemaChainPlatform.online.service;

import com.penistrong.CinemaChainPlatform.online.model.Movie;

import java.util.HashMap;
import java.util.List;

public interface MovieService {
    HashMap<String, List<Movie>> getMoviesFromDataManager(Integer pageIndex, Integer pageSize, Integer listSize);

    Movie getMovieFromDataManager(int movieId);
}
