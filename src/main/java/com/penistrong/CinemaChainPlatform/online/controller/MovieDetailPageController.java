package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    /** 注意，该POST接口在前端基本是使用axios调用POST访问的，但是@RequestParam是从json字符串中解析出参数
     *  而axios的默认transformRequest(发送请求前的处理函数)源码中若判断当前携带的data是对象(即以花括号括起来的形式)
     *  若请求头未设置时会将Content-Type设置为application/json;charset=utf-8，并使用Json.stringify转换data对象
     *  查阅axios官方文档后，axios使用post发送数据时，默认是直接把json放到请求体中
     *  而@RequestParam注解是解析Content-Type="application/x-www-form-urlencoded"且分析QueryString，这与前述不符，自然解析不到携带的data中的参数
     *  使用@RequestBody注解，若json对象的root不是list，直接用Map<String, String>接收参数，自己转换对应的参数类型
     */
    @PostMapping("/{movieId}/getSimilarMovieRecList")
    @ResponseBody
    public List<Movie> getSimilarMovieRecList(@PathVariable int movieId,
                                              @RequestBody Map<String, String> requestBody){
        return movieService.getSimilarMovieRecList(movieId, Integer.parseInt(requestBody.get("size")), SimilarityMethod.Embedding);
    }
}
