#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@File    :   WideNDeep.py
@Time    :   2021/04/30 18:54:10
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   Google Wide&Deep model
"""

# here put the import lib
import tensorflow as tf

"""
Google的Wide&Deep模型，Wide部分负责模型的记忆能力，Deep部分负责模型的泛化能力
①记忆能力:模型直接学习历史数据中物品或特征的共现频率，并将其直接作为推荐依据的能力
②泛化能力:模型对于新样本及从未出现过的特征组合的预测能力,也是DNN模型在推荐系统中主要负责的部分
"""

# 以下数据处理部分(包含类别型特征和数值型特征)与EmbeddingMLP中如出一辙
# TODO:基于MovieLens数据集的分片得到，若要使用原始数据集这里要进行更改!
MOVIE_NUMS = 1000
USER_NUMS = 30000

training_samples_file_path = tf.keras.utils.get_file(fname="trainingSamples.csv",
                                                     origin="file:///E:/workspace/CinemaChainPlatform/src/main/resources"
                                                            "/resources/sampledata/trainingSamples.csv")

test_samples_file_path = tf.keras.utils.get_file(fname="testSamples.csv",
                                                 origin="file:///E:/workspace/CinemaChainPlatform/src/main/resources"
                                                        "/resources/sampledata/testSamples.csv")


# load samples.csv as tf dataset
def load_dataset(dataset_path):
    return tf.data.experimental.make_csv_dataset(
        file_pattern=dataset_path,
        batch_size=12,
        label_name='label',
        na_value="0",
        num_epochs=1,
        ignore_errors=True
    )


# Already used Spark to split train_data and test_data based on the original dataset
train_data = load_dataset(training_samples_file_path)
test_data = load_dataset(test_samples_file_path)

# genre features vocabulary.Use Spark to solve with rawSamples to conclude all genres into genre_vocab
genre_vocab = ['Film-Noir', 'Action', 'Adventure', 'Horror', 'Romance', 'War', 'Comedy', 'Western', 'Documentary',
               'Sci-Fi', 'Drama', 'Thriller',
               'Crime', 'Fantasy', 'Animation', 'IMAX', 'Mystery', 'Children', 'Musical']

GENRE_FEATURE = {
    'userGenre0': genre_vocab,
    'userGenre1': genre_vocab,
    'userGenre2': genre_vocab,
    'userGenre3': genre_vocab,
    'userGenre4': genre_vocab,
    # Following genre[X] as movieGenre[X]
    'genre0': genre_vocab,
    'genre1': genre_vocab,
    'genre2': genre_vocab
}

# 类别型特征:userId, movieId, userGenre[0~4], movieGenre[0~2]
categorical_columns = []
# 处理电影风格类别(包括电影的和用户的)
for feature, vocab in GENRE_FEATURE.items():
    # 由于各Genre都是genres集合中的某个元素，这里使用词表将其转化为One-hot编码的类别型特征
    categorical_column = tf.feature_column.categorical_column_with_vocabulary_list(
        key=feature,
        vocabulary_list=vocab
    )
    # 使用tf.feature_column.embedding_column将One-hot特征转换为Embedding向量，维数设置为10
    embedding_column = tf.feature_column.embedding_column(
        categorical_column=categorical_column,
        dimension=10
    )
    categorical_columns.append(embedding_column)

# 处理movieId，这里没有词表，直接使用tf.feature_column.categorical_column_with_identity转换为One-hot型(确实特别稀疏)
# 注意num_buckets限定了key的范围,左闭右开,[0, num_buckets)
movie_col = tf.feature_column.categorical_column_with_identity(key='movieId', num_buckets=MOVIE_NUMS + 1)
movie_emb_col = tf.feature_column.embedding_column(movie_col, 10)
categorical_columns.append(movie_emb_col)

# 处理userId，原理同上
user_col = tf.feature_column.categorical_column_with_identity(key='userId', num_buckets=USER_NUMS + 1)
user_emb_col = tf.feature_column.embedding_column(user_col, 10)
categorical_columns.append(user_emb_col)

# 数值型特征，直接输入，无需经过Embedding层
numerical_columns = [tf.feature_column.numeric_column('releaseYear'),
                     tf.feature_column.numeric_column('movieRatingCount'),
                     tf.feature_column.numeric_column('movieAvgRating'),
                     tf.feature_column.numeric_column('movieRatingStddev'),
                     tf.feature_column.numeric_column('userRatingCount'),
                     tf.feature_column.numeric_column('userAvgRating'),
                     tf.feature_column.numeric_column('userRatingStddev')]

# 数据处理部分的Extra处(同EmbeddingMLP中使用的基础数据处理)
# TODO:针对业务场景，设计相关规则，形成交叉特征
# 将当前评价电影和用户历史最新评价的电影组成交叉特征，目的是让模型记住“喜欢电影A的用户也会喜欢电影B”的效果
rated_movie = tf.feature_column.categorical_column_with_identity(key='userRatedMovie0', num_buckets=MOVIE_NUMS+1)
crossed_feature = tf.feature_column.indicator_column(tf.feature_column.crossed_column([movie_col, rated_movie], 10000))

# 不同于Sequential Model，Wide&Deep的Deep和Wide部分都要用到输入的某些或全部特征，所以要定义输入列
inputs = {
    'movieAvgRating': tf.keras.layers.Input(name='movieAvgRating', shape=(), dtype='float32'),
    'movieRatingStddev': tf.keras.layers.Input(name='movieRatingStddev', shape=(), dtype='float32'),
    'movieRatingCount': tf.keras.layers.Input(name='movieRatingCount', shape=(), dtype='int32'),
    'userAvgRating': tf.keras.layers.Input(name='userAvgRating', shape=(), dtype='float32'),
    'userRatingStddev': tf.keras.layers.Input(name='userRatingStddev', shape=(), dtype='float32'),
    'userRatingCount': tf.keras.layers.Input(name='userRatingCount', shape=(), dtype='int32'),
    'releaseYear': tf.keras.layers.Input(name='releaseYear', shape=(), dtype='int32'),

    'movieId': tf.keras.layers.Input(name='movieId', shape=(), dtype='int32'),
    'userId': tf.keras.layers.Input(name='userId', shape=(), dtype='int32'),
    'userRatedMovie0': tf.keras.layers.Input(name='userRatedMovie0', shape=(), dtype='int32'),

    'userGenre0': tf.keras.layers.Input(name='userGenre0', shape=(), dtype='string'),
    'userGenre1': tf.keras.layers.Input(name='userGenre1', shape=(), dtype='string'),
    'userGenre2': tf.keras.layers.Input(name='userGenre2', shape=(), dtype='string'),
    'userGenre3': tf.keras.layers.Input(name='userGenre3', shape=(), dtype='string'),
    'userGenre4': tf.keras.layers.Input(name='userGenre4', shape=(), dtype='string'),
    'genre0': tf.keras.layers.Input(name='genre0', shape=(), dtype='string'),
    'genre1': tf.keras.layers.Input(name='genre1', shape=(), dtype='string'),
    'genre2': tf.keras.layers.Input(name='genre2', shape=(), dtype='string'),
}

# Wide&Deep model architecture
# Part 'Deep':沿用EmbeddingMLP的模型结构
deep = tf.keras.layers.DenseFeatures(numerical_columns + categorical_columns)(inputs)
# 注意Google自己的模型实现中，一共三层全连接层，分别为1024,512,256，这里用的是EmbeddingMLP的
deep = tf.keras.layers.Dense(128, activation='relu')(deep)
deep = tf.keras.layers.Dense(128, activation='relu')(deep)

# Part 'Wide':交叉特征已经求出(可以视为已经过交叉积变换层)直接作为单一一层输入，并准备连接至LogLoss层
wide = tf.keras.layers.DenseFeatures(crossed_feature)(inputs)

# 将Wide部分和Deep部分连接到一起
both = tf.keras.layers.concatenate([deep, wide])

# 输出层，使用一个sigmoid神经元，老样子二分类
output = tf.keras.layers.Dense(1, activation='sigmoid')(both)

model = tf.keras.Model(inputs, output)

model.compile(
    loss='binary_crossentropy',
    optimizer='adam',
    metrics=['accuracy', tf.keras.metrics.AUC(curve='ROC'), tf.keras.metrics.AUC(curve='PR')])

model.fit(train_data, epochs=5)

test_loss, test_accuracy, test_roc_auc, test_pr_auc = model.evaluate(test_data)
print('\n\nTest Loss : {}\nTest Accuracy : {}\nTest ROC AUC : {}\nTest PR AUC : {}\n'.format(test_loss, test_accuracy,
                                                                                             test_roc_auc, test_pr_auc))

# 进行预测
predictions = model.predict(test_data)
for prediction, goodRating in zip(predictions[:12], list(test_data)[0][1][:12]):
    print("Predicted good rating: {:.2%}".format(prediction[0]),
          " | Actual rating label: ",
          ("Good Rating" if bool(goodRating) else "Bad Rating"))
