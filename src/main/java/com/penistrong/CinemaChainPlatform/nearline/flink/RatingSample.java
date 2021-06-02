package com.penistrong.CinemaChainPlatform.nearline.flink;

import org.apache.flink.types.Row;

//使用ratings.csv数据集中的格式模拟数据流环境，即新样本落盘至其内
public class RatingSample {
    public String userId;           //column 1
    public String movieId;          //column 2
    public String rating;           //column 3
    public String timestamp;        //column 4
    public String latestMovieId;    //Repeat for Window Slide

    public RatingSample(String line){
        String[] lines = line.split(",");
        this.userId = lines[0];
        this.movieId = lines[1];
        this.rating = lines[2];
        this.timestamp = lines[3];
        this.latestMovieId = lines[1];
    }

    public RatingSample(Row row){
        this.userId = String.valueOf(row.getField(0));
        this.movieId = String.valueOf(row.getField(1));
        this.rating = String.valueOf(row.getField(2));
        this.timestamp = String.valueOf(row.getField(3));
        this.latestMovieId = this.movieId;
    }
}
