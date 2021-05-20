#!/usr/bin/env python
# -*- encoding: utf-8 -*-
'''
@File    :   CollaborativeFiltering.py
@Time    :   2021/03/04 11:16:30
@Author  :   Penistrong 
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   利用Spark构建协同过滤模型
'''

# here put the import lib
from pyspark import SparkConf
from pyspark.ml.evaluation import RegressionEvaluator
from pyspark.ml.recommendation import ALS
from pyspark.ml.tuning import CrossValidator, ParamGridBuilder
from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import *

'''
建立协同过滤模型，并采用矩阵分解优化稀疏矩阵的问题
先利用协同过滤建立User与Item的共现矩阵，针对共现矩阵往往特别稀疏的问题，使用矩阵分解将MxN的贡献矩阵分解为Mxk的用户向量矩阵和kxN的物品向量矩阵
即分别输入用户和物品的One-hot向量后通过隐向量层转换成用户和物品的隐向量(当做Embedding使用)，再通过点积的方式交叉生成最终预测
很显然，这种特征向量交叉的方式过于简单导致模型复杂度低，如果数据模式比较复杂往往会出现欠拟合情况，无法很好地拟合训练集数据
据此有改进版的NeuralCF(神经网络协同过滤)模型，使用多层神经网络替换点积层以解决欠拟合问题

注意：协同过滤是五年前推荐系统的主流技术，但是它不好融入用户行为之外的特征，比如场景上下文等，现在逐渐被深度学习方案取代了
这里只是学习一下传统CF的处理方法
'''


def CollaborativeFiltering(spark, sampleDataPath):
    ratingSamples = spark.read.format('csv').option('header', 'true').load(sampleDataPath) \
        .withColumn("userIdInt", F.col("userId").cast(IntegerType())) \
        .withColumn("movieIdInt", F.col("movieId").cast(IntegerType())) \
        .withColumn("ratingFloat", F.col("rating").cast(FloatType()))

    # 将训练样本使用pyspark.rdd中的randomSplit按0.8:0.2的比例随机分为训练集和测试集
    training_data, test_data = ratingSamples.randomSplit((0.8, 0.2))

    # 在训练集上建立矩阵分解模型
    '''参数详解
    regParam:L2正则的系数lambda
    maxIter:交替计算User与Item的latent factors的迭代次数
    userCol:DataFrame中用户列的名字
    itemCol:DataFrame中物品列的名字
    ratingCol:DataFrame中评分列的名字
    coldStateStrategy,冷启动策略:设定为'drop'以确保模型在预测时遇到未知user或者item时(即没有在训练集中出现过)不会返回NaN，而是直接忽略
    '''
    als = ALS(regParam=0.01, maxIter=5, userCol='userIdInt', itemCol='movieIdInt', ratingCol='ratingFloat',
              coldStartStrategy='drop')

    # 训练模型
    model = als.fit(training_data)
    # 通过在测试集上计算RMSE(Root Mean Squared Error, 均方根误差)以评估模型
    predictions = model.transform(test_data)
    # 展示ALS模型的物品隐向量和用户隐向量，可以将这两者当做Item Embedding与User Embedding进行处理
    model.itemFactors.show(10, truncate=False)
    model.userFactors.show(10, truncate=False)
    # 使用Spark的回归评估器进行评估,metricName选择rmse(Root Mean Square Error, 均方根误差)
    evaluator = RegressionEvaluator(predictionCol="prediction", labelCol="ratingFloat", metricName='rmse')
    rmse = evaluator.evaluate(predictions)
    # 打印结果
    print("RMSE = {}".format(rmse))
    # 为每个用户生成Top 10 item推荐列表(即电影推荐)
    recListForUser = model.recommendForAllUsers(10)
    # 为每部电影生成Top 10 用户推荐列表
    recListForMovie = model.recommendForAllItems(10)
    # 在给定的用户集上为集合中的每个用户生成Top 10电影推荐列表
    userSubset = ratingSamples.select(als.getUserCol()).distinct().limit(3)
    recListForUserSubset = model.recommendForUserSubset(userSubset, 10)
    # 在给定的电影集上为集合中的每个电影生成Top 10用户推荐列表
    movieSubset = ratingSamples.select(als.getItemCol()).distinct().limit(3)
    recListForMovieSubset = model.recommendForItemSubset(movieSubset, 10)
    # 显示推荐结果
    recListForUser.show(5, truncate=False)
    recListForMovie.show(5, truncate=False)
    recListForUserSubset.show(5, truncate=False)
    recListForMovieSubset.show(5, truncate=False)

    paramGrid = ParamGridBuilder().addGrid(als.regParam, [0.01]).build()
    # 使用离线评估策略的交叉验证
    # 将全部样本划分为k个大小相等的样本子集，依次遍历这k个子集，将每次遍历到的子集作为验证集，其余子集作为训练集
    # 依次进行k次模型的训练和评估，k通常取10
    # 最后将这k次评估指标的平均值作为最终评估指标
    cv = CrossValidator(estimator=als, estimatorParamMaps=paramGrid, evaluator=evaluator, numFolds=10)
    cvModel = cv.fit(test_data)
    avgMetrics = cvModel.avgMetrics


if __name__ == '__main__':
    conf = SparkConf().setAppName('collaborativeFiltering').setMaster('local')
    spark = SparkSession.builder.config(conf=conf).getOrCreate()
    sampleDataPath = 'E:/workspace/CinemaChainPlatform/src/main/resources/resources/dataset/ratings.csv'

    CollaborativeFiltering(spark, sampleDataPath=sampleDataPath)
    spark.stop()
