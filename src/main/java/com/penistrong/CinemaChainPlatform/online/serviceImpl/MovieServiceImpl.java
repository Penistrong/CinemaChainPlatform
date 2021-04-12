package com.penistrong.CinemaChainPlatform.online.serviceImpl;

import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.mapper.MovieMapper;
import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.SimilarityMethod;
import com.penistrong.CinemaChainPlatform.online.model.SortByMethod;
import com.penistrong.CinemaChainPlatform.online.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
        List<Movie> candidates = generateCandidates(movie);
        //使用这些电影对应的Embedding的相关相似度计算方法排序这些候选电影
        List<Movie> rankedCandidates = ranker(movie, candidates, method);

        if(rankedCandidates.size() > size)
            rankedCandidates = rankedCandidates.subList(0, size);

        return rankedCandidates;
    }

    /**
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
}
