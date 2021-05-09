package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.penistrong.CinemaChainPlatform.online.mapper.RatingMapper;
import com.penistrong.CinemaChainPlatform.online.model.Rating;
import com.penistrong.CinemaChainPlatform.online.service.RatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RatingServiceImpl implements RatingService {

    @Autowired
    private RatingMapper ratingMapper;

    @Override
    public Float getUserRatingValue(int userId, int movieId) {
        Rating rating = this.ratingMapper.getUserRating(userId, movieId);
        if(rating == null)
            return -1f;
        else
            return rating.getScore();
    }

    @Override
    public boolean saveOrUpdateUserRating(Rating rating) {
        Rating ratingInDB = this.ratingMapper.getUserRating(rating.getUserId(), rating.getMovieId());
        if(ratingInDB == null){
            //如果没有该记录，则插入
            return this.ratingMapper.insertUserRating(rating) > 0;
        }else{
            //有该记录，执行更新
            return this.ratingMapper.updateUserRating(rating) > 0;
        }
    }
}
