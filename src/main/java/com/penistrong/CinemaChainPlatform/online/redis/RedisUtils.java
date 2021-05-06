package com.penistrong.CinemaChainPlatform.online.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类，当涉及到从Redis读写时，使用此工具类
 * 注意，此类是在SpringBoot扫描完各容器之后处理依赖后使用的，即DataManager不可直接调用，另建一个单例类
 */
@Component
public class RedisUtils {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 指定缓存失效时间
     * @param key 键
     * @param time 失效时间(秒)
     * @return boolean:true,无false,直接抛出异常
     */
    public boolean expire(String key, long time){
        if(time > 0) {
            redisTemplate.expire(key, time, TimeUnit.SECONDS);
            return true;
        }else
            throw  new RuntimeException("设置的超时时间不能小于0");
    }

    /**
     * 获取key的过期时间
     * @param key 不能为空
     * @return expire time
     */
    public long getExpire(String key){
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public boolean hasKey(String key){
        return redisTemplate.hasKey(key);
    }

    @SuppressWarnings("unchecked")
    public void delete(String... key){
        if(key != null && key.length > 0){
            if(key.length == 1)
                redisTemplate.delete(key[0]);
            else
                redisTemplate.delete((Collection<String>) CollectionUtils.arrayToList(key));
        }
    }

    /**
     * 获取符合pattern的键集合
     * @param pattern
     * @return Set<String>装入满足条件的各key
     */
    public Set<String> keys(String pattern){
        return redisTemplate.keys(pattern);
    }

    // ============================String=============================
    /**
     * 普通缓存获取
     * @param key 键
     * @return value     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }
    /**
     * 普通缓存放入     * @param key 键     * @param value 值     * @return true成功 false失败
     */
    public boolean set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
        return true;
    }
    /**
     * 普通缓存放入并设置过期时间
     * @param key 键
     * @param value 值
     * @param time 时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public boolean set(String key, Object value, long time) {
        if (time > 0) {
            redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
        } else {
            this.set(key, value);
        }
        return true;
    }
    /**
     * 递增
     * @param key 键
     * @param delta 增量(正值)
     * @return     */
    public long increment(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递增因子必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, delta);
    }
    /**
     * 递减
     * @param key 键
     * @param delta 减量(正值)
     * @return     */
    public long decrement(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递减因子必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, -delta);
    }
    // ================================Map=================================
    /**
     * HashGet
     * @param key 键 不能为null
     * @param item 项 不能为null
     * @return HashValue     */
    public Object hget(String key, String item) {
        return redisTemplate.opsForHash().get(key, item);
    }
    /**
     * 获取hashKey对应的所有键值
     * @param key 键
     * @return 对应的多个键值     */
    public Map<Object, Object> hgetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }
    /**
     * HashSet
     * * @param key 键
     * @param map 对应多个键值
     * @return true 成功 false 失败
     */
    public boolean hmset(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
        return true;
    }
    /**
     * HashSet 并设置时间
     * @param key 键
     * @param map 对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean hmset(String key, Map<String, Object> map, long time) {
        redisTemplate.opsForHash().putAll(key, map);
        if (time > 0) {
            expire(key, time);
        }
        return true;
    }
    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * @param key 键
     * @param item 项
     * @param value 值
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value) {
        redisTemplate.opsForHash().put(key, item, value);
        return true;
    }
    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * @param key 键
     * @param item 项
     * @param value 值
     * @param time 时间(秒)  注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value, long time) {
        redisTemplate.opsForHash().put(key, item, value);
        if (time > 0) {
            expire(key, time);
        }
        return true;
    }
    /**
     * 删除hash表中的值
     * @param key 键 不能为null
     * @param item 项 可以使多个 不能为null     */
    public void hdel(String key, Object... item) {
        redisTemplate.opsForHash().delete(key, item);
    }
    /**
     * 判断hash表中是否有该项的值
     * @param key 键 不能为null
     * @param item 项 不能为null
     * @return true 存在 false不存在
     */
    public boolean hHasKey(String key, String item) {
        return redisTemplate.opsForHash().hasKey(key, item);
    }
    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     * @param key 键
     * @param item 项
     * @param by 增量，正数
     * @return  新增后的hash值   */
    public double hIncrement(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, by);
    }
    /**
     * hash递减
     * @param key 键
     * @param item 项
     * @param by 减量，正数
     * @return  递减后的hash值  */
    public double hDecrement(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, -by);
    }
    // ============================set=============================
    /**
     * 根据key获取Set中的所有值
     * @param key 键
     * @return  装有各值的Set   */
    public Set<Object> sGet(String key) {
        return redisTemplate.opsForSet().members(key);
    }
    /**
     * 根据value从一个set中查询,是否存在
     * @param key 键
     * @param value 值
     * @return true 存在 false不存在
     */
    public boolean sHasKey(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }
    /**
     * 将set数据放入set缓存
     * @param key 键
     * @param values 值 可以是多个
     * @return 放入成功的个数     */
    public long sSet(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }
    /**
     * 将set数据放入缓存，同时设置过期时间
     * @param key 键
     * @param time 时间(秒)
     * @param values 值 可以是多个
     * @return 放入成功的个数     */
    public long sSetWithTime(String key, long time, Object... values) {
        final Long count = redisTemplate.opsForSet().add(key, values);
        if (time > 0)
            expire(key, time);
        return count;
    }
    /**
     * 获取set缓存的长度
     * @param key 键
     * @return 缓存长度     */
    public long sGetSetSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }
    /**
     * 移除值为value的
     * @param key 键
     * @param values 值 可以是多个
     * @return 移除成功的个数     */
    public long setRemove(String key, Object... values) {
        final Long count = redisTemplate.opsForSet().remove(key, values);
        return count;
    }
    // ===============================list=================================
    /**
     * 获取key对应的list中指定范围的内容
     * @param key 键
     * @param start 下界
     * @param end 上界  [0, -1]即取List中所有内容
     * @return  装入指定范围内容的List   */
    public List<Object> lGet(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }
    /**
     * 获取key对应的list的长度
     * @param key 键
     * @return 长度   */
    public long lGetListSize(String key) {
        return redisTemplate.opsForList().size(key);
    }
    /**
     * 通过索引 获取list中的值
     * @param key 键
     * @param index 索引 index>=0时，0为表头，1为第二个元素，以此类推。index<0时，-1，表尾，-2倒数第二个元素，以此类推。
     * @return     */
    public Object lGetIndex(String key, long index) {
        return redisTemplate.opsForList().index(key, index);
    }
    /**
     * 将一个对象放入key对应的List中
     * @param key 键
     * @param value 值
     * @return  是否成功   */
    public boolean lSet(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
        return true;
    }
    /**
     * 将一个对象放入key对应的List中，并设置过期时间
     * @param key 键
     * @param value 值
     * @param time 时间(秒)
     * @return  是否成功   */
    public boolean lSet(String key, Object value, long time) {
        redisTemplate.opsForList().rightPush(key, value);
        if (time > 0) {
            expire(key, time);
        }
        return true;
    }
    /**
     * 将一个List中的所有内容放入key对应的List中
     * @param key 键
     * @param value 值
     * @return  是否成功   */
    public boolean lSet(String key, List<Object> value) {
        redisTemplate.opsForList().rightPushAll(key, value);
        return true;
    }
    /**
     * 将一个List中的所有内容放入key对应的List中，并设置过期时间
     * @param key 键
     * @param value 值
     * @param time 时间(秒)
     * @return  是否成功   */
    public boolean lSet(String key, List<Object> value, long time) {
        redisTemplate.opsForList().rightPushAll(key, value);
        if (time > 0) {
            expire(key, time);
        }
        return true;
    }
    /**
     * 根据索引修改list中的某条数据
     * @param key 键
     * @param index 索引
     * @param value 值
     * @return 是否成功    */
    public boolean lUpdateIndex(String key, long index, Object value) {
        redisTemplate.opsForList().set(key, index, value);
        return true;
    }

}
