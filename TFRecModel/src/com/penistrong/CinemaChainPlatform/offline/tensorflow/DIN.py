#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@File    :   DIN.py
@Time    :   2021/05/05 19:58:20
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   Alibaba Deep Interest Network(DIN) model
"""

# here put the import lib
import tensorflow as tf
from CCPDataManager import CCPDataManager

"""
DIN引入了注意力机制，对于用户行为中的历史物品，它通过设计一个激活单元提取提取每个物品Embedding的注意力权重
激活单元(Activation Unit)原理：
当前历史行为物品的Embedding同候选物品的Embedding使用外积计算出一个叉乘向量(一开始使用元素减,Element-wise minus,但是效果不好)
然后这两个Embedding同生成的外积向量连接为一个向量，再输入给激活单元的MLP层(由36神经元的PRelu/Dice层和一层Linear输出层组成)
最后生成代表当前历史行为物品和候选物品的关联程度的注意力权重
"""

train_data, test_data = CCPDataManager().get_ccp_dataset()

# Config
RECENT_MOVIES = 5  # 用户观看电影的历史，取最近5部: userRatedMovie[0-4]
EMBEDDING_SIZE = 10  # Embedding的维数，一般建议是取原One-hot向量总维数的四次方根
# 数据集信息
MOVIE_NUMS = 1000
USER_NUMS = 30000

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
    'userRatedMovie1': tf.keras.layers.Input(name='userRatedMovie1', shape=(), dtype='int32'),
    'userRatedMovie2': tf.keras.layers.Input(name='userRatedMovie2', shape=(), dtype='int32'),
    'userRatedMovie3': tf.keras.layers.Input(name='userRatedMovie3', shape=(), dtype='int32'),
    'userRatedMovie4': tf.keras.layers.Input(name='userRatedMovie4', shape=(), dtype='int32'),

    'userGenre0': tf.keras.layers.Input(name='userGenre0', shape=(), dtype='string'),
    'userGenre1': tf.keras.layers.Input(name='userGenre1', shape=(), dtype='string'),
    'userGenre2': tf.keras.layers.Input(name='userGenre2', shape=(), dtype='string'),
    'userGenre3': tf.keras.layers.Input(name='userGenre3', shape=(), dtype='string'),
    'userGenre4': tf.keras.layers.Input(name='userGenre4', shape=(), dtype='string'),
    'genre0': tf.keras.layers.Input(name='genre0', shape=(), dtype='string'),
    'genre1': tf.keras.layers.Input(name='genre1', shape=(), dtype='string'),
    'genre2': tf.keras.layers.Input(name='genre2', shape=(), dtype='string'),
}

# movieId Embedding
movie_col = tf.feature_column.categorical_column_with_identity(key='movieId', num_buckets=MOVIE_NUMS + 1)
movie_emb_col = tf.feature_column.embedding_column(movie_col, dimension=EMBEDDING_SIZE)

# userId Embedding
user_col = tf.feature_column.categorical_column_with_identity(key='userId', num_buckets=USER_NUMS + 1)
user_emb_col = tf.feature_column.embedding_column(user_col, dimension=EMBEDDING_SIZE)

# Genre Features vocabulary
genre_vocab = ['Film-Noir', 'Action', 'Adventure', 'Horror', 'Romance', 'War', 'Comedy', 'Western', 'Documentary',
               'Sci-Fi', 'Drama', 'Thriller',
               'Crime', 'Fantasy', 'Animation', 'IMAX', 'Mystery', 'Children', 'Musical']

# User genre Embedding,即用户历史观看风格的特征向量,这里只取最近的一个风格进行One-hot编码,再转为Dense Embedding
user_genre_col = tf.feature_column.categorical_column_with_vocabulary_list(key="userGenre0",
                                                                           vocabulary_list=genre_vocab)
user_genre_emb_col = tf.feature_column.embedding_column(user_genre_col, dimension=EMBEDDING_SIZE)

# Item genre Embedding,同样只取样本中第一顺位的进行One-hot编码,再转为Dense Embedding
item_genre_col = tf.feature_column.categorical_column_with_vocabulary_list(key="genre0", vocabulary_list=genre_vocab)
item_genre_emb_col = tf.feature_column.embedding_column(item_genre_col, dimension=EMBEDDING_SIZE)

# DIN的核心部分
# 参考DIN模型架构图，自左向右依次构建

# 1.User Profile Features:与用户信息相关的特征即U/I/C中的基础User feature
user_profile_features = [
    user_emb_col,
    user_genre_emb_col,
    tf.feature_column.numeric_column('userRatingCount'),  # 评分总数
    tf.feature_column.numeric_column('userAvgRating'),  # 平均评分
    tf.feature_column.numeric_column('userRatingStddev')  # 评分标准差
]

# 2.User Behaviors:训练样本中提取了用户近5部已评分(认为是观看过的)电影，作为用户行为
# 注意因为有的样本形成时可能该用户评价过的电影还小于5部，对于在相应列上为null值的情况使用default_value=0进行空值处理
user_behaviors = [
    tf.feature_column.numeric_column(key='userRatedMovie0', default_value=0),
    tf.feature_column.numeric_column(key='userRatedMovie1', default_value=0),
    tf.feature_column.numeric_column(key='userRatedMovie2', default_value=0),
    tf.feature_column.numeric_column(key='userRatedMovie3', default_value=0),
    tf.feature_column.numeric_column(key='userRatedMovie4', default_value=0),
]

# 3.Candidate Item:将movieId的Embedding向量作为候选物品向量
candidate_item = [movie_emb_col]

# 4.Context Features:场景上下文特征，前面学习U/I/C的时候提过似乎只有评分时间和电影上映年份可以作为场景，但这里不妨添加当前候选电影的各个信息试试
# 注意每一行训练样本其都是围绕着用户关于当前电影的历史行为形成的，包括该时间节点前的历史窗口内该电影的相关信息，因此可以作为场景信息
context_features = [
    item_genre_emb_col,
    tf.feature_column.numeric_column('releaseYear'),
    tf.feature_column.numeric_column('movieRatingCount'),
    tf.feature_column.numeric_column('movieAvgRating'),
    tf.feature_column.numeric_column('movieRatingStddev')
]

# DIN最底层的四大特征已经准备完毕，现在开始构建各层
user_profile_layer = tf.keras.layers.DenseFeatures(user_profile_features)(inputs)
user_behaviors_layer = tf.keras.layers.DenseFeatures(user_behaviors)(inputs)
candidate_item_layer = tf.keras.layers.DenseFeatures(candidate_item)(inputs)
context_features_layer = tf.keras.layers.DenseFeatures(context_features)(inputs)

# Activation Unit:构建激活单元,获取用户行为中的电影同候选电影的注意力分数

# 首先将前面封装的用户行为中的5个直接用数值型特征表示的movieId列转为Embedding层
user_behaviors_emb_layer = tf.keras.layers.Embedding(input_dim=MOVIE_NUMS + 1,
                                                     output_dim=EMBEDDING_SIZE,
                                                     mask_zero=True)(user_behaviors_layer)

# 注意激活单元在每个历史行为的Item Embedding上都覆盖了一次，且每次都需要当前候选电影的Embedding作为输入
# Repeat candidate_item_layer,repeat_times = RECENT_MOVIES(p.s. num)
repeat_candidate_item_layer = tf.keras.layers.RepeatVector(RECENT_MOVIES)(candidate_item_layer)

# 激活单元内，Inputs from User同Inputs from item即当前历史行为电影的Embedding同候选电影的Embedding进行操作
# 在旧版的DIN中，使用的是元素减，而定稿中使用的是外积
# 这里使用Element-wise minus+Element-wise product,即激活单元内部比旧版DIN还多了一个元素乘的merge单元
# 元素减
activation_subtract_layer = tf.keras.layers.Subtract()([user_behaviors_emb_layer, repeat_candidate_item_layer])
# 元素乘
activation_product_layer = tf.keras.layers.Multiply()([user_behaviors_emb_layer, repeat_candidate_item_layer])

# 将上两步得到的特征向量与原始的两大输入向量连接,默认axis=-1,在输入张量上的倒数第1个维度开始拼接
activation_all = tf.keras.layers.concatenate([user_behaviors_emb_layer,
                                              activation_subtract_layer,
                                              activation_product_layer,
                                              repeat_candidate_item_layer])

# DIN的激活单元中使用到了阿里自定义的Dice激活函数，常用的ReLU类型激活函数在零点处有阶跃，并且对于任何分布的数据其激活方式维持不变
# DIN认为此举不太合理，于是设计了一种新的激活函数Dice，能够随着数据的分布而动态改变激活策略，详见论文
# Dice实现很麻烦，不写了，直接用不带Dice的DIN(即使用PReLU)
activation_unit = tf.keras.layers.Dense(36)(activation_all)
activation_unit = tf.keras.layers.PReLU()(activation_unit)
activation_unit = tf.keras.layers.Dense(1, activation='sigmoid')(activation_unit)
# 展平为一维
activation_unit = tf.keras.layers.Flatten()(activation_unit)
activation_unit = tf.keras.layers.RepeatVector(EMBEDDING_SIZE)(activation_unit)
# 置换输入的维度，参数dims为整数元组，比如(2,1)表示将第一维和第二维置换
activation_unit = tf.keras.layers.Permute((2, 1))(activation_unit)
# 将得到的注意力权重同原历史行为物品的Embedding再进行一次元素乘，准备输入到Sum Pooling层中
activation_unit = tf.keras.layers.Multiply()([user_behaviors_emb_layer, activation_unit])

# Sum Pooling层
# axis ∈ [-rank(input_tensor), rank(input_tensor))
# 使用Lambda将自定义的函数封装为Layer对象,自定义函数使用keras.backend里的sum函数,设定axis=1沿着第一个轴相加
# 比如[[1,2,3], [3,2,1]]这个2X3的矩阵(2阶张量),the result is [1,3]+[2,2]+[3,1] = [6,6](即列向量相加)
# 如果axis=0，则是行向量相加;注意axis=-2和axis=0效果相同,axis=-1和axis=1效果相同(对该二阶张量而言)
# 注意不能使用tf.keras.layers.Add
# ValueError: A merge layer should be called on a list of inputs.
# 因为Add是把不同层作为输入而逐元素叠加
sum_pooling = tf.keras.layers.Lambda(lambda x: tf.keras.backend.sum(x, axis=1))(activation_unit)

# FC layer 全连接层
# 将Sum Pooling层同最底层的其他三个特征层①User Profile Features②Candidate Item③Context Features 全部拼接
concat_layer = tf.keras.layers.concatenate([user_profile_layer,
                                            sum_pooling,
                                            candidate_item_layer,
                                            context_features_layer])

# 组装FC Layer,DIN中使用了2层
# 第一层200个神经元，使用PReLU或者Dice
# 第二层80个神经元，使用PReLU或者Dice
fc_layer = tf.keras.layers.Dense(200)(concat_layer)
fc_layer = tf.keras.layers.PReLU()(fc_layer)
fc_layer = tf.keras.layers.Dense(80)(fc_layer)
fc_layer = tf.keras.layers.PReLU()(fc_layer)

# 输出层是二分类，DIN使用softmax(2)，这里直接用sigmoid即可
output_layer = tf.keras.layers.Dense(1, activation='sigmoid')(fc_layer)

# 构建模型
model = tf.keras.Model(inputs, output_layer)

# 编译模型
model.compile(
    loss='binary_crossentropy',
    optimizer='adam',
    metrics=['accuracy',
             tf.keras.metrics.AUC(curve='ROC', name='ROC_AUC'),
             tf.keras.metrics.AUC(curve='PR', name='PR_AUC')],
)

history = model.fit(
    train_data,
    epochs=5,
    batch_size=12  # if unspecified 'batch_size' will default to 32
)

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

'''
# 保存模型，供tf_serving使用
tf.keras.models.save_model(
    model,
    "E:/workspace/CinemaChainPlatform/src/main/resources/resources/model/DIN",
    overwrite=True,
    include_optimizer=True,
    save_format=None,
    signatures=None,
    options=None
)
'''

CCPDataManager().plot_learning_curves(history)
