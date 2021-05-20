package com.penistrong.CinemaChainPlatform.online.service;

import com.penistrong.CinemaChainPlatform.online.model.DNNmodel;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.Rating;
import com.penistrong.CinemaChainPlatform.online.model.User;

import java.util.List;
import java.util.Map;

public interface UserService {

    Map<String, String> login(String username, String password);

    boolean register(String username, String password);

    List<Movie> getUserRecList(int userId, int size, DNNmodel model);

    boolean addWatchList(int userId, int movieId);

    boolean delWatchList(int userId, int movieId);

    boolean removeWatchList(int userId);

    User getUser(Integer userId);

    List<Rating> getRatingsList(Integer userId, Integer size);
}
