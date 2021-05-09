package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.Rating;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import com.penistrong.CinemaChainPlatform.online.service.RatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/movie")
public class MovieDetailPageController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private RatingService ratingService;

    @GetMapping("/{movieId}")
    public String MovieDetailPage(@PathVariable int movieId, Model model){
        Movie movie = movieService.getMovieFromDataManager(movieId);
        model.addAttribute("movie", movie);
        //已改为Vue懒加载，因为要计算相似度，可能会有延迟
        return "movieDetail";
    }

    @PostMapping("/{movieId}")
    @ResponseBody
    public Movie getMovieDetail(@PathVariable int movieId){
        return movieService.getMovieFromDataManager(movieId);
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

    /**
     * 获取当前用户对给定电影的评分，如果没有则返回null
     * @param session Session取登录用户信息
     * @param movieId 电影ID
     * @return
     */
    @PostMapping("/{movieId}/getUserRatingValue")
    @ResponseBody
    public Map<String, String> getUserRatingValue(HttpSession session, @PathVariable int movieId){
        Map<String, String> res = new HashMap<>();
        String userId = (String) session.getAttribute("userId");
        if(userId == null || userId.isEmpty()){  //没登录就是默认null值
            res.put("status", "failed");
        } else{
            //用户已登录
            Float ratingValue = this.ratingService.getUserRatingValue(Integer.parseInt(userId), movieId);
            if(ratingValue != -1f) {
                res.put("status", "success");
                res.put("ratingValue", String.valueOf(ratingValue));
            }
            else{  //该用户没有对该电影进行过评分
                res.put("status", "failed");
            }
        }
        return res;
    }

    /**
     * 给电影评价的接口，前端视情况进行API请求频率限制(使用lodash即可)
     * ContentType:application/x-www-form-urlencoded,所以是传统"?param_name=value"的方式来传参的,这里使用@RequestParam接收
     * @param movieId 待评分的电影Id
     * @param ratingValue 评分值
     * @return JSON数据，包含状态和错误提示
     */
    @PostMapping("/{movieId}/rateMovie")
    @ResponseBody
    public Map<String, String> rateMovie(HttpSession session, @PathVariable int movieId, @RequestParam(value = "ratingValue") Float ratingValue){
        Map<String, String> res = new HashMap<>();
        String userId = (String) session.getAttribute("userId");
        if(userId == null || userId.isEmpty()){
            res.put("status", "failed");
            res.put("error_msg", "Please log in first");
        } else{
            //用户已登录,注意timestamp是自UNIX元年的秒数而非毫秒数要除以1000
            if(this.ratingService.saveOrUpdateUserRating(new Rating(Integer.parseInt(userId), movieId, ratingValue, System.currentTimeMillis() / 1000)))
                res.put("status", "success");
            else{
                res.put("status", "failed");
                res.put("error_msg", "Could not rate movie!");
            }
        }
        return res;
    }
}
