package com.penistrong.CinemaChainPlatform.online.util;

public class Config {
    public static final String DATA_SOURCE_REDIS = "redis";
    public static final String DATA_SOURCE_FILE = "file";

    public static String EMB_DATA_SOURCE = Config.DATA_SOURCE_REDIS;
    public static boolean IS_LOAD_USER_FEATURE_FROM_REDIS = true;
    public static boolean IS_LOAD_ITEM_FEATURE_FROM_REDIS = false;

    public static boolean IS_ENABLE_AB_TEST = false;

    public static final String USER_EMBEDDING_PREFIX_IN_REDIS = "uEmb:";
    public static final String MOVIE_ITEM2VEC_EMBEDDING_PREFIX_IN_REDIS = "i2vEmb:";
    public static final String MOVIE_GRAPH_EMBEDDING_PREFIX_IN_REDIS = "graphEmb:";
    public static final String MOVIE_FEATURE_PREFIX_IN_REDIS = "mf:";
    public static final String USER_FEATURE_PREFIX_IN_REDIS = "uf:";
}
