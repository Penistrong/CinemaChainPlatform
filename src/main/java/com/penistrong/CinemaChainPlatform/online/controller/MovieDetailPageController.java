package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/movie")
public class MovieDetailPageController {

    @Autowired
    private MovieService movieService;

    @GetMapping("/{movieId}")
    public String MovieDetailPage(@PathVariable int movieId, Model model){
        Movie movie = movieService.getMovieFromDataManager(movieId);
        model.addAttribute("movie", movie);
        //TODO:改成懒加载，因为要计算相似度，可能会有延迟
        List<Movie> relatedMovies = movieService.getSimilarMovieRecList(movie.getMovieId(), 21, SimilarityMethod.Embedding);
        model.addAttribute("relatedMovies", relatedMovies);
        return "movieDetail";
    }
}
