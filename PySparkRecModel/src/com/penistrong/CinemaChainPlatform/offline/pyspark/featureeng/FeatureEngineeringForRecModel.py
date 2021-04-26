#!/usr/bin/env python
# -*- encoding: utf-8 -*-
'''
@File    :   FeatureEngineeringForRecModel.py
@Time    :   2021/04/24 21:07:19
@Author  :   Penistrong 
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   用pyspark处理数据集，生成特征和样本并存储到redis中供模型线上服务使用
'''

# here put the import lib
from typing import List

import redis
import string
from collections import defaultdict

from pyspark import SparkConf, Row
from pyspark.sql import SparkSession, DataFrame, Window
from pyspark.sql import functions as F
from pyspark.sql.types import *

'''
不使用numpy和tensorflow原生处理数据集，是因为在并行处理数据的能力上它们都不如Spark
使用Spark进行数据清洗、预处理和特征提取，发挥Spark处理数据的长处
针对MovieLens数据集:
1.movies.csv[movieId, title, genres](注意releaseYear包含在title中，用字符串处理可以简单提取出来)
2.ratings.csv[userId, movieId, rating, timestamp]
从这两张表中按U/I/C提取这三大类推荐系统常用特征:
1.U(User feature):从ratings.csv中可以提取用户历史行为特征，联合movies.csv可以提取用户的统计类特征
2.I(Item feature):从movies.csv可以提取各物品的基本信息，联合ratings.csv可以提取物品的统计类特征
3.C(Context feature):可用场景特征只有评分的timestamp(时间戳)，可提取作为代表时间场景的特征

p.s. 使用python3.5+的type hint类型提示支持，便于IDE等进行类型检查和运行时支持，代码更易读
'''

# redis配置
REDIS_ENDPOINT = "66.42.66.135"
REDIS_AUTH = "chenliwei"
REDIS_PORT = 6379

# 精确到小数点后的位数
NUMBER_PRECISION = 2


# 给评分样本添加label
def addSampleLabel(ratingSamples: DataFrame) -> DataFrame:
    # 查看原生评价样本的格式
    ratingSamples.printSchema()
    ratingSamples.show(5, truncate=False)

    # 查看统计类特征:各离散评分的占比
    sampleCount = ratingSamples.count()
    ratingSamples.groupBy('rating').count().orderBy('rating') \
        .withColumn('percentage', F.col('count') / sampleCount).show()

    # 为了能适配CTR模型，这里贴的label是个二分类的1/0，以评分为衡量标准，假设3.5分以上为喜欢(label=1)，以下为不喜欢(label=0)
    return ratingSamples.withColumn('label', F.when(F.col('rating') >= 3.5, 1).otherwise(0))


# UDF: 用户定义函数
# 处理title列中包含的releaseYear信息，在ratings.csv中releaseYear在字段末尾的括号里
def extractReleaseYearUDF(title: string) -> int:
    if not title or len(title.strip()) < 6:
        return 1990  # 若title不存在或者长度小于6显然releaseYear也不存在，返回初始值1990
    else:
        return int(title.strip()[-5:-1])


# 从genres字段中提取各genre
def extractGenresUDF(genres_list: list) -> list:
    """
    Pass in a list which format like ["Action|Adventure|Sci-Fi|Thriller", "Crime|Horror|Thriller"]
    count by each genre，return genre_list in reverse order
    eg:
    (('Thriller',2),('Action',1),('Sci-Fi',1),('Horror', 1), ('Adventure',1),('Crime',1))
    return:['Thriller','Action','Sci-Fi','Horror','Adventure','Crime']
    """
    genres_dict = defaultdict(int)
    for genres in genres_list:
        for genre in genres.split('|'):
            genres_dict[genre] += 1
    # 按字典中genre及其出现次数的键值对中的value(出现次数)降序排列字典
    sortedGenres = sorted(genres_dict.items(), key=lambda x: x[1], reverse=True)
    # 抛掉提取genre时使用的出现次数value值
    return [x[0] for x in sortedGenres]


# 提取电影特征(Item Feature)
def addMovieFeatures(movieSamples: DataFrame, ratingSamplesWithLabel: DataFrame) -> DataFrame:
    # 在movieId列上等值连接，ratingSamplesWithLabel作为左表，movieSamples作为右表，
    samplesWithMovie = ratingSamplesWithLabel.join(movieSamples, on=['movieId'], how='left')
    # 从title列中提取处于其末尾括号中的releaseYear字段
    samplesWithMovie = samplesWithMovie.withColumn('releaseYear',
                                                   F.udf(extractReleaseYearUDF, IntegerType())('title')) \
        .withColumn('title', F.udf(lambda x: x.strip()[:-6].strip(), StringType())('title')) \
        .drop('title')  # DataFrame中去掉暂时无用的title字段(只需要movieId即可)
    # 从genres列中分隔以'|'符号为分界符的各genre字段
    samplesWithMovie = samplesWithMovie.withColumn('genre0', F.split(F.col('genres'), "\\|")[0]) \
        .withColumn('genre1', F.split(F.col('genres'), "\\|")[1]) \
        .withColumn('genre2', F.split(F.col('genres'), "\\|")[2])
    # 添加评分特征
    movieRatingFeatures = samplesWithMovie.groupBy('movieId').agg(F.count(F.lit(1)).alias('movieRatingCount'),
                                                                  F.format_number(F.avg(F.col('rating')),
                                                                                  NUMBER_PRECISION).alias(
                                                                      'movieAvgRating'),
                                                                  F.stddev(F.col('rating')).alias(
                                                                      'movieRatingStddev')) \
        .fillna(0) \
        .withColumn('movieRatingStddev', F.format_number(F.col('movieRatingStddev'), NUMBER_PRECISION))

    # 将samplesWithMovie和movieRatingFeatures在movieId列上等值连接，前者作为左表,后者作为右表
    return samplesWithMovie.join(movieRatingFeatures, on=['movieId'], how='left')


# 提取用户特征，主要是用户的历史行为特征，由于是历史行为，在某个行为节点上计算特征时不能引入未来信息，所以要使用滑动窗口保证不引入未来信息
def addUserFeatures(samplesWithMovieFeatures: DataFrame) -> DataFrame:
    """
    新增列详解:
    --- 以下都是**到该条评价产生时间前的**历史行为特征记录 ---
    1.userPositiveHistory: 收集该用户的积极评价并形成一个list，积极评价定义为评价分>3.5(认定为其喜欢该电影)，同时使用滑动窗口收集该评价发生时间前的历史节点，避免收集未来信息
    2.使用F.reverse将①中得到的评价序列反序，即按最新评价在前的顺序
    3.userRatedMovie[0~4]: 该用户最近评价的5部电影
    4.userRatingCount:                  用户评价总数
    5.userRatedMovieAvgReleaseYear:     用户评价过的电影的平均上映年份
    6.userRatedMovieReleaseYearStddev:  用户评价过的电影的上映年份的无偏标准差
    7.userAvgRating:                    用户平均评分
    8.userRatingStddev:                 用户评分的无偏标准差
    9.userGenres:                       用户观看过的电影的风格分类汇总
    10.userGenre[0~4]:                  用户最近5个观看的电影风格分类
    --- 以下是对DataFrame中无用信息列的修正
    1.drop:
        ①genres:                        原始电影风格分类，在历史行为特征中不具有含义，删去
        ②userGenres:                    收集到的按时间排列的最近观看电影分类的列表，已捡取前5个，原始列可删去
        ③userPositiveHistory            收集到的按时间排列的评分序列，已捡取前5个，原始列可删去
    2.filter:
        不保留用户整个历史行为中第一次的电影评价，因为在这个行为前没有历史行为，属于冷启动部分
    :param samplesWithMovieFeatures
    :return: samplesWithUserFeatures
    """
    samplesWithUserFeatures = samplesWithMovieFeatures \
        .withColumn('userPositiveHistory',
                    F.collect_list(F.when(F.col('label') == 1, F.col('movieId')).otherwise(F.lit(None))).over(
                        Window.partitionBy('userId').orderBy(F.col('timestamp')).rowsBetween(-100, -1)
                    )) \
        .withColumn('userPositiveHistory', F.reverse(F.col('userPositiveHistory'))) \
        .withColumn('userRatedMovie0', F.col('userPositiveHistory')[0]) \
        .withColumn('userRatedMovie1', F.col('userPositiveHistory')[1]) \
        .withColumn('userRatedMovie2', F.col('userPositiveHistory')[2]) \
        .withColumn('userRatedMovie3', F.col('userPositiveHistory')[3]) \
        .withColumn('userRatedMovie4', F.col('userPositiveHistory')[4]) \
        .withColumn('userRatingCount',
                    F.count(F.lit(1)).over(Window.partitionBy('userId').orderBy('timestamp').rowsBetween(-100, -1))
                    ) \
        .withColumn('userRatedMovieAvgReleaseYear',
                    F.avg(F.col('releaseYear')).over(Window.partitionBy('userId').orderBy('timestamp').rowsBetween(-100, -1))
                    .cast(IntegerType())) \
        .withColumn('userRatedMovieReleaseYearStddev', F.format_number(
                    F.stddev(F.col('releaseYear')).over(Window.partitionBy('userId').orderBy('timestamp').rowsBetween(-100, -1)),
                    NUMBER_PRECISION)) \
        .withColumn('userAvgRating', F.format_number(
                    F.avg(F.col('rating')).over(Window.partitionBy('userId').orderBy('timestamp').rowsBetween(-100, -1)),
                    NUMBER_PRECISION)) \
        .withColumn("userRatingStddev", F.format_number(
                    F.stddev(F.col("rating")).over(Window.partitionBy('userId').orderBy('timestamp').rowsBetween(-100, -1)),
                    NUMBER_PRECISION)) \
        .withColumn("userGenres", F.udf(extractGenresUDF, ArrayType(StringType()))(
                    F.collect_list(F.when(F.col('label') == 1, F.col('genres')).otherwise(F.lit(None))).over(
                        Window.partitionBy('userId').orderBy('timestamp').rowsBetween(-100, -1))
                    )) \
        .withColumn("userGenre0", F.col("userGenres")[0]) \
        .withColumn("userGenre1", F.col("userGenres")[1]) \
        .withColumn("userGenre2", F.col("userGenres")[2]) \
        .withColumn("userGenre3", F.col("userGenres")[3]) \
        .withColumn("userGenre4", F.col("userGenres")[4]) \
        .drop("genres", "userGenres", "userPositiveHistory") \
        .filter(F.col("userRatingCount") > 1)

    samplesWithUserFeatures.printSchema()
    samplesWithUserFeatures.show(10)
    samplesWithUserFeatures.filter(samplesWithMovieFeatures['userId'] == 1).orderBy(F.col('timestamp').asc()).show(
        truncate=False)
    return samplesWithUserFeatures


# 将samplesWithUserFeature: DataFrame中的用户历史行为特征提取并存储到Redis中
def extractAndSaveUserFeaturesToRedis(samplesWithUserFeature: DataFrame):
    """
    Spark实现的来自SQL的写法
    使用F中的ROW_NUMBER()和OVER(PARTITION BY COLUMN ORDER BY COLUMN)给每个分组(partition，用窗口实现，按某列分组)中的元组按给定列进行升序或降序排列并分配序号row_num
    使用Filter只保留row_num==1的元组，即按时间戳排列的最新历史行为特征，空值用空串填充
    其实就是提取DataFrame中某用户的最近的历史行为特征(最新的一次评价产生的特征)，将其存入Redis
    :param samplesWithUserFeature:
    :return:
    """
    userLatestSamples = samplesWithUserFeature \
        .withColumn('userRowNum', F.row_number().over(Window.partitionBy('userId').orderBy(F.col('timestamp').desc()))) \
        .filter(F.col('userRowNum') == 1) \
        .select("userId", "userRatedMovie0", "userRatedMovie1", "userRatedMovie2", "userRatedMovie3", "userRatedMovie4",
                "userRatingCount", "userRatedMovieAvgReleaseYear", "userRatedMovieReleaseYearStddev", "userAvgRating",
                "userRatingStddev", "userGenre0", "userGenre1", "userGenre2", "userGenre3", "userGenre4") \
        .fillna("")

    userLatestSamples.printSchema()
    userFeaturePrefix = "uf:"
    redis_client = redis.StrictRedis(host=REDIS_ENDPOINT, port="6379", db=0, password=REDIS_AUTH)
    expire_time = 60*60*24*30  # 过期时间设定为一个月 DEV.version:设定15分钟TTL，观察redis的monitor
    # 开启pipeline
    pipe = redis_client.pipeline(transaction=True)

    sampleArray: List[Row] = userLatestSamples.collect()
    print("total user features num: {}".format(len(sampleArray)))
    for sample in sampleArray:
        userKey = userFeaturePrefix + sample['userId']
        valueDict = sample.asDict()
        # 去掉valueDict中的'userId'字段
        valueDict.pop('userId', 'Unable to find userId in this Row!Sth. wired happened...')
        pipe.hset(name=userKey, mapping=valueDict)
        pipe.expire(name=userKey, time=expire_time)

    # 执行pipeline
    pipe.execute()
    redis_client.close()
    print("User features saved to redis successfully...")


if __name__ == '__main__':
    conf = SparkConf().setAppName('FeatureEngineering').setMaster('local')
    spark = SparkSession.builder.config(conf=conf).getOrCreate()
    ratingDataPath = 'E:/workspace/CinemaChainPlatform/src/main/resources/resources/dataset/ratings.csv'
    ratingSamples = spark.read.format('csv').option('header', 'true').load(ratingDataPath)
    ratingSamplesWithLabel = addSampleLabel(ratingSamples)
    ratingSamplesWithLabel.show()

    movieDataPath = 'E:/workspace/CinemaChainPlatform/src/main/resources/resources/dataset/movies.csv'
    movieSamples = spark.read.format('csv').option('header', 'true').load(movieDataPath)
    samplesWithMovieFeatures = addMovieFeatures(movieSamples, ratingSamplesWithLabel)
    samplesWithMovieFeatures.printSchema()
    samplesWithMovieFeatures.show(10, truncate=False)

    samplesWithUserFeatures = addUserFeatures(samplesWithMovieFeatures)

    # 存储到Redis中
    extractAndSaveUserFeaturesToRedis(samplesWithUserFeatures)
