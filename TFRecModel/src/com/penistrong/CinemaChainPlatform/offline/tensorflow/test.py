import pandas as pd
from matplotlib import pyplot as plt
import numpy as np

history = {
    'Loss': [0.9192683100700378, 0.5368390679359436, 0.524445652961731, 0.5188649892807007, 0.5153046250343323],
    'Accuracy': [0.6856651306152344, 0.7298699021339417, 0.738366961479187, 0.7420907020568848, 0.7439160346984863],
    'ROC_AUC': [0.7294506430625916, 0.7992410659790039, 0.8104289174079895, 0.815310001373291, 0.8181856274604797],
    'PR_AUC': [0.7350736260414124, 0.8213751912117004, 0.8339670300483704, 0.8394820690155029, 0.8430948853492737]
}

pd.DataFrame(history).plot(figsize=(8, 5))
plt.grid(ls='dotted')
# gca is Get Current Axes, set_ylim设置y轴显示的上下限，这里是学习曲线，肯定是在0~1之间
plt.gca().set_ylim(0.5, 1)
plt.title('History')
plt.xlabel('Epoch')
plt.yticks(np.arange(0.5, 1.05, 0.1))
plt.show()
