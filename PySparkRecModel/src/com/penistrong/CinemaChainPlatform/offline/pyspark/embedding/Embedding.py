#!/usr/bin/env python
# -*- encoding: utf-8 -*-
'''
@File    :   FeatureEngineering.py
@Time    :   2021/04/08 19:56:55
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   使用Spark提取数据集中的相关Embedding特征向量
'''

# here put the import lib
import os

import findspark
from pyspark import SparkConf
from pyspark.mllib.feature import Word2Vec
from pyspark.sql.types import ArrayType, StringType
from pyspark.sql import SparkSession
from pyspark.sql.functions import *
from pyspark.sql import functions as F

findspark.init()


def sortF(movie_list, timestamp_list):
    """
    Sort by timestamp then return the corresponding movie sequence
    :param movie_list: [1,2,3]
    :param timestamp_list: [123456,12345,10000]
    :return: [3,2,1]
    """
    # 存放(movie, timestamp)形式的tuple列表
    pairs = []
    for m, t in zip(movie_list, timestamp_list):
        pairs.append((m, t))
    # 根据timestamp排序，使用sorted()，排序键选择tuple中的第二个元素，即x[1]
    # 默认ASC升序排序，也可以追加参数reverse=true以降序排序
    pairs = sorted(pairs, key=lambda x: x[1])
    # 根据时间排序后，只返回电影id的序列
    return [x[0] for x in pairs]


# 根据Item2vec的思想，将电影评价转换为Item Sequence，尔后交给Word2vec模型进行训练
# Item2vec要学习到物品之间的相似性，根据应用上下文，这里仅过滤评分较高的电影，以学习他们的相似性
def processItemSequence(spark, rawSampleDataPath):
    # rating data from rawSampleDataPath
    ratingSamples = spark.read.format("csv").option("header", "true").load(rawSampleDataPath)
    # ratingSamples.show(5)
    # ratingSamples.printSchema()
    # udf:User defined function，实现一个用户定义的操作函数，用于之后Spark处理数据集时排序元数据使用
    sortUdf = udf(sortF, ArrayType(StringType()))
    # pyspark.sql，类sql写法
    # 按照用户id分组，过滤掉3.5/5分以下的电影，并在聚集函数中使用UDF对同一用户组的电影评价按时间进行排序(聚集函数默认会在DataFrame中新增一列)
    # 返回的是排序好的movieIds(顺序存放movieId的列表，在DataFrame中新增一列，名为movieIds)
    # 最后使用withColumn新增一列，列名为movieIdStr，其内容为取movieIds列中列表的各id分量以空格为分界符连接为一个String
    userSeq = ratingSamples \
        .where(F.col("rating") >= 3.5) \
        .groupBy("userId") \
        .agg(sortUdf(F.collect_list("movieId"), F.collect_list("timestamp")).alias('movieIds')) \
        .withColumn("movieIdStr", array_join(F.col("movieIds"), " "))

    userSeq.show()
    # rdd is Resilient Distributed Dataset
    return userSeq.select("movieIdStr").rdd.map(lambda x: x[0].split(' '))


# 使用Word2vec模型训练得到Item2vec的Embedding向量
def trainItem2vec(spark, samples, embLength, embOutputPath, redisKeyPrefix, saveToRedis=False):
    # 构造Word2vec网络模型结构
    # setVectorSize设置Embedding向量的维度，即Word2vec的隐含层的神经元数目
    # setWindowSize设置在序列上进行滑动的滑动窗口大小(windowSize=2c+1)
    # setNumIterations设置训练模型时的迭代次数，类似epoch
    word2vec = Word2Vec().setVectorSize(embLength).setWindowSize(5).setNumIterations(10)
    model = word2vec.fit(samples)
    # 调用封装好的函数寻找与某个item最相似的N个其它item
    # 这里是用余弦相似度计算的相似性，其它相似性计算方法见我的另一篇博文
    synonyms = model.findSynonyms("592", 20)  # id"592"为蝙蝠侠Batman
    for synonym, cosineSimilarity in synonyms:
        print(synonym, cosineSimilarity)
    # 准备从训练完毕后的Word2vec中取出Embedding向量并存入目标文件夹中或redis中
    embOutputDir = '/'.join(embOutputPath.split('/')[:-1])  #
    if not os.path.exists(embOutputDir):
        os.mkdir(embOutputDir)
    # 使用getVectors()方法得到存放word及其向量表达(Embedding向量,W_vxn的行向量)的map<movie_id : String, Embedding : Vector>
    with open(embOutputPath, 'w') as file:
        for movie_id in model.getVectors():
            vectors = " ".join([str(emb) for emb in model.getVectors()[movie_id]])
            file.write(movie_id + ":" + vectors + "\n")
    # TODO: embeddingLSH(spark, model.getVectors())
    return model


if __name__ == '__main__':
    conf = SparkConf().setAppName('CTRmodel').setMaster('local')
    spark = SparkSession.builder.config(conf=conf).getOrCreate()
    file_context = 'file:///E:/workspace/CinemaChainPlatform/src/main/resources/resources'
    ratingsPath = file_context + "/dataset/ratings.csv"
    samples = processItemSequence(spark, ratingsPath)
