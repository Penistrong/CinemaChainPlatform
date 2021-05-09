#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@File    :   DeepFM.py
@Time    :   2021/05/05 19:58:20
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   HuaWei DeepFM model
"""

# here put the import lib
import tensorflow as tf
from CCPDataManager import CCPDataManager

"""
DeepFM利用了Factorization Machine的架构，将Wide&Deep模型中的Wide部分(就是输入层直接连到输出层的部分)替换为FM部分
加强了浅层网络部分特征组合的能力。Deep部分仍然是靠MLP进行所有特征的深层处理。
FM和DeepFM中的特征交叉方式只有加法和内积两种简单形式，要思考别的特征交叉操作
NFM(Neural Factorization Machine)使用了Bi-Interaction Pooling Layer(两两特征交叉池化层)，其具体操作为元素积(Element-wise Product)
具体操作为两个长度相同的向量对应维相乘得到元素积向量。得到交叉特征向量后，该层不使用Concatenate操作将其连接起来，而是采用求和的池化操作进行叠加
"""

# TODO:基于MovieLens数据集的分片得到，若要使用原始数据集这里要进行更改!
MOVIE_NUMS = 1000
USER_NUMS = 30000

train_data, test_data = CCPDataManager().get_ccp_dataset()

# 定义模型输入列
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

# Sparse Feature transform to Dense Embedding
# movieId Embedding Feature
movie_col = tf.feature_column.categorical_column_with_identity(key='movieId', num_buckets=MOVIE_NUMS+1)
movie_emb_col = tf.feature_column.embedding_column(movie_col, 10)
movie_ind_col = tf.feature_column.indicator_column(movie_col)

# userId Embedding Feature
user_col = tf.feature_column.categorical_column_with_identity(key='userId', num_buckets=USER_NUMS+1)
user_emb_col = tf.feature_column.embedding_column(user_col, 10)
user_ind_col = tf.feature_column.indicator_column(user_col)

# genre features vocabulary.Use Spark to solve with rawSamples to conclude all genres into genre_vocab
genre_vocab = ['Film-Noir', 'Action', 'Adventure', 'Horror', 'Romance', 'War', 'Comedy', 'Western', 'Documentary',
               'Sci-Fi', 'Drama', 'Thriller',
               'Crime', 'Fantasy', 'Animation', 'IMAX', 'Mystery', 'Children', 'Musical']

# User genre Embedding Feature
user_genre_col = tf.feature_column.categorical_column_with_vocabulary_list(key="userGenre0", vocabulary_list=genre_vocab)
user_genre_emb_col = tf.feature_column.embedding_column(user_genre_col, 10)
user_genre_ind_col = tf.feature_column.indicator_column(user_genre_col)

# Item genre Embedding Feature
item_genre_col = tf.feature_column.categorical_column_with_vocabulary_list(key="genre0", vocabulary_list=genre_vocab)
item_genre_emb_col = tf.feature_column.embedding_column(item_genre_col, 10)
item_genre_ind_col = tf.feature_column.indicator_column(item_genre_col)

# FM first-order term columns: without Embedding and concatenate to the output layer directly
# FM的一阶特征项，直接输入原始One-hot特征，不作处理
FM_first_order_columns = [movie_ind_col, user_ind_col, user_genre_ind_col, item_genre_ind_col]

# FM层的一阶特征层
fm_first_order_layer = tf.keras.layers.DenseFeatures(FM_first_order_columns)(inputs)

# 数值型特征和前述处理得到的稠密Embedding特征组成Deep部分的特征向量
deep_feature_columns = [tf.feature_column.numeric_column('releaseYear'),
                        tf.feature_column.numeric_column('movieRatingCount'),
                        tf.feature_column.numeric_column('movieAvgRating'),
                        tf.feature_column.numeric_column('movieRatingStddev'),
                        tf.feature_column.numeric_column('userRatingCount'),
                        tf.feature_column.numeric_column('userAvgRating'),
                        tf.feature_column.numeric_column('userRatingStddev'),
                        movie_emb_col,
                        user_emb_col]

item_emb_layer = tf.keras.layers.DenseFeatures([movie_emb_col])(inputs)
user_emb_layer = tf.keras.layers.DenseFeatures([user_emb_col])(inputs)
item_genre_emb_layer = tf.keras.layers.DenseFeatures([item_genre_emb_col])(inputs)
user_genre_emb_layer = tf.keras.layers.DenseFeatures([user_genre_emb_col])(inputs)

# FM部分，针对性地交叉不同的类别型特征对应的Embedding
# 用户Embedding同电影Embedding交叉
product_layer_item_user = tf.keras.layers.Dot(axes=1)([item_emb_layer, user_emb_layer])
# 用户爱看风格Embedding同电影风格Embedding交叉
product_layer_item_genre_user_genre = tf.keras.layers.Dot(axes=1)([item_genre_emb_layer, user_genre_emb_layer])
# 上述两类两两交叉
product_layer_item_genre_user = tf.keras.layers.Dot(axes=1)([item_genre_emb_layer, user_emb_layer])
product_layer_item_user_genre = tf.keras.layers.Dot(axes=1)([item_emb_layer, user_genre_emb_layer])

# Deep部分，仍然使用MLP交叉所有输入的特征
deep = tf.keras.layers.DenseFeatures(deep_feature_columns)(inputs)
# 两层64神经元的全连接层作为MLP
deep = tf.keras.layers.Dense(64, activation='relu')(deep)
deep = tf.keras.layers.Dense(64, activation='relu')(deep)

# 将FM部分和Deep部分连接,注意FM各部分是散开的
concat_layer = tf.keras.layers.concatenate([
    # FM part
    fm_first_order_layer,
    product_layer_item_user,
    product_layer_item_genre_user_genre,
    product_layer_item_genre_user,
    product_layer_item_user_genre,
    # Deep part
    deep
], axis=1)

# 二分类输出层
output_layer = tf.keras.layers.Dense(1, activation='sigmoid')(concat_layer)

model = tf.keras.Model(inputs, output_layer)

# compile the model, set loss function, optimizer and evaluation metrics
model.compile(
    loss='binary_crossentropy',
    optimizer='adam',
    metrics=['accuracy', tf.keras.metrics.AUC(curve='ROC'), tf.keras.metrics.AUC(curve='PR')])

# train the model
model.fit(train_data, epochs=5)

model.summary()

# evaluate the model
test_loss, test_accuracy, test_roc_auc, test_pr_auc = model.evaluate(test_data)
print('\n\nTest Loss : {}\nTest Accuracy : {}\nTest ROC AUC : {}\nTest PR AUC : {}\n'.format(test_loss, test_accuracy,
                                                                                             test_roc_auc, test_pr_auc))

# print some predict results
predictions = model.predict(test_data)
for prediction, goodRating in zip(predictions[:12], list(test_data)[0][1][:12]):
    print("Predicted good rating: {:.2%}".format(prediction[0]),
          " | Actual rating label: ",
          ("Good Rating" if bool(goodRating) else "Bad Rating"))

# 保存模型，供tf_serving使用
tf.keras.models.save_model(
    model,
    "E:/workspace/CinemaChainPlatform/src/main/resources/resources/model/DeepFM",
    overwrite=True,
    include_optimizer=True,
    save_format=None,
    signatures=None,
    options=None
)