package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;

@Controller
@RequestMapping("/homepage")
public class HomePageController {

    @Autowired
    Environment environment;//注入资源池,即application.properties

    @Autowired
    private MovieService movieService;

    @RequestMapping(value="")
    public String homePage(@RequestParam(value="pageIndex", defaultValue="1") Integer pageIndex,
                           @RequestParam(value="pageSize", defaultValue = "6") Integer pageSize,
                           @RequestParam(value="listSize", defaultValue = "7") Integer listSize,
                           Model model){
        HashMap<String, List<Movie>> genre_movies = movieService.getMoviesFromDataManager(pageIndex, pageSize, listSize);
        model.addAttribute("genre_movies", genre_movies);
        return "homePage";
    }
}
