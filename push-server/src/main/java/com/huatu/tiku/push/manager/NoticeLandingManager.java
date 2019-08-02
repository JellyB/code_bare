package com.huatu.tiku.push.manager;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.huatu.common.exception.BizException;
import com.huatu.tiku.push.annotation.SplitParam;
import com.huatu.tiku.push.constant.NoticePushErrors;
import com.huatu.tiku.push.constant.NoticePushRedisKey;
import com.huatu.tiku.push.dao.NoticeEntityMapper;
import com.huatu.tiku.push.dao.NoticeUserMapper;
import com.huatu.tiku.push.entity.NoticeEntity;
import com.huatu.tiku.push.entity.NoticeUserRelation;
import com.huatu.tiku.push.enums.NoticeStatusEnum;
import com.huatu.tiku.push.request.NoticeReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-14 上午10:43
 **/

@Slf4j
@Component
public class NoticeLandingManager {

    @Autowired
    private NoticeEntityMapper noticeEntityMapper;

    @Autowired
    private NoticeUserMapper noticeUserMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private NoticeViewManager noticeViewManager;


    /**
     * 批量新增 notice entity relation
     *
     * @param noticeReqs
     * @return
     */
    @Transactional(rollbackFor = BizException.class)
    public int insertBatch(List<NoticeReq> noticeReqs) {
        AtomicInteger count = new AtomicInteger(0);
        noticeReqs.forEach(noticeReq -> {
            insert(noticeReq);
            count.incrementAndGet();
        });

        return count.get();
    }


    /**
     * 保存一条noticeEntity
     *
     * @param noticeReq
     * @return
     * @throws BizException
     */
    public long insertNoticeEntity(NoticeReq noticeReq) throws BizException {
        final long noticeId;
        try {
            NoticeEntity noticeEntity = NoticeEntity.builder()
                    .type(noticeReq.getType())
                    .detailType(noticeReq.getDetailType())
                    .title(noticeReq.getTitle())
                    .text(noticeReq.getText4Data())
                    .custom(JSONObject.toJSON(noticeReq.getCustom()).toString())
                    .updateTime(new Timestamp(System.currentTimeMillis()))
                    .createTime(new Timestamp(System.currentTimeMillis()))
                    .displayType(noticeReq.getDisplayType())
                    .status(NoticeStatusEnum.NORMAL.getValue())
                    .build();
            noticeEntityMapper.insertSelective(noticeEntity);
            noticeId = noticeEntity.getId();
            String key = NoticePushRedisKey.getNoticeEntityKey(noticeId);
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
            valueOperations.set(key, JSONObject.toJSONString(noticeEntity), 1 ,TimeUnit.DAYS);
            return noticeId;
        } catch (Exception e) {
            log.error("store notice entity error!", e);
        }
        return 0;
    }

    /**
     * 插入单条notice relation 数据
     *
     * @param noticeUserRelation
     * @throws BizException
     */

    public void insertNoticeRelation(NoticeUserRelation noticeUserRelation) throws BizException {
        noticeUserRelation.setStatus(NoticeStatusEnum.NORMAL.getValue());
        noticeViewManager.saveOrUpdate(noticeUserRelation.getUserId(), noticeUserRelation.getNoticeId());
        ((NoticeLandingManager)AopContext.currentProxy()).insertRelationUnderAnnotation(noticeUserRelation.getUserId(), noticeUserRelation);
    }

    /**
     * 保存消息体和消息关系
     *
     * @param noticeReq
     * @return
     */
    @Transactional(rollbackFor = BizException.class)
    public int insert(NoticeReq noticeReq) throws BizException {
        AtomicInteger count = new AtomicInteger(0);
        final long noticeId;
        try {

            NoticeEntity noticeEntity = NoticeEntity.builder()
                    .type(noticeReq.getType())
                    .detailType(noticeReq.getDetailType())
                    .title(noticeReq.getTitle())
                    .text(noticeReq.getText4Data())
                    .custom(JSONObject.toJSON(noticeReq.getCustom()).toString())
                    .displayType(noticeReq.getDisplayType())
                    .updateTime(new Timestamp(System.currentTimeMillis()))
                    .createTime(new Timestamp(System.currentTimeMillis()))
                    .status(NoticeStatusEnum.NORMAL.getValue())
                    .build();
            noticeEntityMapper.insertSelective(noticeEntity);
            noticeId = noticeEntity.getId();

            String key = NoticePushRedisKey.getNoticeEntityKey(noticeId);
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
            valueOperations.set(key, JSONObject.toJSONString(noticeEntity), 1 ,TimeUnit.DAYS);

            List<NoticeUserRelation> noticeUserRelations = Lists.newArrayList();
            List<NoticeReq.NoticeUserRelation> relations = noticeReq.getUsers();
            relations.forEach(notice -> {

                NoticeUserRelation noticeUserRelation = NoticeUserRelation
                        .builder()
                        .type(noticeReq.getType())
                        .detailType(noticeReq.getDetailType())
                        .userId(notice.getUserId())
                        .type(noticeReq.getType())
                        .detailType(noticeReq.getDetailType())
                        .updateTime(new Timestamp(System.currentTimeMillis()))
                        .status(NoticeStatusEnum.NORMAL.getValue())
                        .noticeId(noticeId)
                        .build();

                noticeUserRelations.add(noticeUserRelation);
            });
            noticeUserRelations.forEach(item -> {
                noticeViewManager.saveOrUpdate(item.getUserId(), item.getNoticeId());
                ((NoticeLandingManager)AopContext.currentProxy()).insertRelationUnderAnnotation(item.getUserId(), item);
                count.incrementAndGet();
            });
            return count.get();
        } catch (Exception e) {
            log.error("insert notice msg error！", e);
            throw new BizException(NoticePushErrors.NOTICE_ENTITY_SAVE_FAILED);
        }
    }

    /**
     * 参与分表
     * @param userId
     * @param noticeUserRelation
     */
    @SplitParam
    public void insertRelationUnderAnnotation(long userId, NoticeUserRelation noticeUserRelation) {
        log.debug("user_id.value:{}", userId);
        noticeUserMapper.insertSelective(noticeUserRelation);
    }
}
