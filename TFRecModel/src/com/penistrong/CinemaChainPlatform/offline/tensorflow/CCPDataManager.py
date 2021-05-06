#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@File    :   CCPDataManager.py
@Time    :   2021/05/01 14:36:20
@Author  :   Penistrong
@Version :   1.0
@Contact :   770560618@qq.com
@Desc    :   load dataset and do some pre-processing work
"""

import tensorflow as tf


class CCPDataManager:

    def __init__(self):
        self.training_samples_file_path = tf.keras.utils.get_file(
            fname="trainingSamples.csv",
            origin="file:///E:/workspace/CinemaChainPlatform/src/main/resources"
                   "/resources/sampledata/trainingSamples.csv")
        self.test_samples_file_path = tf.keras.utils.get_file(
            fname="testSamples.csv",
            origin="file:///E:/workspace/CinemaChainPlatform/src/main/resources"
                   "/resources/sampledata/testSamples.csv")

    def load_dataset(self, file_path):
        dataset = tf.data.experimental.make_csv_dataset(
            file_path,
            batch_size=12,
            label_name='label',
            na_value="0",
            num_epochs=1,
            ignore_errors=True
        )
        return dataset

    def get_ccp_dataset(self):
        train_data = self.load_dataset(self.training_samples_file_path)
        test_data = self.load_dataset(self.test_samples_file_path)
        return train_data, test_data


