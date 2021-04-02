import sys
from operator import add
import findspark
findspark.init()
from pyspark import SparkContext


if __name__ == "__main__":
    sc = SparkContext(appName="PythonWordCount")
    lines = sc.textFile('resources/words.txt')
    counts = lines.flatMap(lambda x: x.split(' ')) \
                  .map(lambda x: (x, 1)) \
                  .reduceByKey(add)
    output = counts.collect()
    for (word, count) in output:
        print ("%s: %i" % (word, count))

    sc.stop()