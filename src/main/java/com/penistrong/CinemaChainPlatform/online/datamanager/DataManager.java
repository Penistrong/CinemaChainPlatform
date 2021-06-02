package com.penistrong.CinemaChainPlatform.online.datamanager;


import com.penistrong.CinemaChainPlatform.online.model.Movie;
import com.penistrong.CinemaChainPlatform.online.model.Rating;
import com.penistrong.CinemaChainPlatform.online.model.SortByMethod;
import com.penistrong.CinemaChainPlatform.online.model.User;
import com.penistrong.CinemaChainPlatform.online.redis.JedisClient;
import com.penistrong.CinemaChainPlatform.online.util.Config;
import com.penistrong.CinemaChainPlatform.online.util.Utility;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/*
    DataManager is an utility class, take responsibility of data loading login from data set
*/
public class DataManager {
    //Singleton instance
    private static volatile DataManager instance;
    HashMap<Integer, Movie> movieMap;
    HashMap<Integer, User> userMap;
    //Genre reverse index for quick querying all movies of a genre
    HashMap<String, List<Movie>> genreReverseIndexMap;

    public DataManager(){
        this.movieMap = new HashMap<>();
        this.userMap = new HashMap<>();
        this.genreReverseIndexMap = new HashMap<>();
        instance = this;
    }

    //获取DataManager的实例，当实例为空时使用线程锁保证只有一个实例被创建
    public static DataManager getInstance(){
        if(instance == null){
            synchronized (DataManager.class){
                if(instance == null)
                    instance = new DataManager();
            }
        }
        return instance;
    }

    //Load data from local file including movie, rating, link data and model data like Embedding vectors.
    public void loadData(String movieDataPath, String linkDataPath, String ratingDataPath, String movieEmbPath, String userEmbPath, String movieRedisKey, String userRedisKey) throws Exception{
        //加载电影基本信息
        loadMovieData(movieDataPath);
        //加载电影在IMDB和TMDB的链接ID
        loadLinkData(linkDataPath);
        //加载评分信息
        loadRatingData(ratingDataPath);
        //从Redis加载电影的Embedding
        loadMovieEmb(movieEmbPath, movieRedisKey);
        //从Redis加载电影的Features
        if (Config.IS_LOAD_ITEM_FEATURE_FROM_REDIS)
            loadMovieFeatures(Config.MOVIE_FEATURE_PREFIX_IN_REDIS);
        //从Redis加载用户的Embedding
        loadUserEmb(userEmbPath, userRedisKey);
    }

    //load movie data from movies.csv
    private void loadMovieData(String movieDataPath) throws Exception{
        System.out.println("Loading movie data from " + movieDataPath + " ...");
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("resources/dataset/movies.csv");
        assert is != null;
        BufferedReader bufReader = new BufferedReader(new InputStreamReader(is));
        String movieRawData = "";
        boolean skipFirstLine = true;
        //try (Scanner scanner = new Scanner(new File(movieDataPath))) {
            while ((movieRawData = bufReader.readLine()) != null) {
                //String movieRawData = scanner.nextLine();
                if (skipFirstLine){
                    skipFirstLine = false;
                    continue;
                }
                String[] movieData = movieRawData.split(",");
                if (movieData.length == 3){
                    Movie movie = new Movie();
                    movie.setMovieId(Integer.parseInt(movieData[0]));
                    int releaseYear = parseReleaseYear(movieData[1].trim());
                    if (releaseYear == -1){
                        movie.setTitle(movieData[1].trim());
                    }else{
                        movie.setReleaseYear(releaseYear);
                        movie.setTitle(movieData[1].trim().substring(0, movieData[1].trim().length()-6).trim());
                    }
                    String genres = movieData[2];
                    if (!genres.trim().isEmpty()){
                        String[] genreArray = genres.split("\\|");
                        for (String genre : genreArray){
                            movie.addGenre(genre);
                            addMovie2GenreIndex(genre, movie);
                        }
                    }
                    this.movieMap.put(movie.getMovieId(), movie);
                }
            //}
        }
        System.out.println("Loading movie data completed. " + this.movieMap.size() + " movies in total.");
    }

    //add movie to genre reversed index
    private void addMovie2GenreIndex(String genre, Movie movie){
        if (!this.genreReverseIndexMap.containsKey(genre)){
            this.genreReverseIndexMap.put(genre, new ArrayList<>());
        }
        this.genreReverseIndexMap.get(genre).add(movie);
    }

    //parse release year: format like "Red Dead Redemption (2018)"
    private int parseReleaseYear(String rawTitle){
        if (null == rawTitle || rawTitle.trim().length() < 6){
            return -1;
        }else{
            String yearString = rawTitle.trim().substring(rawTitle.length()-5, rawTitle.length()-1);
            try{
                return Integer.parseInt(yearString);
            }catch (NumberFormatException exception){
                return -1;
            }
        }
    }

    //load links data from links.csv
    private void loadLinkData(String linkDataPath) throws Exception{
        System.out.println("Loading link data from " + linkDataPath + " ...");
        int count = 0;
        boolean skipFirstLine = true;
        try (Scanner scanner = new Scanner(new File(linkDataPath))) {
            while (scanner.hasNextLine()) {
                String linkRawData = scanner.nextLine();
                if (skipFirstLine){
                    skipFirstLine = false;
                    continue;
                }
                String[] linkData = linkRawData.split(",");
                if (linkData.length == 3){
                    int movieId = Integer.parseInt(linkData[0]);
                    Movie movie = this.movieMap.get(movieId);
                    if (null != movie){
                        count++;
                        movie.setImdbId(linkData[1].trim());
                        movie.setTmdbId(linkData[2].trim());
                    }
                }
            }
        }
        System.out.println("Loading link data completed. " + count + " links in total.");
    }

    //load ratings data from ratings.csv
    private void loadRatingData(String ratingDataPath) throws Exception{
        System.out.println("Loading rating data from " + ratingDataPath + " ...");
        boolean skipFirstLine = true;
        int count = 0;
        try (Scanner scanner = new Scanner(new File(ratingDataPath))) {
            while (scanner.hasNextLine()) {
                String ratingRawData = scanner.nextLine();
                if (skipFirstLine){
                    skipFirstLine = false;
                    continue;
                }
                String[] linkData = ratingRawData.split(",");
                if (linkData.length == 4){
                    count ++;
                    Rating rating = new Rating();
                    rating.setUserId(Integer.parseInt(linkData[0]));
                    rating.setMovieId(Integer.parseInt(linkData[1]));
                    rating.setScore(Float.parseFloat(linkData[2]));
                    rating.setTimestamp(Long.parseLong(linkData[3]));
                    Movie movie = this.movieMap.get(rating.getMovieId());
                    if (null != movie){
                        movie.addRating(rating);
                    }
                    if (!this.userMap.containsKey(rating.getUserId())){
                        User user = new User();
                        user.setUserId(rating.getUserId());
                        this.userMap.put(user.getUserId(), user);
                    }
                    this.userMap.get(rating.getUserId()).addRating(rating);
                }
            }
        }

        System.out.println("Loading rating data completed. " + count + " ratings in total.");
    }

    //load movie embedding
    private void loadMovieEmb(String movieEmbPath, String embKey) throws Exception{
        if (Config.EMB_DATA_SOURCE.equals(Config.DATA_SOURCE_FILE)) {
            System.out.println("Loading movie embedding from " + movieEmbPath + " ...");
            int validEmbCount = 0;
            try (Scanner scanner = new Scanner(new File(movieEmbPath))) {
                while (scanner.hasNextLine()) {
                    String movieRawEmbData = scanner.nextLine();
                    String[] movieEmbData = movieRawEmbData.split(":");
                    if (movieEmbData.length == 2) {
                        Movie m = getMovieById(Integer.parseInt(movieEmbData[0]));
                        if (m != null) {
                            m.setEmb(Utility.parseEmbStr(movieEmbData[1]));
                            validEmbCount++;
                        }
                    }
                }
            }
            System.out.println("Loading movie embedding completed. " + validEmbCount + " movie embeddings in total.");
        }
        else{
            System.out.println("Loading movie embedding from Redis ...");
            //先拿到前缀为embKey的各键组成的集合
            Set<String> movieEmbKeys = JedisClient.getInstance().keys(embKey + "*");
            //不用PipeLine的话一个个读取十分耗时，用PipeLine直接一次提交所有请求
            Pipeline pipe = JedisClient.getInstance().pipelined();
            Map<String, Response<String>> movieEmbMap = new HashMap<>(movieEmbKeys.size());
            for(String movieEmbKey : movieEmbKeys){
                movieEmbMap.put(movieEmbKey, pipe.get(movieEmbKey));
            }
            //以弱事务形式执行pipeline
            pipe.sync();
            //对结果进行操作
            int validEmbCount = 0;
            for (Map.Entry<String, Response<String>> entry: movieEmbMap.entrySet()){
                String movieId = entry.getKey().split(":")[1];
                Movie m = getMovieById(Integer.parseInt(movieId));
                if (m != null) {
                    m.setEmb(Utility.parseEmbStr(entry.getValue().get()));
                    validEmbCount++;
                }
            }
            System.out.println("Loading movie embedding completed. " + validEmbCount + " movie embeddings in total.");
        }
    }

    //load movie features
    private void loadMovieFeatures(String movieFeaturesPrefix) throws Exception{
        System.out.println("Loading movie features from Redis ...");
        Set<String> movieFeaturesKeys = JedisClient.getInstance().keys(movieFeaturesPrefix + "*");
        //创建管道
        Pipeline pipe = JedisClient.getInstance().pipelined();
        Map<String, Response<Map<String, String>>> mfMap = new HashMap<>(movieFeaturesKeys.size());
        for(String movieFeaturesKey : movieFeaturesKeys){
            mfMap.put(movieFeaturesKey, pipe.hgetAll(movieFeaturesKey));
        }
        pipe.sync();

        int validFeaturesCount = 0;
        for (Map.Entry<String, Response<Map<String, String>>> entry: mfMap.entrySet()){
            String movieId = entry.getKey().split(":")[1];
            Movie m = getMovieById(Integer.parseInt(movieId));
            if (null == m) {
                continue;
            }
            m.setMovieFeatures(entry.getValue().get());
            validFeaturesCount++;
        }
        System.out.println("Loading movie features completed. " + validFeaturesCount + " movie features in total.");
    }

    //load user embedding
    //由于数据集中的用户比较多，如果从Redis一次性将所有用户的Embedding加载出来有失偏颇，目前的考虑是建立一个活跃用户视图，只加载活跃用户的
    //非活跃用户线上临时拉取即可
    private void loadUserEmb(String userEmbPath, String embKey) throws Exception{
        //2021.04.14 暂时都从文件系统中读取，redis中还没有userEmb
        if (Config.EMB_DATA_SOURCE.equals(Config.DATA_SOURCE_FILE) || Config.EMB_DATA_SOURCE.equals(Config.DATA_SOURCE_REDIS)) {
            System.out.println("Loading user embedding from " + userEmbPath + " ...");
            int validEmbCount = 0;
            try (Scanner scanner = new Scanner(new File(userEmbPath))) {
                while (scanner.hasNextLine()) {
                    String userRawEmbData = scanner.nextLine();
                    String[] userEmbData = userRawEmbData.split(":");
                    if (userEmbData.length == 2) {
                        User u = getUserById(Integer.parseInt(userEmbData[0]));
                        if (null == u) {
                            continue;
                        }
                        u.setEmb(Utility.parseEmbStr(userEmbData[1]));
                        validEmbCount++;
                    }
                }
            }
            System.out.println("Loading user embedding completed. " + validEmbCount + " user embeddings in total.");
        }
    }

    //get movie object by movie id
    public Movie getMovieById(int movieId){
        return this.movieMap.get(movieId);
    }

    //get user object by user id
    public User getUserById(int userId){
        return this.userMap.get(userId);
    }

    //Get all the movies of a given genre
    public List<Movie> getMoviesByGenre(String genre){
        return this.genreReverseIndexMap.get(genre);
    }

    //Get a given size of the movies in a given genre, order them by param "sortBy" method
    public List<Movie> getOrderedMoviesByGenre(String genre, int size, SortByMethod method) {
        if(genre == null)
            return null;
        List<Movie> movies = new ArrayList<>(this.genreReverseIndexMap.get(genre));
        switch(method){
            case rating: //按评分排序
                movies.sort((m1, m2) -> Double.compare(m2.getAverageRating(), m1.getAverageRating()));
                break;
            case releaseYear: //按上映年份排序
                movies.sort((m1, m2) -> Integer.compare(m2.getReleaseYear(), m1.getReleaseYear()));
                break;
            default:
        }
        //如果超过给定大小则给出子列表
        return movies.subList(0, Math.min(movies.size(), size));
    }

    //Given a size and sortByMethod, order the movies and return a given size of List<Movie>
    public List<Movie> getOrderedMovies(int size, SortByMethod method){
        List<Movie> movies = new ArrayList<>(movieMap.values());
        switch (method) {
            case rating: //按评分排序
                movies.sort((m1, m2) -> Double.compare(m2.getAverageRating(), m1.getAverageRating()));
                break;
            case releaseYear: //按上映年份排序
                movies.sort((m1, m2) -> Integer.compare(m2.getReleaseYear(), m1.getReleaseYear()));
                break;
            default:
        }
        return movies.subList(0, Math.min(movies.size(), size));
    }

    //Get subMap of genre and its movieList by given pageSize
    public HashMap<String, List<Movie>> getMoviesByGenreNumbers(Integer pageSize, Integer listSize) {
        HashMap<String, List<Movie>> genre_movies = new HashMap<>();
        int counter = 1;
        for(HashMap.Entry<String, List<Movie>> entry : this.genreReverseIndexMap.entrySet()){
            genre_movies.put(entry.getKey(), entry.getValue().size() > listSize?entry.getValue().subList(0, listSize): entry.getValue());
            if(++counter > pageSize)
                break;
        }
        return genre_movies;
    }

}
