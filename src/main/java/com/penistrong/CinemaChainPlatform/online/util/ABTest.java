package com.penistrong.CinemaChainPlatform.online.util;

import com.penistrong.CinemaChainPlatform.online.model.DNNmodel;

/**
 * ABTest,线上A/B测试工具类
 * 线上服务器进行A/B测试是为了验证新模型、新功能、新产品能否提升效果的主要测试方法
 * A/B测试的原理是将用户分桶后进行对照测试,要保证样本的独立性和分桶过程的无偏性
 * ①独立性:同一个用户在测试的全过程中只能被分到同一个桶中
 * ②无偏性:分桶过程中用户被分到哪个实验桶中应该是一个纯随机的过程
 * 为了随机性，一般要选取合适的Hash函数进行散列
 */
public class ABTest {
    //所有用户分隔份数
    final static int trafficSplitNumber = 5;

    //A桶用户配置
    final static DNNmodel bucketA_Model = DNNmodel.NeuralCF;
    //B桶用户配置
    final static DNNmodel bucketB_Model = DNNmodel.DIN;
    //不在A、B桶，即不参与测试的默认策略使用朴素Embedding计算
    final static DNNmodel defaultModel = DNNmodel.DefaultEmbedding;

    /**
     * 简单的哈希一下
     * 注意userId字段类型为int，调用该函数前要转为String
     * @param userId Don't forget to cast userId:int to String
     * @return 使用的深度学习推荐模型枚举值
     */
    public static DNNmodel getBucketByUserId(String userId){
        if(userId == null || userId.isEmpty())
            return defaultModel;
        if(userId.hashCode() % trafficSplitNumber == 0)         //1份分给A桶
            return bucketA_Model;
        else if(userId.hashCode() % trafficSplitNumber == 1)    //1份分给B桶
            return bucketB_Model;
        else                                                    //其余${trafficSplitNumber - 2}份采用默认
            return defaultModel;
    }
}
