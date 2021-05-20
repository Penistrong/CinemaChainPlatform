package com.penistrong.CinemaChainPlatform.online.service;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MovieService {
    HashMap<String, List<Movie>> getMoviesFromDataManager(Integer pageIndex, Integer pageSize, Integer listSize);

    Movie getMovieFromDataManager(int movieId);

    List<Movie> getSimilarMovieRecList(int movieId, int size, SimilarityMethod method);

    Map<Integer, Boolean> queryIsInWatchList(int userId, List<Integer> movieIdList);

    List<Movie> getWatchList(Integer userId, Integer size);

    boolean addWatchList(int userId, int movieId);

    boolean delWatchList(int userId, int movieId);

    boolean removeWatchList(int userId);
}
