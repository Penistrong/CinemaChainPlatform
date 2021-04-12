package com.penistrong.CinemaChainPlatform.online.service;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;

import java.util.HashMap;
import java.util.List;

public interface MovieService {
    HashMap<String, List<Movie>> getMoviesFromDataManager(Integer pageIndex, Integer pageSize, Integer listSize);

    Movie getMovieFromDataManager(int movieId);

    List<Movie> getSimilarMovieRecList(int movieId, int size, SimilarityMethod method);
}
