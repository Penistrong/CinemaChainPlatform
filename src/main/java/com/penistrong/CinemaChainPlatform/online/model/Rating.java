package com.penistrong.CinemaChainPlatform.online.model;


/**
 * Rating Class, contains attributes loaded from movielens ratings.csv
 */
public class Rating {
    int userId;
    int movieId;
    float score;
    long timestamp;

    public Rating() {
    }

    public Rating(int userId, int movieId, float score, long timestamp) {
        this.userId = userId;
        this.movieId = movieId;
        this.score = score;
        this.timestamp = timestamp;
    }


    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getMovieId() {
        return movieId;
    }

    public void setMovieId(int movieId) {
        this.movieId = movieId;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
