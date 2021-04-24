import tensorflow as tf
from tensorflow import keras

# 载入MINST数据集
mnist = tf.keras.datasets.mnist
# 划分训练集和测试集
(x_train, y_train), (x_test, y_test) = mnist.load_data()
x_train, x_test = x_train / 255.0, x_test / 255.0

# 定义模型结构和模型参数
model = keras.Sequential([
    # 输入层28*28维矩阵
    keras.layers.Flatten(input_shape=(28, 28)),  # 将图像格式从二维数组降维至一维28*28=784像素,该层只改动数据格式
    # 128维隐层，使用relu作为激活函数
    keras.layers.Dense(128, activation='relu'),  # Dense层为密集连接或全连接的神经层,第一层有128个神经元、激活函数为ReLU的层，第二个具有10个神经元、使用softmax输出归一化的层
    # Dropout层，防止过拟合
    keras.layers.Dropout(0.2),
    # 输出层采用softmax模型，处理多分类问题
    keras.layers.Dense(10, activation='softmax')  # 该层会返回一个具有10个概率得分的数组,这些得分的总和为1。每个节点包含一个得分，表示当前图像属于上述10个类别中某一个的概率
])

# 定义模型的优化方法(初始为经典的adam，可以改为随机梯度下降SGD,自动变更学习率的AdaGrad,动量优化Momentum等)
# 损失函数使用多分类交叉熵(sparse_categorical_crossentropy)
# 评估指标选择准确度(accuracy)
model.compile(optimizer='adam',
              loss='sparse_categorical_crossentropy',
              metrics=['accuracy'])

# 训练模型，进行5轮迭代更新(epochs=5)
model.fit(x_train, y_train, epochs=5)

# 评估模型
model.evaluate(x_test,  y_test, verbose=2)
