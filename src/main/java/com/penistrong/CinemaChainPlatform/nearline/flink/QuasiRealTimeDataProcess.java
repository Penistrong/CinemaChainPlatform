package com.penistrong.CinemaChainPlatform.nearline.flink;


import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.io.TextInputFormat;
import org.apache.flink.api.java.io.jdbc.JDBCInputFormat;
import org.apache.flink.api.java.io.jdbc.split.ParameterValuesProvider;
import org.apache.flink.api.java.operators.DataSource;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.FileProcessingMode;
import org.apache.flink.streaming.api.windowing.time.Time;

import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.Serializable;

/**
 * 使用Flink完成准实时数据处理，将用户的行为历史记录落盘生成新的训练样本
 * 从数据库获取数据流，处理后生成训练样本下沉(sink)至Redis和离线训练样本集里
 */
@Configuration
@EnableScheduling
public class QuasiRealTimeDataProcess {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private long lastProcessTime;

    //被@PostConstruct修饰的方法会在服务器加载Servlet后运行，并且只会被服务器执行一次
    //服务器加载Servlet初始化顺序: Constructor => AutoWired => PostConstruct
    @PostConstruct
    private void initLastProcessTime(){
        this.lastProcessTime = System.currentTimeMillis() / 1000;
    }

    private static final Logger logger = LoggerFactory.getLogger(QuasiRealTimeDataProcess.class);

    //每个整点执行一次新数据落盘，生成新的训练样本
    @Scheduled(cron = "0 0 */1 * * *")
    public void processNewSample() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        long currentProcessTime = System.currentTimeMillis() / 1000;
        //定义数据源 data stream source
        DataStreamSource<Row> dataSource = env.createInput(JDBCInputFormat.buildJDBCInputFormat()
                .setDrivername(this.driverClassName)
                .setDBUrl(this.dbUrl)
                .setUsername(this.username)
                .setPassword(this.password)
                .setQuery("SELECT userId, movieId, score, timestamp FROM ratings WHERE timestamp BETWEEN ? AND ?")
                .setParametersProvider(() -> {
                    //选取处于上次处理和本次处理时间范围内的用户评分行为
                    Serializable[][] queryParameters = new Serializable[1][2];
                    String[] param = new String[2];
                    param[0] = String.valueOf(lastProcessTime);
                    param[1] = String.valueOf(currentProcessTime);
                    queryParameters[0] = param;
                    return queryParameters;
                })
                .setRowTypeInfo(new RowTypeInfo(
                        BasicTypeInfo.INT_TYPE_INFO,
                        BasicTypeInfo.INT_TYPE_INFO,
                        BasicTypeInfo.FLOAT_TYPE_INFO,
                        BasicTypeInfo.INT_TYPE_INFO))
                .finish()
        );
        //其次，定义数据转换操作 transformation
        //从元组Row解析成Rating类的数据流(测试后转换没有任何问题，都转换成了POJO)
        DataStream<RatingSample> ratingDataStream = dataSource.map(RatingSample::new);
        /*
        URL ratingResourcesPath = this.getClass().getResource("/resources/dataset/ratings.csv");

        //监控目录，检查是否有新文件
        assert ratingResourcesPath != null;
        TextInputFormat format = new TextInputFormat(new Path(ratingResourcesPath.getPath()));
        //首先，定义数据源
        DataStream<String> inputStream = env.readFile(
                format,
                ratingResourcesPath.getPath(),
                FileProcessingMode.PROCESS_CONTINUOUSLY,
                1000
        );

        DataStream<Rating> ratingDataStream = inputStream.map(Rating::new);
        */
        ratingDataStream.keyBy(rating -> rating.userId)
                .timeWindow(Time.hours(1))          //时间窗口大小为1个小时，每小时执行一次数据下沉
                .reduce((rating, t1) -> {
                            if(rating.timestamp.compareTo(t1.timestamp) > 0)
                                return rating;
                            else
                                return t1;
                        })                                   //定义时间窗口到期时的操作
                .addSink(new CustomSinkFunction()); //最后，定义数据下沉使用的方式，继承RichSinkFunction
        //定义完毕，调用Flink环境执行数据流处理
        env.execute();
    }

    //for Test
    public static void main(String[] args) throws Exception{
        new QuasiRealTimeDataProcess().processNewSample();
    }
}
