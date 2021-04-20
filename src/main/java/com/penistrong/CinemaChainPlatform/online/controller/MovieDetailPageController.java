package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        //已改为Vue懒加载，因为要计算相似度，可能会有延迟
        return "movieDetail";
    }

    //用于前后端分离，异步加载相似电影
    @PostMapping("/{movieId}/getSimilarMovieRecList")
    @ResponseBody
    public List<Movie> getSimilarMovieRecList(@PathVariable int movieId,
                                              @RequestParam(name="size", defaultValue = "21") int size){
        return movieService.getSimilarMovieRecList(movieId, size, SimilarityMethod.Embedding);
    }
}
