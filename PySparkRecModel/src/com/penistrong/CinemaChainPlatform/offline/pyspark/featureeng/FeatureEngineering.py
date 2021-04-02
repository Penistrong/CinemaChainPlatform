#!/usr/bin/env python
# -*- encoding: utf-8 -*-
'''
@File    :   FeatureEngineering.py
@Time    :   2021/03/05 10:51:55
@Author  :   Penistrong 
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   使用Spark进行分布式处理数据集,注意shuffle操作需要在不同计算节点间进行数据交换，非常消耗资源应尽量避免。此为DEMO，用以测试Spark提取数据集的特征
'''

# here put the import lib
# 不使用findspark找到自己本地安装的完全版spark时，使用pyspark自身在进行collect时会出现Python worker failed to connect back.原因未知
from pyspark import SparkConf
from pyspark.ml import Pipeline
from pyspark.ml.feature import OneHotEncoder, StringIndexer, QuantileDiscretizer, MinMaxScaler, StringIndexerModel
from pyspark.ml.linalg import VectorUDT, Vectors
from pyspark.sql import SparkSession
from pyspark.sql.functions import *
from pyspark.sql.types import *
from pyspark.sql import functions as F

import findspark
findspark.init()

# 使用One-hot编码将类别、ID型特征转换为特征向量
# 独热编码，指某个特征是排他的
def oneHotEncoderExample(movieSamples):
    samplesWithIdNumber = movieSamples.withColumn("movieIdNumber", F.col("movieId").cast(IntegerType()))
    encoder = OneHotEncoder(inputCols=["movieIdNumber"], outputCols=['movieIdVector'], dropLast=False)
    #先fit进行预处理，尔后使用transform将原始特征转换为One-hot特征
    oneHotEncoderSamples = encoder.fit(samplesWithIdNumber).transform(samplesWithIdNumber)
    oneHotEncoderSamples.printSchema()
    oneHotEncoderSamples.show(10)

# 将数组转换为向量
def array2vec(genreIndexes, indexSize):
    genreIndexes.sort()
    fill_list = [1.0 for _ in range(len(genreIndexes))]
    return Vectors.sparse(indexSize, genreIndexes, fill_list)

# 使用Multi-hot编码，对物品生成多个标签
# 对movies.csv数据集中的电影分类，刚好使用多热编码，因为每个电影有多种分类
def multiHotEncoderExample(movieSamples):
    samplesWithGenres = movieSamples.select("movieId", "title", explode(split(F.col("genres"), "\\|").cast(ArrayType(StringType()))).alias("genre"))
    genreIndexer = StringIndexer(inputCol="genre", outputCol="genreIndex")
    StringIndexerModel = genreIndexer.fit(samplesWithGenres)
    genreIndexSamples = StringIndexerModel.transform(samplesWithGenres).withColumn("genreIndexInt", F.col("genreIndex").cast(IntegerType()))

    indexSize = genreIndexSamples.agg(max(F.col("genreIndexInt"))).head()[0] + 1
    processedSamples = genreIndexSamples.groupBy("movieId").agg(F.collect_list("genreIndexInt").alias('genreIndexes')).withColumn("IndexSize", F.lit(indexSize))
    finalSample = processedSamples.withColumn("vector", udf(array2vec, VectorUDT())(F.col("genreIndexes"), F.col("indexSize")))

    finalSample.printSchema()
    finalSample.show(10)

def ratingFeatures(ratingSamples):
    ratingSamples.printSchema()
    ratingSamples.show()
    # 计算电影平均评分和评分人数
    movieFeatures = ratingSamples.groupBy('movieId').agg(F.count(F.lit(1)).alias('ratingCount'),
                                                         F.avg("rating").alias('avgRating'),
                                                         F.variance('rating').alias('ratingVar')).withColumn('avgRatingVec', udf(lambda x: Vectors.dense(x), VectorUDT())('avgRating'))
    movieFeatures.show(10)
    # Bucketing,分100个桶
    ratingCountDiscretizer = QuantileDiscretizer(numBuckets=100, inputCol="ratingCount", outputCol="ratingCountBucket")
    # Normalization
    ratingScaler = MinMaxScaler(inputCol="avgRatingVec", outputCol="scaleAvgRating")
    # 创建一个pipeline，依次执行两个特征处理的过程
    pipelineStage = [ratingCountDiscretizer, ratingScaler]
    featurePipeline = Pipeline(stages=pipelineStage)
    movieProcessedFeatures = featurePipeline.fit(movieFeatures).transform(movieFeatures)
    movieProcessedFeatures.show(10)

# 测试各特征处理是否有效
if __name__ == '__main__':
    conf = SparkConf().setAppName('featureEngineering').setMaster('local')
    spark = SparkSession.builder.config(conf = conf).getOrCreate()
    file_context = 'file:///E:/workspace/CinemaChainPlatform/src/main/resources/resources'
    movieResourcesPath = file_context + "/dataset/movies.csv"
    movieSamples = spark.read.format('csv').option('header', 'true').load(movieResourcesPath)
    print("Raw Movie Samples:")
    movieSamples.show(10)
    movieSamples.printSchema()

    print("OneHotEncoder Example:")
    oneHotEncoderExample(movieSamples)

    print("MultiHotEncoder Example:")
    multiHotEncoderExample(movieSamples)

    ratingsResourcePath = file_context + "/dataset/ratings.csv"
    ratingSamples = spark.read.format('csv').option('header', 'true').load(ratingsResourcePath)
    ratingFeatures(ratingSamples)