package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.model.DNNmodel;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.Rating;
import com.penistrong.CinemaChainPlatform.online.model.User;
import com.penistrong.CinemaChainPlatform.online.service.UserService;
import com.penistrong.CinemaChainPlatform.online.util.ABTest;
import com.penistrong.CinemaChainPlatform.online.util.Config;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController {

    //protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserService userService;

    /* 使用POST请求访问context_path/oauth/token获取access_token再访问受限资源（页面等），不用自写登录控制器了，用Token操作
    //Session登录控制器,GET请求访问登陆页面
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String LoginPage() {
        return "loginPage";
    }

    //处理form login请求,POST请求处理登录信息
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String Login() {

    }
    */

    //OAuth2登录控制器
    @RequestMapping("/login/oauth2")
    public String OAuth2LoginPage(){
        return "OAuthLoginPage";
    }

    //GET登录页面
    @GetMapping("/login")
    public String loginPage(){
        return "loginPage";
    }

    //POST登录
    @PostMapping("/login")
    @ResponseBody
    public Map<String, String> login(HttpSession session, @RequestBody Map<String, String> requestBody){
        Map<String, String> res = this.userService.login(requestBody.get("username"), requestBody.get("password"));

        if(res.containsKey("currentUserId")){
            //写入Session
            session.setAttribute("userId", res.get("currentUserId"));
            res.put("status", "success");
        }else {
            res.put("status", "failed");
            if(res.get("error_code").equals("404.1"))
                res.put("error_msg", "用户名不存在");
            else if(res.get("error_code").equals("404.2"))
                res.put("error_msg", "密码错误");
        }

        return res;
    }

    //POST注册
    @PostMapping("/register")
    @ResponseBody
    public Map<String, String> register(HttpSession session, @RequestBody Map<String, String> requestBody){
        String username = requestBody.get("username");
        String password = requestBody.get("password");
        boolean isSuccess = this.userService.register(username, password);
        Map<String, String> res = new HashMap<>();
        if(isSuccess){
            res.put("status", "success");
        }else{
            res.put("status", "failed");
            res.put("error_msg", "用户名已存在");
        }
        return res;
    }

    //测试Redis Session功能
    @PostMapping("/testRedisSession")
    @ResponseBody
    public Map<String, Object> testRedisSession(HttpServletRequest request){
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", request.getSession().getId());
        result.put("logged_userId_in_session", request.getSession().getAttribute("userId"));
        return result;
    }

    @GetMapping("/{userId}")
    public String getUserPage(@PathVariable Integer userId, HttpSession session, Model model){
        //将访问的用户的相关信息放入Model中,这部分就不使用Vue异步拉取了,直接服务器端Thymeleaf渲染
        model.addAttribute("user", this.userService.getUser(userId));
        model.addAttribute("isSelfVisited", false);
        //检查当前登录用户是否访问的是自己的用户页面，根据情况开放相关设置等
        String rawUserId = (String) session.getAttribute("userId");
        if(rawUserId != null && !rawUserId.isEmpty())
            //都不为空时,检查请求的用户页面和登录用户是否为同一页面
            if(userId == Integer.parseInt(rawUserId))
                model.addAttribute("isSelfVisited", true);
        return "userPage";
    }

    //为你推荐
    //使用A/B测试获取用户所在的桶
    @PostMapping("/{userId}/getUserRecList")
    @ResponseBody
    public List<Movie> getUserRecList(@PathVariable Integer userId,
                                      @RequestParam(name="size", defaultValue = "10")Integer size){
        if(Config.IS_ENABLE_AB_TEST)
            return userService.getUserRecList(userId, size, ABTest.getBucketByUserId(String.valueOf(userId)));
        else
            return userService.getUserRecList(userId, size, DNNmodel.DIN);
    }

    //添加电影至待看列表
    @PostMapping("/{userId}/addWatchList")
    @ResponseBody
    public Map<String, String> addWatchList(@PathVariable Integer userId,
                                            @RequestBody Map<String, String> requestBody){
        Map<String, String> res = new HashMap<>();
        if(!requestBody.containsKey("movieId")){
            res.put("status", "failed");
            res.put("error_msg", "Please provide valid movieId");
            return res;
        }
        int movieId = Integer.parseInt(requestBody.get("movieId"));
        if(this.userService.addWatchList(userId, movieId)){
            res.put("status", "success");
        }else{
            res.put("status", "failed");
            res.put("error_msg", "Could not insert into watch list");
        }
        return res;
    }

    //从待看列表删除电影
    @PostMapping("/{userId}/delWatchList")
    @ResponseBody
    public Map<String, String> delWatchList(@PathVariable Integer userId,
                                            @RequestBody Map<String, String> requestBody){
        Map<String, String> res = new HashMap<>();
        if(!requestBody.containsKey("movieId")){
            res.put("status", "failed");
            res.put("error_msg", "Please provide valid movieId");
            return res;
        }
        if(this.userService.delWatchList(userId, Integer.parseInt(requestBody.get("movieId")))){
            res.put("status", "success");
        }else{
            res.put("status", "failed");
            res.put("error_msg", "Could not delete from watch list");
        }
        return res;
    }

    //清空待看列表
    @PostMapping("/{userId}/removeWatchList")
    @ResponseBody
    public Map<String, String> removeWatchList(@PathVariable Integer userId){
        Map<String, String> res = new HashMap<>();
        if(this.userService.removeWatchList(userId)){
            res.put("status", "success");
        }else{
            res.put("status", "failed");
            res.put("error_msg", "Could not remove watch list");
        }
        return res;
    }

    //获取用户的评分历史
    @PostMapping("/{userId}/getRatingsList")
    @ResponseBody
    public List<Rating> getRatingsList(@PathVariable Integer userId, @RequestParam(name = "size", defaultValue = "5")Integer size){
        return this.userService.getRatingsList(userId, size);
    }

}
