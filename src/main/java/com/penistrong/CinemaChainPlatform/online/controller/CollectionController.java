package com.penistrong.CinemaChainPlatform.online.controller;

import com.github.pagehelper.PageInfo;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.service.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/collection")
public class CollectionController {

    @Autowired
    private CollectionService collectionService;

    @GetMapping("/{genre}")
    public String CollectionPage(@PathVariable String genre,
                                 @RequestParam(value="pageIndex", defaultValue="1") Integer pageIndex,
                                 @RequestParam(value="pageSize", defaultValue = "30") Integer pageSize,
                                 Model model){
        PageInfo<Movie> movieList = collectionService.getMoviesByGenre(genre, pageIndex, pageSize);
        model.addAttribute("movieList", movieList);
        model.addAttribute(genre);
        return "collection";
    }
}
