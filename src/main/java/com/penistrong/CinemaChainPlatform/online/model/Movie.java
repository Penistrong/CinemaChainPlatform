package com.penistrong.CinemaChainPlatform.online.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.penistrong.CinemaChainPlatform.online.datamanager.RatingListSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/*
    Movie model, contains attributes loaded from MovieLens movies.csv
    Include other advanced data like averageRating, emb(Embedding), etc.
*/
public class Movie {
    int movieId;
    String title;
    int releaseYear;
    String imdbId;
    String tmdbId;
    List<String> genres;
    //this movie's rating user number;
    int ratingNumber;
    //average rating score
    double averageRating;

    //Embedding of this movie
    //Using JsonIgnore to avoid transformation from object to Json with Embedding
    @JsonIgnore
    Embedding emb;

    //All rating socres list
    @JsonIgnore
    List<Rating> ratings;

    //Movie's feature
    @JsonIgnore
    Map<String, String> movieFeatures;

    final int TOP_RATING_SIZE = 10;

    @JsonSerialize(using = RatingListSerializer.class)
    List<Rating> topRatings;

    public Movie(){
        ratingNumber = 0;
        averageRating = 0;
        this.genres = new ArrayList<>();
        this.ratings = new ArrayList<>();
        this.topRatings = new ArrayList<>();
        this.emb = null;
        this.movieFeatures = null;
    }
    //暂时给MovieMapper的mybatis自动映射准备的构造函数
    public Movie(int movieId, String title, int releaseYear, String genres){
        this.movieId = movieId;
        this.title = title;
        this.releaseYear = releaseYear;
        this.genres = new ArrayList<>();
        if (!genres.trim().isEmpty())
            Collections.addAll(this.genres, genres.split("\\|"));
        ratingNumber = 0;
        averageRating = 0;
    }

    public int getMovieId() {
        return movieId;
    }

    public void setMovieId(int movieID) {
        this.movieId = movieID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(int releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public String getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(String tmdbId) {
        this.tmdbId = tmdbId;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public void addGenre(String genre) {
        this.genres.add(genre);
    }

    public int getRatingNumber() {
        return ratingNumber;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public Embedding getEmb() {
        return emb;
    }

    public void setEmb(Embedding emb) {
        this.emb = emb;
    }

    public List<Rating> getRatings() {
        return ratings;
    }

    public void setRatings(List<Rating> ratings) {
        this.ratings = ratings;
    }

    //Update related attributes
    public void addRating(Rating rating) {
        averageRating = (averageRating * ratingNumber + rating.getScore()) / (ratingNumber + 1);
        ratingNumber++;
        this.ratings.add(rating);
        addTopRating(rating);
    }

    public void addTopRating(Rating rating){
        if(this.topRatings.isEmpty()){
            this.topRatings.add(rating);
        }else{
            int index = 0;
            for(Rating topRating : this.topRatings){
                if(topRating.getScore() >= rating.getScore())
                    break;
                index++;
            }
            topRatings.add(index, rating);
            if(topRatings.size() > TOP_RATING_SIZE)
                topRatings.remove(0);
        }
    }

    public Map<String, String> getMovieFeatures() {
        return movieFeatures;
    }

    public void setMovieFeatures(Map<String, String> movieFeatures) {
        this.movieFeatures = movieFeatures;
    }

    public int getTOP_RATING_SIZE() {
        return TOP_RATING_SIZE;
    }

    public List<Rating> getTopRatings() {
        return topRatings;
    }

    public void setTopRatings(List<Rating> topRatings) {
        this.topRatings = topRatings;
    }
}
