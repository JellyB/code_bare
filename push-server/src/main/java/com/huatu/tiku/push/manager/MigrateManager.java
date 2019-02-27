package com.huatu.tiku.push.manager;

import com.alibaba.fastjson.JSONObject;
import com.huatu.tiku.push.annotation.SplitParam;
import com.huatu.tiku.push.constant.NoticePushRedisKey;
import com.huatu.tiku.push.dao.NoticeUserMapper;
import com.huatu.tiku.push.entity.NoticeUserRelation;
import com.huatu.tiku.push.enums.NoticeStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Example;

import java.sql.Timestamp;
import java.util.List;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-12-07 下午4:57
 **/
@Component
@Slf4j
public class MigrateManager {

    @Autowired
    private NoticeUserMapper noticeUserMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private NoticeViewManager noticeViewManager;

    /**
     * 关系表插入逻辑
     * @param message
     */
    public void insertRelation(String message){
        try{
            NoticeUserRelation noticeUserRelation = JSONObject.parseObject(message, NoticeUserRelation.class);
            String key = NoticePushRedisKey.getDataMigrateNoticeCreateTime();
            HashOperations hashOperations = redisTemplate.opsForHash();
            String value = String.valueOf(hashOperations.get(key, String.valueOf(noticeUserRelation.getNoticeId())));
            Timestamp timestamp = new Timestamp(Long.valueOf(value));
            noticeUserRelation.setId(null);
            noticeUserRelation.setCreateTime(timestamp);
            boolean checkExist = ((MigrateManager)AopContext.currentProxy()).checkDataExist(noticeUserRelation.getUserId(), noticeUserRelation.getNoticeId());
            if(checkExist){
                return;
            }else{
                noticeViewManager.saveOrUpdate(noticeUserRelation.getUserId(), noticeUserRelation.getNoticeId());
                ((MigrateManager)AopContext.currentProxy()).insert(noticeUserRelation.getUserId(), noticeUserRelation);
            }
        }catch (Exception e){

        }
    }

    @SplitParam
    public int insert(long userId, NoticeUserRelation noticeUserRelation){
        try{
            log.info("user.id.value:{}", userId);
            return noticeUserMapper.insertSelective(noticeUserRelation);
        }catch (Exception e){
            log.error("*******************************");
            log.error(">>>>> 消费队列插入mysql 异常！<<<<");
            log.error("*******************************");
            log.error("{}", e);
        }
        return 0;
    }


    /**
     * 检查数据是否存在
     * @param userId
     * @param noticeId
     * @return
     */
    @SplitParam
    public boolean checkDataExist(long userId, long noticeId){
        try{
            Example example = new Example(NoticeUserRelation.class);
            example.and()
                    .andEqualTo("userId", userId)
                    .andEqualTo("noticeId", noticeId)
                    .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());

            List<NoticeUserRelation> list =  noticeUserMapper.selectByExample(example);
            return CollectionUtils.isNotEmpty(list);
        }catch (Exception e){
            log.error("*******************************");
            log.error(">>>>>> 检查数据是否存在异常 <<<<<<");
            log.error("*******************************");
            log.error("{}", e);
        }
        return false;
    }
}
