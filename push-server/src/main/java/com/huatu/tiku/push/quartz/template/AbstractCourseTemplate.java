package com.huatu.tiku.push.quartz.template;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.huatu.common.exception.BizException;
import com.huatu.tiku.push.cast.FileUploadTerminal;
import com.huatu.tiku.push.cast.PushService;
import com.huatu.tiku.push.cast.UmengNotification;
import com.huatu.tiku.push.cast.strategy.CustomAliasCastStrategyTemplate;
import com.huatu.tiku.push.cast.strategy.CustomFileCastStrategyTemplate;
import com.huatu.tiku.push.cast.strategy.NotificationHandler;
import com.huatu.tiku.push.constant.CourseParams;
import com.huatu.tiku.push.constant.NoticePushRedisKey;
import com.huatu.tiku.push.constant.RabbitMqKey;
import com.huatu.tiku.push.entity.CourseInfo;
import com.huatu.tiku.push.enums.NoticeTypeEnum;
import com.huatu.tiku.push.manager.SimpleUserManager;
import com.huatu.tiku.push.quartz.factory.CourseCastFactory;
import com.huatu.tiku.push.request.NoticeReq;
import com.huatu.tiku.push.service.api.NoticeStoreService;
import com.huatu.tiku.push.service.api.SimpleUserService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-21 下午9:18
 **/
public abstract class AbstractCourseTemplate {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SimpleUserManager simpleUserManager;

    @Autowired
    private PushService pushService;

    @Autowired
    private SimpleUserService simpleUserService;

    @Autowired
    private NotificationHandler notificationHandler;

    @Autowired
    private CustomAliasCastStrategyTemplate customCastStrategyTemplate;

    @Autowired
    private CustomFileCastStrategyTemplate fileCastStrategyTemplate;

    @Autowired
    private NoticeStoreService noticeStoreService;

    private int userCountInRedis;


    protected int getUserCountInRedis() {
        return userCountInRedis;
    }

    private final void setUserCountInRedis(int userCountInRedis) {
        this.userCountInRedis = userCountInRedis;
    }

    /**
     * push logic for custom cast
     * @param courseInfo
     * @param courseParams
     * @param noticeRelations
     * @return
     * @throws BizException
     */
    protected abstract List<NoticeReq> noticePush(CourseInfo courseInfo, CourseParams.Builder courseParams, List<NoticeReq.NoticeUserRelation> noticeRelations)throws BizException;


    /**
     * push logic for file cast
     * @param courseInfo
     * @param courseParams
     * @return
     * @throws BizException
     */
    protected abstract NoticeReq noticePush(CourseInfo courseInfo, CourseParams.Builder courseParams)throws BizException;

    /**
     * insert logic for custom
     * @param courseInfo
     * @param courseParams
     * @param noticeRelations
     * @return
     * @throws BizException
     */
    protected abstract List<NoticeReq> noticeInsert(CourseInfo courseInfo, CourseParams.Builder courseParams, List<NoticeReq.NoticeUserRelation> noticeRelations)throws BizException;


    /**
     * insert logic for file
     * @param courseInfo
     * @param courseParams
     * @return
     * @throws BizException
     */
    protected abstract NoticeReq noticeInsert(CourseInfo courseInfo, CourseParams.Builder courseParams)throws BizException;

    /**
     * custom notification push list
     * @param noticePushList
     * @return
     * @throws BizException
     */
    protected List<UmengNotification> customCastNotification(List<NoticeReq> noticePushList)throws BizException{
        return CourseCastFactory.customCastNotifications(noticePushList);
    }

    /**
     * file notification push -->
     * @param noticeReq
     * @param classId
     * @param liveId
     * @return
     * @throws BizException
     */
    protected List<UmengNotification> fileCastNotification(long classId, long liveId, NoticeReq noticeReq)throws BizException{
        FileUploadTerminal fileUploadTerminal = simpleUserService.obtainFileUpload(classId, liveId);
        if(null == fileUploadTerminal){
            return Lists.newArrayList();
        }
        return CourseCastFactory.customFileCastNotifications(noticeReq, fileUploadTerminal);
    }

    /**
     * 处理逻辑
     * @param bizData
     */
    public final void dealDetailJob(NoticeTypeEnum noticeTypeEnum, String bizData)throws BizException{
        CourseInfo courseInfo = JSONObject.parseObject(bizData, CourseInfo.class);
        String key = NoticePushRedisKey.getCourseClassId(courseInfo.getClassId());
        Set<Integer> users = redisTemplate.opsForSet().members(key);
        if(CollectionUtils.isEmpty(users)){
            users.addAll(simpleUserManager.selectByBizId(CourseParams.TYPE, courseInfo.getClassId()));
        }
        setUserCountInRedis(users.size());
        CourseParams.Builder courseParams = CourseCastFactory.courseParams(courseInfo);
        List<NoticeReq.NoticeUserRelation> noticeRelations = CourseCastFactory.noticeRelation(users);
        List<NoticeReq> noticePushList = noticePush(courseInfo, courseParams, noticeRelations);
        List<NoticeReq> noticeInsertList = noticeInsert(courseInfo, courseParams, noticeRelations);
            // ---
        if(getUserCountInRedis() < RabbitMqKey.PUSH_STRATEGY_THRESHOLD){
            List<UmengNotification> notificationList = customCastNotification(noticePushList);
            customCastStrategyTemplate.setNotificationList(notificationList);
            notificationHandler.setPushStrategy(customCastStrategyTemplate);
        }else{
            NoticeReq noticeReq = noticePush(courseInfo, courseParams);
            List<UmengNotification> notificationList = fileCastNotification(courseInfo.getClassId(), courseInfo.getLiveId(), noticeReq);
            fileCastStrategyTemplate.setNotificationList(notificationList);
            notificationHandler.setPushStrategy(fileCastStrategyTemplate);
        }
        notificationHandler.setDetailType(noticeTypeEnum);
        notificationHandler.setBizId(courseInfo.getLiveId());
        notificationHandler.push();
            //  ====
        //pushService.push(noticePushList);
        noticeStoreService.store(noticeInsertList);
    }
}