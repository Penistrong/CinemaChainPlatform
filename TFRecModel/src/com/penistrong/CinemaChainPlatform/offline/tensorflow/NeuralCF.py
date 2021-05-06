#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@File    :   NeuralCF.py
@Time    :   2021/05/01 14:36:20
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   NeuralCF model(Neural Collaborative Filtering)
"""

# here put the import lib
from typing import List

import tensorflow as tf
from CCPDataManager import CCPDataManager

"""
协同过滤中，它直接利用非常稀疏的用户物品共现矩阵，导致其泛化能力非常弱，遇到冷启动问题(比如几乎没有历史行为的用户)难以产生正确结果
而矩阵分解也不过是在CF的基础上对泛化能力的一种增强方法，拿到稠密的隐向量作为Embedding进行计算。但是交叉特征的方法只是简单的内积操作，难免欠拟合
2017年新加坡国立大学的学者提出NeuralCF模型，旨在用多层神经网络(Neural Network)代替简单的用户隐向量与物品隐向量进行内积的点积层(Inner Product Layer)
NeuralCF是一种改进的模型，但是其中提到的双塔模型思想十分有价值:
将模型分为用户侧与物品侧两部分，最后使用互操作层将这两部分联系起来，并生成预测得分
即在处理用户侧与物品侧时，既可以使用简单的Embedding层又可以使用复杂的神经网络
互操作层既可以使用简单的内积操作又可以使用更复杂的MLP结构，且前者更适合在线上部署模型时进行快速推断
在NeuralCF的双塔结构中，用户侧只用到了userId，物品侧只用到了itemId作为输入特征，进一步扩展可将其他特征按需放入用户侧和物品侧，使模型学习得更加全面
"""

# TODO:基于MovieLens数据集的分片得到，若要使用原始数据集这里要进行更改!
MOVIE_NUMS = 1000
USER_NUMS = 30000

train_data, test_data = CCPDataManager().get_ccp_dataset()

# Item Embedding Feature
movie_col = tf.feature_column.categorical_column_with_identity(key='movieId', num_buckets=MOVIE_NUMS + 1)
movie_emb_col = tf.feature_column.embedding_column(movie_col, 10)

# User Embeding Feature
user_col = tf.feature_column.categorical_column_with_identity(key='userId', num_buckets=USER_NUMS + 1)
user_emb_col = tf.feature_column.embedding_column(user_col, 10)

# 双塔中，用户塔和物品塔都只使用各自ID的Embedding作为唯一输入特征
inputs = {
    'movieId': tf.keras.layers.Input(name='movieId', shape=(), dtype='int32'),
    'userId': tf.keras.layers.Input(name='userId', shape=(), dtype='int32'),
}


# 第一种NeuralCF模型
# 双塔中每个塔都只使用Embedding，互操作层使用MLP
# hidden_units指互操作层的MLP各层的神经元个数(都是全连接层)
def NeuralCF_model_1(feature_inputs, item_feature_column, user_feature_column, hidden_units: List[int]):
    item_tower = tf.keras.layers.DenseFeatures(item_feature_column)(feature_inputs)
    user_tower = tf.keras.layers.DenseFeatures(user_feature_column)(feature_inputs)
    # 将双塔连接后，准备添加互操作层
    interaction_layer = tf.keras.layers.concatenate([item_tower, user_tower])
    for node_nums in hidden_units:
        interaction_layer = tf.keras.layers.Dense(node_nums, activation='relu')(interaction_layer)
    # 输出层，二分类，单神经元
    output_layer = tf.keras.layers.Dense(1, activation='sigmoid')(interaction_layer)
    # 构建模型
    neural_cf_model = tf.keras.Model(feature_inputs, output_layer)
    return neural_cf_model


# 第二种NeuralCF模型
# 每个塔同时使用Embedding+MLP,但是互操作层使用简单的内积
# 注意这里的hidden_inputs为每个塔配置相同的MLP层
def NeuralCF_model_2(feature_inputs, item_feature_column, user_feature_column, hidden_units: List[int]):
    item_tower = tf.keras.layers.DenseFeatures(item_feature_column)(feature_inputs)
    user_tower = tf.keras.layers.DenseFeatures(user_feature_column)(feature_inputs)
    for node_nums in hidden_units:
        item_tower = tf.keras.layers.Dense(node_nums, activation='relu')(item_tower)
        user_tower = tf.keras.layers.Dense(node_nums, activation='relu')(user_tower)

    # 互操作层为Inner Product
    interaction_layer = tf.keras.layers.Dot(axes=1)([item_tower, user_tower])
    output_layer = tf.keras.layers.Dense(1, activation='sigmoid')(interaction_layer)

    neural_cf_model = tf.keras.Model(feature_inputs, output_layer)
    return neural_cf_model


# 这里先使用第一种模型结构以作实验
model = NeuralCF_model_2(inputs, [movie_emb_col], [user_emb_col], [10, 10])

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
print('\n\nTest Loss {}, Test Accuracy {}, Test ROC AUC {}, Test PR AUC {}'.format(test_loss, test_accuracy,
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
    "E:/workspace/CinemaChainPlatform/src/main/resources/resources/model/NeuralCF_2",
    overwrite=True,
    include_optimizer=True,
    save_format=None,
    signatures=None,
    options=None
)
