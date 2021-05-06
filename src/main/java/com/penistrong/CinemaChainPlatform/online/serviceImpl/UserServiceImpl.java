package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.mapper.UserMapper;
import com.penistrong.CinemaChainPlatform.online.model.DNNmodel;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SortByMethod;
import com.penistrong.CinemaChainPlatform.online.model.User;
import com.penistrong.CinemaChainPlatform.online.redis.RedisUtils;
import com.penistrong.CinemaChainPlatform.online.service.UserService;
import com.penistrong.CinemaChainPlatform.online.util.Config;
import com.penistrong.CinemaChainPlatform.online.util.Utility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 线上服务模块中的用户服务
 * 要进行组合的特征从Redis中取出，再交付DNN模型线上服务(TF Serving)进行线上推断
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    //操作Redis的工具Bean
    @Autowired
    private RedisUtils redisUtils;

    public PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, String> login(String username, String password) {
        Map<String, String> res = new HashMap<>();

        String encodedPassword = this.userMapper.queryUserPassword(username);
        if(encodedPassword == null || encodedPassword.isEmpty()) {
            res.put("error_code", "404.1");
        }
        else if(this.passwordEncoder.matches(password, encodedPassword)) {  //通过BcryptPasswordEncoder匹配成功
            res.put("currentUserId", String.valueOf(this.userMapper.selectUser(username, encodedPassword).getUserId()));
        }
        else
            res.put("error_code", "404.2");

        return res;
    }

    //加上事务注解，防止两表插入时，出现插入异常
    @Transactional
    @Override
    public boolean register(String username, String password) {
        String encodedPassword = this.userMapper.queryUserPassword(username);
        //查询到为空值，说明该用户不存在，则可以注册
        if(encodedPassword == null || encodedPassword.equals("")){
            //插入成功后会返回插入成功的元组数，由于是注册这里判断一下元组数是否为1即可
            String newEncodedPassword = this.passwordEncoder.encode(password);
            int insert_user_res = this.userMapper.createUser(username, newEncodedPassword);
            User createdUser = this.userMapper.selectUser(username, newEncodedPassword);
            int insert_authority_res = this.userMapper.createAuthority(createdUser.getUserId(), createdUser.getUsername());
            //两者都要成功
            return insert_user_res == 1 && insert_authority_res == 1;
        }
        return false;
    }

    @Override
    public List<Movie> getUserRecList(int userId, int size, DNNmodel model) {
        //TODO:从MySQL数据库中拿到用户
        User user = DataManager.getInstance().getUserById(userId);
        if(user == null)
            return new ArrayList<>();
        //hyper parameter:设定候选集大小为800
        final int CANDIDATE_SIZE = 800;
        List<Movie> candidate_list = DataManager.getInstance().getOrderedMovies(CANDIDATE_SIZE, SortByMethod.rating);

        //加载存储在redis中的用户Embedding
        if(Config.EMB_DATA_SOURCE.equals(Config.DATA_SOURCE_REDIS)){
            String userEmbeddingKey = Config.USER_EMBEDDING_PREFIX_IN_REDIS + userId;
            String userEmbedding = (String) redisUtils.get(userEmbeddingKey);
            if(userEmbedding != null && !userEmbedding.isEmpty())
                user.setEmb(Utility.parseEmbStr(userEmbedding));
        }
        //加载存储在redis中的用户Feature
        if(Config.IS_LOAD_USER_FEATURE_FROM_REDIS){
            String userFeatureKey = Config.USER_FEATURE_PREFIX_IN_REDIS + userId;
            //拿到Redis中该user对应的哈希表，表项为特征键值对
            //用Stream从Map<Object, Object>转化为Map<String,String>
            Map<String, String> userFeatures = redisUtils.hgetAll(userFeatureKey).entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> (String) entry.getKey(),
                            entry -> (String) entry.getValue()
                            ));
            if(!userFeatures.isEmpty())
                user.setUserFeatures(userFeatures);
        }

        //调用DNN排序层
        List<Movie> rankedList = ranker(user, candidate_list, model);

        return rankedList.subList(0, Math.min(rankedList.size(), size));
    }

    @Override
    public boolean addWatchList(int userId, int movieId) {
        //检查userId与movieId是否存在于表中，防止因参照完整性造成的插入异常
        if(this.userMapper.isUserExist(userId) == 1 && this.userMapper.isMovieExist(movieId) == 1)
            return this.userMapper.insertWatchList(userId, movieId) == 1;
        return false;
    }

    @Override
    public boolean delWatchList(int userId, int movieId) {
        //从该用户的待看列表中删除该电影
        return this.userMapper.deleteWatchList(userId, movieId) == 1;
    }

    @Override
    public boolean removeWatchList(int userId) {
        //清空该用户待看列表
        int deleted_rows = this.userMapper.removeWatchList(userId);
        //反正一股脑都删了，除了抛出异常的情况，懒得写了
        return true;
    }

    public List<Movie> ranker(User user, List<Movie> candidate_list, DNNmodel model){
        HashMap<Movie, Double> candidateScoreMap = new HashMap<>();
        switch(model){
            case EmbeddingMLP:
            case NeuralCF:
            case WideNDeep:
            case DeepFM:
            case DIN:
            default: //未指定DNNmodel时,默认使用朴素Embedding相似度计算方法，计算用户Emb同各候选电影Emb的余弦相似度
                for(Movie candidate : candidate_list)
                    candidateScoreMap.put(candidate, calculateEmbSimilarity(user, candidate));
        }

        List<Movie> rankedList = new ArrayList<>();
        //按相似度得分(值)降序排序并只收集电影(键)
        candidateScoreMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> rankedList.add(e.getKey()));

        return rankedList;
    }

    //计算用户Embedding同候选电影Embedding的相似度，包了一层作空类型检查
    public double calculateEmbSimilarity(User user, Movie candidate){
        if(user == null || candidate == null || user.getEmb() == null || candidate.getEmb() == null)
            return -1;
        return user.getEmb().calculateSimilarity(candidate.getEmb());
    }

}
