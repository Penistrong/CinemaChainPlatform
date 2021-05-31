package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.mapper.UserMapper;
import com.penistrong.CinemaChainPlatform.online.model.*;
import com.penistrong.CinemaChainPlatform.online.redis.RedisUtils;
import com.penistrong.CinemaChainPlatform.online.service.UserService;
import com.penistrong.CinemaChainPlatform.online.util.Config;
import com.penistrong.CinemaChainPlatform.online.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.penistrong.CinemaChainPlatform.online.util.HttpAPIcaller.asyncSinglePostRequest;

/**
 * 线上服务模块中的用户服务
 * 要进行组合的特征从Redis中取出，再交付DNN模型线上服务(TF Serving)进行线上推断
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

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

    //根据userId获取user对象
    @Override
    public User getUser(Integer userId) {
        //DONE:已从数据库基表中fetch用户信息
        //User user = DataManager.getInstance().getUserById(userId);
        //如果不是初始数据集里的
        //if(user == null)
        //    user =  this.userMapper.selectUserById(userId);
        return this.userMapper.selectUserById(userId);
    }

    //根据userId获取对应User的评分历史，并根据历史找到对应的电影组成列表后返回
    @Override
    public List<Rating> getRatingsList(Integer userId, Integer size) {
        return this.userMapper.selectRatingsByUserId(userId, size);
    }
    /*
     * 注意不同模型的输入样本不同，但总输入样本包含用户与电影的所有特征，而用户特征是存储在Redis中，要实时线上取出
     * 电影特征在系统启动时已从Redis加载，如果候选电影是从DataManager中拿到的，那么其特征可以直接取出并与用户特征组装成组合特征
     */
    @Override
    public List<Movie> getUserRecList(int userId, int size, DNNmodel model) {
        //TODO:从MySQL数据库中拿到用户
        User user = DataManager.getInstance().getUserById(userId);
        if(user == null)
            return new ArrayList<>();
        //hyper parameter:设定候选集大小为800
        final int CANDIDATE_SIZE = 800;
        //按照评分排序召回前CANDIDATE_SIZE个评分最高的电影
        List<Movie> candidate_list = DataManager.getInstance().getOrderedMovies(CANDIDATE_SIZE, SortByMethod.rating);

        //加载存储在redis中的用户Embedding
        if(Config.EMB_DATA_SOURCE.equals(Config.DATA_SOURCE_REDIS)){
            String userEmbeddingKey = Config.USER_EMBEDDING_PREFIX_IN_REDIS + userId;
            String userEmbedding = (String) redisUtils.get(userEmbeddingKey);
            if(userEmbedding != null && !userEmbedding.isEmpty())
                user.setEmb(Utility.parseEmbStr(userEmbedding));
        }
        //加载存储在redis中的用户Feature
        //Done:fastJSON反序列化报错，原因待排查，目前使用NeuralCF模型，暂时不需要用户特征，可以先不加载
        //设置RedisTemplate的hashValueSerializer时从fastJsonSerializer改为StringRedisSerializer,可成功解析hashMap里<k,v>的值
        //原因不明
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

            logger.info("Fetched User[userId=" + user.getUserId() + "]'s feature from Redis");
            logger.info(user.getUserFeatures().toString());
        }

        //调用DNN排序层
        logger.info("Call Model:" + model.toString() + " to rank candidates");
        List<Movie> rankedList = ranker(user, candidate_list, model);

        return rankedList.subList(0, Math.min(rankedList.size(), size));
    }

    public List<Movie> ranker(User user, List<Movie> candidate_list, DNNmodel model){
        HashMap<Movie, Double> candidateScoreMap = new HashMap<>();
        switch(model){
            case EmbeddingMLP:
            case NeuralCF:
                call_NeuralCF_TFServing(user, candidate_list, candidateScoreMap);
                break;
            case WideNDeep:
            case DeepFM:
            case DIN:
                call_DIN_TFServing(user, candidate_list, candidateScoreMap);
                break;
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

    /**
     * 调用线上部署的NeuralCF模型的TF Serving服务，获取线上推断结果
     * 将结果存放进候选电影分数Map中
     * API返回值格式
     {
     "predictions": [[0.824034274], [0.86393261], [0.921346784], [0.957705915], [0.875154734], [0.905113697], [0.831545711], [0.926080644], [0.898158073]...]
     }
     * @param user              input user
     * @param candidates        candidate movies
     * @param candidateScoreMap save prediction score into the map
     */
    public void call_NeuralCF_TFServing(User user, List<Movie> candidates, HashMap<Movie, Double> candidateScoreMap){
        if(user == null || candidates == null || candidates.size() == 0)
            return;
        JSONArray instances = new JSONArray();
        for(Movie m : candidates){
            JSONObject instance = new JSONObject();
            instance.put("userId", user.getUserId());
            instance.put("movieId", m.getMovieId());
            instances.add(instance);
        }
        //将JSON Array放入JSON对象中
        JSONObject instancesRoot = new JSONObject();
        instancesRoot.put("instances", instances);

        //调用TF Serving API
        String predictionsScores = asyncSinglePostRequest(Config.TF_SERVING_NEURAL_CF_ENDPOINT, instancesRoot.toString());
        JSONObject predictionsObject = JSONObject.parseObject(predictionsScores);

        logger.info("TF Serving API return: " + predictionsObject.toString());

        JSONArray scores = predictionsObject.getJSONArray("predictions");
        for(int i = 0;i < candidates.size();i++)
            candidateScoreMap.put(candidates.get(i), scores.getJSONArray(i).getDouble(0));
    }

    /**
     * 调用DIN的TF Serving服务
     * @param user              输入用户
     * @param candidates        候选电影列表
     * @param candidateScoreMap 候选者预测得分
     */
    public void call_DIN_TFServing(User user, List<Movie> candidates, HashMap<Movie, Double> candidateScoreMap){
        if(user == null || candidates == null || candidates.size() == 0)
            return;
        JSONArray instances = new JSONArray();
        for(Movie m : candidates){
            JSONObject instance = new JSONObject();
            instance.put("userId", user.getUserId());
            instance.put("movieId", m.getMovieId());
            //由于redis拿到用户或者电影的特征集合后以Map<String, String>形式存储，但模型的输入样本中有的字段是int有的是float，要进行转换
            //组装User Features //instance.putAll(user.getUserFeatures());
            Map<String, String> uf = user.getUserFeatures();
            instance.put("userAvgRating", Float.parseFloat(uf.get("userAvgRating")));
            instance.put("userRatingStddev", Float.parseFloat(uf.get("userRatingStddev")));
            instance.put("userRatingCount", Integer.parseInt(uf.get("userRatingCount")));
            instance.put("userRatedMovie0", Integer.parseInt(uf.get("userRatedMovie0")));
            instance.put("userRatedMovie1", Integer.parseInt(uf.get("userRatedMovie1")));
            instance.put("userRatedMovie2", Integer.parseInt(uf.get("userRatedMovie2")));
            instance.put("userRatedMovie3", Integer.parseInt(uf.get("userRatedMovie3")));
            instance.put("userRatedMovie4", Integer.parseInt(uf.get("userRatedMovie4")));
            instance.put("userGenre0", uf.get("userGenre0"));
            instance.put("userGenre1", uf.get("userGenre1"));
            instance.put("userGenre2", uf.get("userGenre2"));
            instance.put("userGenre3", uf.get("userGenre3"));
            instance.put("userGenre4", uf.get("userGenre4"));

            //组装Movie Features //instance.putAll(m.getMovieFeatures());
            Map<String, String> mf = m.getMovieFeatures();
            instance.put("movieAvgRating", Float.parseFloat(mf.get("movieAvgRating")));
            instance.put("movieRatingStddev", Float.parseFloat(mf.get("movieRatingStddev")));
            instance.put("movieRatingCount", Integer.parseInt(mf.get("movieRatingCount")));
            instance.put("releaseYear", Integer.parseInt(mf.get("releaseYear")));
            instance.put("genre0", mf.get("genre0"));
            instance.put("genre1", mf.get("genre1"));
            instance.put("genre2", mf.get("genre2"));

            instances.add(instance);
        }
        //包裹JSONArray
        JSONObject instanceRoot = new JSONObject();
        instanceRoot.put("instances", instances);
        logger.info(instanceRoot.toString());

        //调用TF Serving API
        String predictionsScores = asyncSinglePostRequest(Config.TF_SERVING_DIN_ENDPOINT, instanceRoot.toString());
        JSONObject predictionsObject = JSONObject.parseObject(predictionsScores);

        logger.info("TF Serving API return: " + predictionsObject.toString());

        JSONArray scores = predictionsObject.getJSONArray("predictions");
        for(int i = 0;i < candidates.size();i++)
            candidateScoreMap.put(candidates.get(i), scores.getJSONArray(i).getDouble(0));
    }

    /*
    public Map<String, ?> parseFeatures(Map<String, String> features, String method){
        if(method.equals("user")){
            Map<String, ?> parsedFeatures = new HashMap<>(features);
            parsedFeatures.put("userAvgRating", Float.parseFloat(features.get("userAvgRating")));
        }else if(method.equals("movie")){

        }
    }
    */
}
