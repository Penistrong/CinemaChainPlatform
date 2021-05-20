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
from collections import defaultdict
import random
import numpy as np
import findspark
import redis

from pyspark import SparkConf
from pyspark.mllib.feature import Word2Vec
from pyspark.ml.feature import BucketedRandomProjectionLSH
from pyspark.ml.linalg import Vectors
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


# 局部敏感哈希，使用多桶策略保证在常数时间内可以搜索到目标Item的Embedding最近邻，作为召回层生成候选列表
# 使用Spark MLlib的LSH分桶模型:BucketedRandomProjectionLSH
def embeddingLSH(spark, movieEmbMap):
    movieEmbSeq = []
    for key, embedding_list in movieEmbMap.items():
        embedding_list = [np.float64(embedding) for embedding in embedding_list]
        movieEmbSeq.append((key, Vectors.dense(embedding_list)))

    # 数据集准备，创建一个DataFrame
    movieEmbDF = spark.createDataFrame(movieEmbSeq).toDF("movieId", "emb")
    # 利用Spark MLlib 自带的分桶局部敏感哈希模型，其中numHashTables参数设定的是一个Embedding对应的桶数，即分桶函数的数量
    bucketProjectionLSH = BucketedRandomProjectionLSH(inputCol="emb", outputCol="bucketId",
                                                      bucketLength=0.1, numHashTables=3)
    bucketModel = bucketProjectionLSH.fit(movieEmbDF)
    embBucketResult = bucketModel.transform(movieEmbDF)
    print("movieId, emb, bucketId schema:")
    embBucketResult.printSchema()
    print("movieId, emb, bucketId data result:")
    embBucketResult.show(10, truncate=False)
    print("Approximately searching for 5 nearest neighbors of the given sample embedding:")
    # 给定一个Embedding向量，将其转换为Dense Vector
    sampleEmb = Vectors.dense(0.795, 0.583, 1.120, 0.850, 0.174, -0.839, -0.0633, 0.249, 0.673, -0.237)
    # 使用bucketProjectionLSH_model自带的函数寻找其最近邻
    bucketModel.approxNearestNeighbors(dataset=movieEmbDF, key=sampleEmb, numNearestNeighbors=5).show(truncate=False)


# 使用Word2vec模型训练得到Item2vec的Embedding向量
def trainItem2vec(spark, samples, embLength, embOutputPath, redisKeyPrefix, saveToRedis=False):
    # 构造Word2vec网络模型结构
    # setVectorSize设置Embedding向量的维度，即Word2vec的隐含层的神经元数目
    # setWindowSize设置在序列上进行滑动的滑动窗口大小(windowSize=2c+1)
    # setNumIterations设置训练模型时的迭代次数，类似epoch
    word2vec = Word2Vec().setVectorSize(embLength).setWindowSize(5).setNumIterations(10)
    model = word2vec.fit(samples)
    # 调用封装好的函数寻找与某个item最相似的N个其它item
    synonyms = model.findSynonyms("592", 20)  # id"592"为蝙蝠侠Batman
    for synonym, cosineSimilarity in synonyms:
        print(synonym, cosineSimilarity)

    # 准备从训练完毕后的Word2vec中取出Embedding向量并存入目标文件夹中或redis中
    if not saveToRedis:
        embOutputDir = '/'.join(embOutputPath.split('/')[:-1])  #
        if not os.path.exists(embOutputDir):
            os.mkdir(embOutputDir)
        # 使用getVectors()方法得到存放word及其向量表达(Embedding向量,W_vxn的行向量)的map<movie_id : String, Embedding : Vector>
        with open(embOutputPath, 'w') as file:
            for movie_id in model.getVectors():
                vectors = " ".join([str(emb) for emb in model.getVectors()[movie_id]])
                file.write(movie_id + ":" + vectors + "\n")
    else:
        # 将Item的Embedding写入Redis中
        redis_client = redis.StrictRedis(host='66.42.66.135', port='6379', db=0, password='chenliwei')
        expire_time = 60*60*24  # 设置缓存时间为24h
        # 使用Pipeline,否则每一次连接Redis都消耗一次RTT，过于慢了
        pipe = redis_client.pipeline(transaction=True)
        for movie_id in model.getVectors():
            vectors = " ".join([str(emb) for emb in model.getVectors()[movie_id]])
            pipe.set(redisKeyPrefix + ":" + movie_id, vectors)
            # pipe.expire(redisKeyPrefix + ":" + movie_id, expire_time)  # 还没部署到线上，暂时不设置缓存时间
        # 执行管道里的各请求
        pipe.execute()
        redis_client.close()

    return model


'''
Graph Embedding
使用Deep Walk的简单实现
依据Deep Walk原理，要准备Item之间的概率转移矩阵，即图中到达某节点后，根据其出边权重和计算的跳转到各邻接节点的概率
'''


# 根据输入的Item序列，将其中元素两两结合生成相邻的Item Pair
def generate_pair(x):
    # eg
    # input x as Seq: [50, 120, 100, 240] List[str]
    # output pairSeq: [(50, 120), (120, 100), (100, 240)] List[Tuple(str, str)]
    pairSeq = []
    previous_item = ''
    for item in x:
        if not previous_item:
            previous_item = item
        else:
            pairSeq.append((previous_item, item))
            previous_item = item
    return pairSeq


"""
生成转移概率矩阵
return transitionMatrix(转移概率矩阵) as defaultdict(dict), itemDistribution
"""


def generateTransitionMatrix(samples):
    # 使用flatMap将Item序列碎为一个个Item Pair
    pairSamples = samples.flatMap(lambda x: generate_pair(x))
    # 使用pyspark.rdd.countByValue(), 返回的是存放Item Pair及其出现的次数的dictionary, 即{(v1, v2):2, (v1, v3):4, ...}
    # Return the count of each unique value in this RDD as a dictionary of (value, count) pairs.
    pairCountMap = pairSamples.countByValue()
    # 记录总对数
    pairTotalCount = 0
    # 转移数量矩阵,双层Map类数据结构.Like Map<String, Map<String, Integer>> in JAVA
    transitionCountMatrix = defaultdict(dict)
    # 记录从某节点出发，其所有出边的权重之和
    itemCountMap = defaultdict(int)
    # 统计Item Pair的个数 {tuple(v1, v2):int(count)}
    for pair, count in pairCountMap.items():
        v1, v2 = pair
        transitionCountMatrix[v1][v2] = count
        itemCountMap[v1] += count
        pairTotalCount += count
    # 转移概率矩阵，根据前述辅助变量进行计算
    transitionMatrix = defaultdict(dict)
    itemDistribution = defaultdict(dict)
    for v1, transitionMap in transitionCountMatrix.items():
        for v2, count in transitionMap.items():
            # 从某节点跳转到其某一邻接节点的概率是该边权重占所有出边权重之和的比例
            transitionMatrix[v1][v2] = transitionCountMatrix[v1][v2] / itemCountMap[v1]
    for v, count in itemCountMap.items():
        itemDistribution[v] = count / pairTotalCount
    return transitionMatrix, itemDistribution


# 单次随机游走
def oneRandomWalk(transitionMatrix, itemDistribution, sampleLength):
    sample = []
    # 随机选择本次游走的起点
    # 产生一个[0,1)间的随机浮点数
    randomDouble = random.random()
    startItem = ""
    accumulateProbability = 0.0
    # 朴素方法找起始节点
    for item, prob in itemDistribution.items():
        accumulateProbability += prob
        if accumulateProbability >= randomDouble:
            startItem = item
            break
    sample.append(startItem)
    # 标记当前节点的变量
    cur_v = startItem
    i = 1
    while i < sampleLength:
        # 如果当前节点没有出边，即本次游走终止(即本次训练样本长度小于最大样本长度)
        if (cur_v not in itemDistribution) or (cur_v not in transitionMatrix):
            break
        next_v_probability = transitionMatrix[cur_v]
        # 故技重施
        randomDouble = random.random()
        accumulateProbability = 0.0
        for next_v, probability in next_v_probability.items():
            accumulateProbability += probability
            if accumulateProbability >= randomDouble:
                cur_v = next_v
                break
        sample.append(cur_v)
        i += 1
    return sample


# 在图中随机游走生成训练样本
def randomWalk(transitionMatrix, itemDistribution, sampleCount, sampleLength):
    samples = []
    for i in range(sampleCount):
        samples.append(oneRandomWalk(transitionMatrix, itemDistribution, sampleLength))
    return samples


def graphEmbedding(samples, spark, embLength, embOutputPath, redisKeyPrefix, saveToRedis=False):
    # 根据samples(Item Sequence)生成图及其概率转移矩阵和Item的分布情况(某Item作为源点出现在所有节点对中的次数占)
    transitionMatrix, itemDistribution = generateTransitionMatrix(samples)
    # 先定死样本数量(即游走次数)和每个训练样本的最大长度(生成的训练样本有可能提前终止)
    sampleCount = 20000
    sampleLength = 10
    rawSamples = randomWalk(transitionMatrix, itemDistribution, sampleCount, sampleLength)
    # 使用sc.parallelize分发训练样本集形成一个RDD(即创建并行集合)
    rddSamples = spark.sparkContext.parallelize(rawSamples)
    # 开始训练
    trainItem2vec(spark, rddSamples, embLength, embOutputPath, redisKeyPrefix=redisKeyPrefix, saveToRedis=saveToRedis)


if __name__ == '__main__':
    conf = SparkConf().setAppName('CTRmodel').setMaster('local')
    spark = SparkSession.builder.config(conf=conf).getOrCreate()
    file_context = 'file:///E:/workspace/CinemaChainPlatform/src/main/resources/resources'
    ratingsPath = file_context + "/dataset/ratings.csv"
    samples = processItemSequence(spark, ratingsPath)

    # 使用item2vec训练样本
    embLength = 10
    model = trainItem2vec(spark, samples, embLength,
                          embOutputPath=file_context[8:] + "/modeldata/item2vecEmb.csv",
                          redisKeyPrefix="i2vEmb", saveToRedis=True)

    # 测试局部敏感哈希搜索Embedding最近邻的效果
    embeddingLSH(spark, model.getVectors())

    # 使用Graph Embedding生成样本
    graphEmbedding(samples, spark, embLength,
                   embOutputPath=file_context[8:] + "/modeldata/itemGraphEmb.csv",
                   redisKeyPrefix="graphEmb", saveToRedis=True)
