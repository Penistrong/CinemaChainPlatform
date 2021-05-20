import numpy as np
import pymysql
import pandas as pd


def load_movies(conn, cursor):
    file_path = "E:/workspace/CinemaChainPlatform/src/main/resources/resources/dataset/movies.csv"

    data = pd.read_csv(file_path, engine='python')
    print(data.head(5))

    for movieId, title, genres in zip(data['movieId'], data['title'], data['genres']):
        # Extract releaseYear from raw title column
        releaseYear = title[-5:-1]
        # Extract real title
        title = title[:-7]

        valueList = [movieId, title, releaseYear, genres]
        print(valueList)

        try:
            insertSQL = "INSERT INTO movies(movieId, title, releaseYear, genres) VALUES (%s, %s, %s, %s)"
            cursor.execute(insertSQL, valueList)
            conn.commit()
        except Exception as e:
            print(e)
            conn.rollback()


def load_movie_links(conn, cursor):
    file_path = "E:/workspace/CinemaChainPlatform/src/main/resources/resources/dataset/links.csv"
    # 防止pandas读取长数字串尝试使用科学计数法，指定字段类型
    data = pd.read_csv(file_path,
                       encoding='utf-8',
                       engine='python',
                       dtype={'movieId': int, 'imdbId': object, 'tmdbId': object})
    print(data.head(5))

    # 由于MovieLens数据中不是所有的电影都同时被imdb和tmdb收录，因此存在nan值，而MySQL无法使用nan值，要转换为None
    # 数据过滤，替换 nan 值为 None
    data = data.astype(object).where(pd.notnull(data), None)

    for movieId, imdbId, tmdbId in zip(data['movieId'], data['imdbId'], data['tmdbId']):
        valueList = [imdbId, tmdbId, movieId]
        print(valueList)

        try:
            updateSQL = "UPDATE movies SET imdbId=%s, tmdbId=%s WHERE movieId=%s"
            cursor.execute(updateSQL, valueList)
            conn.commit()
        except Exception as e:
            print(e)
            conn.rollback()


# 由于用户信息杂糅在ratings.csv中，因此要进行一番操作
def load_ratings_and_users(conn, cursor):
    file_path = "E:/workspace/CinemaChainPlatform/src/main/resources/resources/dataset/ratings.csv"
    # 防止pandas读取长数字串尝试使用科学计数法，指定字段类型
    data = pd.read_csv(file_path,
                       encoding='utf-8',
                       engine='python',
                       dtype={'userId': int, 'movieId': int, 'rating': float, 'timestamp': object})
    print(data.head(5))

    # 由于MovieLens数据中不是所有的电影都同时被imdb和tmdb收录，因此存在nan值，而MySQL无法使用nan值，要转换为None
    # 数据过滤，替换 nan 值为 None
    data = data.astype(object).where(pd.notnull(data), None)

    # 存放已经入表的userId
    exist_userId = []
    # Initial Password, BcryptEncoder加密, 密码为chenliwei
    pwd = "$2a$10$Xkqvf/EJSFTAKayeT60arerLnGyLn4AagxKqSlIuZHoZPceJqhhmm"
    # assume column 'rating' as score
    for userId, movieId, score, timestamp in zip(data['userId'], data['movieId'], data['rating'], data['timestamp']):
        try:
            if userId not in exist_userId:
                userValueList = [userId, "User"+str(userId), pwd, 1]
                insertUser = "INSERT INTO users(userId, username, password, enabled) VALUES (%s, %s, %s, %s)"
                print(userValueList)
                cursor.execute(insertUser, userValueList)
                exist_userId.append(userId)

            ratingValueList = [userId, movieId, score, timestamp]
            insertRating = "INSERT INTO ratings(userId, movieId, score, timestamp) VALUES (%s, %s, %s, %s)"
            print(ratingValueList)
            cursor.execute(insertRating, ratingValueList)
        except Exception as e:
            print(e)
            conn.rollback()
    conn.commit()


if __name__ == '__main__':
    conn = pymysql.connect(user="Penistrong",
                           passwd="chenliwei",
                           db="ccp_db",
                           host="localhost",
                           port=3306,
                           charset='utf8')
    cursor = conn.cursor()
    # load_movies(conn, cursor)
    # load_movie_links(conn, cursor)
    # load_ratings_and_users(conn, cursor)

    cursor.close()
    conn.close()
