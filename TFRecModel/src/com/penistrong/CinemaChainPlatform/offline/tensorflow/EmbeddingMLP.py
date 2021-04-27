#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@File    :   EmbeddingMLP.py
@Time    :   2021/04/27 16:54:10
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   Establish EmbeddingMLP classical DNN model
"""

# here put the import lib
import tensorflow as tf

# TODO:基于MovieLens数据集的分片得到，若要使用原始数据集这里要进行更改!
MOVIE_NUMS = 1000
USER_NUMS = 30000

training_samples_file_path = tf.keras.utils.get_file(fname="trainingSamples.csv",
                                                     origin="file:///E:/workspace/CinemaChainPlatform/src/main/resources"
                                                            "/resources/sampledata/trainingSamples.csv")

test_samples_file_path = tf.keras.utils.get_file(fname="trainingSamples.csv",
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

"""
基于微软的DeepCrossing模型结构
Feature层有类别型特征和数值型特征两种，在输入Stacking层(也叫Concatenate层)前其策略不同:
①类别型特征是经过One-hot编码后生成的特征向量，过于稀疏，要经过Embedding层转换为较为稠密的Embedding向量，注意每一种特征对应其特有的Embedding层
②数值型特征直接输入
"""

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

# 构建Embedding+MLP模型架构
preprocessing_layer = tf.keras.layers.DenseFeatures(numerical_columns + categorical_columns)

model = tf.keras.Sequential([
    # Feature+Embedding层
    preprocessing_layer,
    # Concatenate层(全连接层),没有像DeepCrossing那样使用多层残差网络,使用隐含层经常使用的ReLU作为激活函数
    tf.keras.layers.Dense(128, activation='relu'),
    tf.keras.layers.Dense(128, activation='relu'),
    # 前述根据评分对样本贴了0-1标签，即这里要解决的是一个类CTR的二分类预估，故输出层只要一个sigmoid神经元
    tf.keras.layers.Dense(1, activation='sigmoid')
])

# 编译模型，设置损失函数、优化器、评价指标(这里设置为准确度,ROC,PR)
model.compile(
    loss="binary_crossentropy",
    optimizer='adam',
    metrics=['accuracy', tf.keras.metrics.AUC(curve='ROC'), tf.keras.metrics.AUC(curve='PR')]
)

# 训练模型
model.fit(train_data, epochs=5)

# 模型概览
model.summary()

# 使用测试集评估模型
test_loss, test_accuracy, test_roc_auc, test_pr_auc = model.evaluate(test_data)
print('\n\nTest Loss : {}\nTest Accuracy : {}\nTest ROC AUC : {}\nTest PR AUC : {}\n'.format(test_loss, test_accuracy,
                                                                                             test_roc_auc, test_pr_auc))

# 进行预测
predictions = model.predict(test_data)
for prediction, goodRating in zip(predictions[:12], list(test_data)[0][1][:12]):
    print("Predicted good rating: {:.2%}".format(prediction[0]),
          " | Actual rating label: ",
          ("Good Rating" if bool(goodRating) else "Bad Rating"))
