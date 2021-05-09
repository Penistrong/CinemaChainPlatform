package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/homepage")
public class HomePageController {

    @Autowired
    Environment environment;//注入资源池,即application.properties

    @Autowired
    private MovieService movieService;

    @GetMapping(value="")
    public String homePage(@RequestParam(value="pageIndex", defaultValue="1") Integer pageIndex,
                           @RequestParam(value="pageSize", defaultValue = "6") Integer pageSize,
                           @RequestParam(value="listSize", defaultValue = "7") Integer listSize
                           /*Model model*/){
        //已经改为Vue异步加载电影列表，不使用Thymeleaf直接渲染了
        //HashMap<String, List<Movie>> genre_movies = this.movieService.getMoviesFromDataManager(pageIndex, pageSize, listSize);
        //model.addAttribute("genre_movies", genre_movies);
        return "homePage";
    }

    @PostMapping(value="/getGenresMovieList")
    @ResponseBody
    public Map<String, List<Movie>> getGenresMovieList(@RequestParam(value="pageIndex", defaultValue="1") Integer pageIndex,
                                                       @RequestParam(value="pageSize", defaultValue = "6") Integer pageSize,
                                                       @RequestParam(value="listSize", defaultValue = "7") Integer listSize){
        return this.movieService.getMoviesFromDataManager(pageIndex, pageSize, listSize);
    }
}
