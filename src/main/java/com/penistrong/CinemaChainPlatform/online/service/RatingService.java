package com.penistrong.CinemaChainPlatform.online.service;

import com.penistrong.CinemaChainPlatform.online.model.Rating;

public interface RatingService{
    Float getUserRatingValue(int userId, int movieId);

    boolean saveOrUpdateUserRating(Rating rating);
}
