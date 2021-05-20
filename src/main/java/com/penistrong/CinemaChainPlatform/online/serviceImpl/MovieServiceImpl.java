package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.mapper.MovieMapper;
import com.penistrong.CinemaChainPlatform.online.model.*;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import static com.penistrong.CinemaChainPlatform.online.util.HttpAPIcaller.asyncSinglePostRequest;

import com.penistrong.CinemaChainPlatform.online.util.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.crypto.Data;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MovieServiceImpl implements MovieService {

    @Autowired
    private MovieMapper movieMapper;

    //从DataManager获得基础电影信息
    @Override
    public HashMap<String, List<Movie>> getMoviesFromDataManager(Integer pageIndex, Integer pageSize, Integer listSize) {
        //TODO:改写为PageInfo形式，可翻页
        return DataManager.getInstance().getMoviesByGenreNumbers(pageSize, listSize);
    }

    //根据电影id获取电影信息
    @Override
    public Movie getMovieFromDataManager(int movieId) {
        return DataManager.getInstance().getMovieById(movieId);
    }

    /**
     * 生成相似电影推荐列表
     * 单策略召回层，基于电影的风格标签快速过滤需要的Item
     * 局限性较大，使用多路召回更好
     * @param movieId input movie's ID
     * @param size  Size of similar items
     * @param method Similarity calculating method
     * @return List of similar movies
     */
    @Override
    public List<Movie> getSimilarMovieRecList(int movieId, int size, SimilarityMethod method) {
        Movie movie = DataManager.getInstance().getMovieById(movieId);
        if(movie == null)
            return new ArrayList<>();
        //得到与该电影genre(可能有多个)相同的候选电影
        //List<Movie> candidates = generateCandidates(movie);
        /*
        List<Movie> userHistory = new ArrayList<>();
        userHistory.add(DataManager.getInstance().getMovieById(movieId));
        List<Movie> candidates = multiRetrievalCandidates(userHistory);
         */
        List<Movie> candidates = retrievalCandidatesByEmbedding(movie, size);
        //使用这些电影对应的Embedding的相关相似度计算方法排序这些候选电影
        List<Movie> rankedCandidates = ranker(movie, candidates, method);

        if(rankedCandidates.size() > size)
            rankedCandidates = rankedCandidates.subList(0, size);

        return rankedCandidates;
    }

    /**
     * 给定movieId列表，查询其中各movie是否在当前user的待看列表中
     * @param userId 用户Id
     * @param movieIdList 待查询的movieId列表
     * @return res <K: movieId, V: isInWatchList>
     */
    @Override
    public Map<Integer, Boolean> queryIsInWatchList(int userId, List<Integer> movieIdList) {
        return movieIdList.stream().collect(Collectors.toMap(
                movieId -> movieId,
                movieId -> this.movieMapper.selectMovieInWatchList(userId, movieId) > 0
                ));
    }

    @Override
    public boolean addWatchList(int userId, int movieId) {
        return this.movieMapper.insertMovieIntoWatchList(userId, movieId) > 0;
    }

    @Override
    public boolean delWatchList(int userId, int movieId) {
        return this.movieMapper.deleteMovieFromWatchList(userId, movieId) > 0;
    }

    @Override
    public boolean removeWatchList(int userId) {
        return this.movieMapper.removeAllFromWatchList(userId) > 0;
    }

    @Override
    public List<Movie> getWatchList(Integer userId, Integer size) {
        List<Movie> watchList = this.movieMapper.getWatchListByUserId(userId, size);
        //由于数据库尚未存储所有电影的所有信息，所以拿到MovieList后再根据属性不全的对象使用DataManager单例拿到完整对象
        watchList = watchList.stream()
                .map(movie -> DataManager.getInstance().getMovieById(movie.getMovieId()))
                .collect(Collectors.toList());
        return watchList;
    }

    /**
     * 朴实的单路召回，写在这但不用
     * generate candidates for similar movies recommendation
     * @param movie input movie object
     * @return recommended movie candidates
     */
    public List<Movie> generateCandidates(Movie movie){
        //使用HashMap，去重，有些电影的部分genre是一致的
        HashMap<Integer, Movie> candidateMap = new HashMap<>();
        for(String genre : movie.getGenres()){
            //默认选取评分在前一百的电影
            List<Movie> oneCandidates = DataManager.getInstance().getOrderedMoviesByGenre(genre, 100, SortByMethod.rating);
            for(Movie candidate : oneCandidates){
                candidateMap.put(candidate.getMovieId(), candidate);
            }
        }
        //移除本电影
        candidateMap.remove(movie.getMovieId());
        //前面使用HashMap是为了去重，这里返回键值对中的值(Movie类的对象)即可
        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Rank movie candidates by given rank method
     * @param movie input movie
     * @param candidates Raw candidates of movie in its genres
     * @param method Rank method
     * @return List of ranked candidates
     */
    public List<Movie> ranker(Movie movie, List<Movie> candidates, SimilarityMethod method){
        HashMap<Movie, Double> candidateSimilarityMap = new HashMap<>();
        for(Movie candidate: candidates){
            double similarity;
            switch(method){
                case Embedding:
                    similarity = calculateEmbeddingSimilarScore(movie, candidate);
                    break;
                default:
                    similarity = calculateSimilarScore(movie, candidate);
            }
            candidateSimilarityMap.put(candidate, similarity);
        }
        List<Movie> rankedCandidates = new ArrayList<>();
        //使用JDK8的stream
        //以Entry中的值(这里是Double)进行比较，使用Comparator.reverseOrder()降序排列
        //最后将排序好的Entry里的键(Movie)依次放入rankedCandidates中
        candidateSimilarityMap.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(m -> rankedCandidates.add(m.getKey()));
        return rankedCandidates;
    }

    /**
     * Calculate similarity score between given movie and its one candidate
     * 朴素算法，只是根据两者类别相似度，并与评分进行加权计算后返回的简单相似度
     * @param movie input movie
     * @param candidate Raw candidate movie
     * @return Similarity Score
     */
    public double calculateSimilarScore(Movie movie, Movie candidate) {
        //记录两者相同的genre数
        int sameGenreCount = 0;
        for(String genre: movie.getGenres())
            if(candidate.getGenres().contains(genre))
                sameGenreCount++;
        //计算类别相似度
        double genreSimilarity = (double)sameGenreCount / (movie.getGenres().size() + candidate.getGenres().size()) / 2;
        //将评分从[0,5]->[0,1]
        double ratingScore = candidate.getAverageRating() / 5;

        //相似度权重为0.7
        double similarityWeight = 0.7;
        //评分权重为0.3
        double ratingScoreWeight = 0.3;

        //计算并返回加权平均和
        return genreSimilarity * similarityWeight + ratingScore * ratingScoreWeight;
    }

    /**
     * 使用movie和candidate对应的Embedding向量计算它们的相似度(使用余弦相似度)
     * @param movie input movie
     * @param candidate Raw candidate movie
     * @return Similarity Score
     */
    public double calculateEmbeddingSimilarScore(Movie movie, Movie candidate) {
        if(movie == null || candidate == null)
            return -1;
        //调用Embedding对象的相似度计算方法(使用余弦相似度)
        return movie.getEmb().calculateSimilarity(candidate.getEmb());
    }

    /**
     * 多路召回策略
     * 根据电影的类别、评分和上映年份快速召回生成候选列表
     * TODO:使用多线程并行，建立标签特征索引、建立常用召回集缓存等方法来进一步完善它
     * @param userHistory 用户观影历史
     * @return 多路召回策略生成的候选列表
     */
    public List<Movie> multiRetrievalCandidates(List<Movie> userHistory) {
        //用HashSet存储genres，去重
        HashSet<String> genres = new HashSet<>();
        //用HashMap存储键值对，多路召回中可能存在重复的电影，去重
        HashMap<Integer, Movie> candidateMap = new HashMap<>();
        //策略①:根据用户观影历史中的各电影风格召回电影候选集
        userHistory.forEach(m -> genres.addAll(m.getGenres()));
        genres.forEach(genre -> {
            DataManager.getInstance().getOrderedMoviesByGenre(genre, 20, SortByMethod.rating).forEach(candidate ->
                    candidateMap.put(candidate.getMovieId(), candidate));
        });
        //策略②:召回所有电影中评分最高的100部电影
        List<Movie> highRatingCandidates = DataManager.getInstance().getOrderedMovies(100, SortByMethod.rating);
        highRatingCandidates.forEach(candidate -> candidateMap.put(candidate.getMovieId(), candidate));
        //策略③:召回最新上映的100部电影
        List<Movie> latestCandidates = DataManager.getInstance().getOrderedMovies(100, SortByMethod.releaseYear);
        latestCandidates.forEach(candidate -> candidateMap.put(candidate.getMovieId(), candidate));
        //候选集里去除用户观看过的电影
        userHistory.forEach(watched_movie -> candidateMap.remove(watched_movie.getMovieId()));
        //生成候选集
        return new ArrayList<>(candidateMap.values());
    }

    /**
     * 基于Embedding进行召回，利用物品和用户Embedding相似性来构建召回层
     * 由Embedding的思想可知，可将多路召回中不同的召回策略作为附加信息融入Embedding向量中
     * 多路找回中不同的召回策略产生的相似度、热度等分值不具备可比性，无法据此决定每个召回策略召回Item的数量
     * 但是使用Embedding召回却可以把Embedding间的相似度作为唯一的判断标准，进而可以随意限定召回的候选集大小
     * 且计算Embedding向量间的相似度也十分简单，比如点积和常用的余弦相似度
     * @param movie 基于电影Embedding进行召回
     * @return 基于Embedding进行召回的候选集
     */
    public List<Movie> retrievalCandidatesByEmbedding(Movie movie, int size){
        if(movie == null || movie.getEmb() == null)
            return null;
        List<Movie> allCandidates = DataManager.getInstance().getOrderedMovies(10000, SortByMethod.rating);
        HashMap<Movie, Double> movieScoreMap = new HashMap<>();
        //逐一获取电影Embedding，并计算与用户Embedding的相似度
        //TODO:使用局部敏感哈希快速找到邻近向量，而不是使用这个线性时间的计算
        for(Movie candidate : allCandidates){
            if (candidate.getEmb() == null)
                continue;
            movieScoreMap.put(candidate, candidate.getEmb().calculateSimilarity(movie.getEmb()));
        }
        //allCandidates.forEach(candidate -> movieScoreMap.put(candidate, candidate.getEmb().calculateSimilarity(userEmbedding)));

        //移除用于计算的源电影
        movieScoreMap.remove(movie);

        //使用Stream对Map按值降序排序
        movieScoreMap = movieScoreMap.entrySet().stream()
                .sorted(Map.Entry.<Movie, Double>comparingByValue().reversed())
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (oldVal, newVal) -> oldVal,
                                LinkedHashMap::new
                        )
                );
        return new ArrayList<>(movieScoreMap.keySet()).subList(0, Math.min(movieScoreMap.size(), size));
    }
}
