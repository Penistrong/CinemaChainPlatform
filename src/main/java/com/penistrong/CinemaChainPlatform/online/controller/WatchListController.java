package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/watchlist")
public class WatchListController {

    @Autowired
    private MovieService movieService;

    /**
     * 根据传入的电影ID列表，找出其是否在当前session用户的待看列表中
     * @param movieIdList 装有各movieId的json对象，使用List接收
     * @return Result map which entry like (movieId: Integer, isInWatchList: Boolean)
     */
    @PostMapping("/getIsInWatchList")
    @ResponseBody
    public Map<Integer, Boolean> getIsInWatchList(HttpSession session, @RequestBody List<Integer> movieIdList) {
        Map<Integer, Boolean> res = new HashMap<>();
        String userId = (String) session.getAttribute("userId");
        //首先查看session中是否已有userId,即发送当前请求的客户端是否已登录
        if(userId == null || userId.isEmpty()){
            //未登录，使用stream新建Map，各movieId作为键，值全部为false
            res = movieIdList
                    .stream()
                    .collect(Collectors.toMap(
                            movieId -> movieId,
                            movieId -> false
                    ));
        }else{
            //已登录，调用service
            res = this.movieService.queryIsInWatchList(Integer.parseInt(userId), movieIdList);
        }
        return res;
    }

    @PostMapping("/addWatchList")
    @ResponseBody
    public Map<String, String> addWatchList(HttpSession session, @RequestBody Map<String, String> requestBody) {
        Map<String, String> res = new HashMap<>();
        String userId = (String) session.getAttribute("userId");
        String movieId = requestBody.get("movieId");
        if(userId == null || userId.isEmpty()){
            res.put("status", "failed");
            res.put("error_msg", "Please log in first");
        }
        else if(movieId == null || movieId.isEmpty()) {
            res.put("status", "failed");
            res.put("error_msg", "Please provide valid movieId");
        }
        else{
            //userId和movieId不为空
            if(this.movieService.addWatchList(Integer.parseInt(userId), Integer.parseInt(movieId)))
                res.put("status", "success");
            else{
                res.put("status", "failed");
                res.put("error_msg", "Could not add movie into watchlist");
            }
        }
        return res;
    }

    @PostMapping("/delWatchList")
    @ResponseBody
    public Map<String, String> delWatchList(HttpSession session, @RequestBody Map<String, String> requestBody) {
        Map<String, String> res = new HashMap<>();
        String userId = (String) session.getAttribute("userId");
        String movieId = requestBody.get("movieId");
        if(userId == null || userId.isEmpty()){
            res.put("status", "failed");
            res.put("error_msg", "Please log in first");
        }
        else if(movieId == null || movieId.isEmpty()) {
            res.put("status", "failed");
            res.put("error_msg", "Please provide valid movieId");
        }
        else{
            //userId和movieId不为空
            if(this.movieService.delWatchList(Integer.parseInt(userId), Integer.parseInt(movieId)))
                res.put("status", "success");
            else{
                res.put("status", "failed");
                res.put("error_msg", "Could not delete movie from watchlist");
            }
        }
        return res;
    }
}
