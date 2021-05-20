package com.penistrong.CinemaChainPlatform.online.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

public class HttpAPIcaller {
    public static String asyncSinglePostRequest(String host, String body) {
        if(host == null || body.isEmpty())
            return null;
        try{
            final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();
            client.start();
            HttpEntity bodyEntity = new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8));
            HttpPost request = new HttpPost(host);
            request.setEntity(bodyEntity);
            final Future<HttpResponse> future_response = client.execute(request, null);
            final HttpResponse response = future_response.get();
            client.close();
            return getRespondContent(response);
        } catch (Exception e){
            e.printStackTrace();
            return "";
        }
    }

    private static String getRespondContent(HttpResponse response) throws Exception{
        HttpEntity entity = response.getEntity();
        InputStream is = entity.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 8);
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = reader.readLine()) != null)
            sb.append(line).append("\n");
        return sb.toString();
    }

    public static void main(String[] args){

        //视不同模型使用的输入列，要自行定义各自的
        //keys must be equal to:
        // movieAvgRating,
        // movieGenre1,movieGenre2,movieGenre3,
        // movieId,
        // movieRatingCount,
        // movieRatingStddev,
        // rating,
        // releaseYear,
        // timestamp,
        // userAvgRating,
        // userAvgReleaseYear,
        // userGenre1,userGenre2,userGenre3,userGenre4,userGenre5,
        // userId,
        // userRatedMovie1,
        // userRatedMovie2,
        // userRatedMovie3,
        // userRatedMovie4,
        // userRatedMovie5,
        // userRatingCount,
        // userRatingStddev,
        // userReleaseYearStddev"
        //}
        JSONObject instance = new JSONObject();
        instance.put("userId",10351);
        instance.put("movieId",52);

        /*
        instance.put("timestamp",1254725234);
        instance.put("userGenre0","Thriller");
        instance.put("userGenre1","Crime");
        instance.put("userGenre2","Drama");
        instance.put("userGenre3","Comedy");
        instance.put("userGenre4","Action");

        instance.put("genre0","Comedy");
        instance.put("genre1","Drama");
        instance.put("genre2","Romance");

        instance.put("userRatedMovie0",608);
        instance.put("userRatedMovie1",6);
        instance.put("userRatedMovie2",1);
        instance.put("userRatedMovie3",32);
        instance.put("userRatedMovie4",25);

        instance.put("rating",4.0);

        instance.put("releaseYear",1995);
        instance.put("movieRatingCount",2033);
        instance.put("movieAvgRating",3.54);
        instance.put("movieRatingStddev",0.91);
        instance.put("userRatingCount",7);
        instance.put("userRatedMovieAvgReleaseYear","1995.43");
        instance.put("userRatedMovieReleaseYearStddev",0.53);
        instance.put("userAvgRating",3.86);
        instance.put("userRatingStddev",0.69);*/


        JSONObject instance2 = new JSONObject();
        instance2.put("userId",10351);
        instance2.put("movieId",53);

        JSONArray instances = new JSONArray();
        instances.add(instance);
        instances.add(instance2);

        JSONObject instancesRoot = new JSONObject();
        instancesRoot.put("instances", instances);

        System.out.println(instancesRoot.toString());

        //System.out.println(asyncSinglePostRequest(Config.TF_SERVING_NEURAL_CF_ENDPOINT, instancesRoot.toString()));
        //调用TF Serving API
        String predictionsScores = asyncSinglePostRequest(Config.TF_SERVING_NEURAL_CF_ENDPOINT, instancesRoot.toString());
        System.out.println(predictionsScores);

        JSONObject predictionsObject = JSONObject.parseObject(predictionsScores);
        JSONArray scores = predictionsObject.getJSONArray("predictions");
        for(int i = 0;i < 2;i++)
            System.out.println(scores.getJSONArray(i).getDouble(0));
    }
}
