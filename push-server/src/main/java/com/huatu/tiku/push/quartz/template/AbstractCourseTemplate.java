package com.huatu.tiku.push.quartz.template;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.huatu.common.exception.BizException;
import com.huatu.tiku.push.cast.FileUploadTerminal;
import com.huatu.tiku.push.cast.UmengNotification;
import com.huatu.tiku.push.cast.strategy.CustomAliasCastStrategyTemplate;
import com.huatu.tiku.push.cast.strategy.CustomFileCastStrategyTemplate;
import com.huatu.tiku.push.cast.strategy.NotificationHandler;
import com.huatu.tiku.push.constant.CourseParams;
import com.huatu.tiku.push.constant.LiveCourseInfo;
import com.huatu.tiku.push.constant.NoticePushRedisKey;
import com.huatu.tiku.push.constant.RabbitMqKey;
import com.huatu.tiku.push.entity.CourseInfo;
import com.huatu.tiku.push.enums.JumpTargetEnum;
import com.huatu.tiku.push.enums.NoticeTypeEnum;
import com.huatu.tiku.push.manager.SimpleUserManager;
import com.huatu.tiku.push.quartz.factory.CourseCastFactory;
import com.huatu.tiku.push.request.NoticeReq;
import com.huatu.tiku.push.service.api.CourseInfoComponent;
import com.huatu.tiku.push.service.api.CourseService;
import com.huatu.tiku.push.service.api.NoticeStoreService;
import com.huatu.tiku.push.service.api.SimpleUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-21 下午9:18
 **/
@Slf4j
public abstract class AbstractCourseTemplate {

    @Autowired
    private CourseInfoComponent courseInfoComponent;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SimpleUserManager simpleUserManager;

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

    @Autowired
    private CourseService courseService;

    @Value("${notice.push.white.list}")
    private String whiteUserId;

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
     * @param jumpTargetEnum
     * @return
     * @throws BizException
     */
    protected ImmutableList<UmengNotification> customCastNotification(long liveId, List<NoticeReq> noticePushList, JumpTargetEnum jumpTargetEnum)throws BizException{
        return CourseCastFactory.customCastNotifications(liveId, noticePushList, jumpTargetEnum);
    }

    /**
     * file notification push -->
     * @param noticeReq
     * @param classId
     * @param liveId
     * @param jumpTargetEnum
     * @return
     * @throws BizException
     */
    protected List<UmengNotification> fileCastNotification(long classId, long liveId, NoticeReq noticeReq, JumpTargetEnum jumpTargetEnum)throws BizException{
        FileUploadTerminal fileUploadTerminal = simpleUserService.obtainFileUpload(classId, liveId);
        if(null == fileUploadTerminal){
            return Lists.newArrayList();
        }
        return CourseCastFactory.customFileCastNotifications(noticeReq, fileUploadTerminal, jumpTargetEnum);
    }

    /**
     * 课后作业数据处理逻辑
     * @param bizData
     * @throws BizException
     */
    public final void alert(String bizData) throws BizException{
        CourseInfo courseInfo_ = bizDataFix(bizData);
        Long liveCourseWareId = courseInfo_.getLiveId();
        Long classId = courseInfo_.getClassId();
        LiveCourseInfo liveCourseInfo = courseInfoComponent.fetchCourseInfo(classId, liveCourseWareId);
        if(liveCourseInfo == null){
            return;
        }
        if(liveCourseInfo.getAfterExercisesNum() == 0 || liveCourseInfo.getSubjectType() != 2){
            return;
        }
        Set<Integer> users = usersData(courseInfo_.getClassId());
        if(CollectionUtils.isEmpty(users)){
            log.error("暂无学员购此课程:{}", classId);
            return;
        }
        //数据放入队列中
        for (Integer user : users) {
            JSONObject userInfo = new JSONObject();
            userInfo.put("classId", classId);
            userInfo.put("userId", user);
            userInfo.put("syllabusId",liveCourseInfo.getSyllabusId());
            userInfo.put("courseWareId", liveCourseWareId);
            rabbitTemplate.convertAndSend(RabbitMqKey.LIVE_COURSE_END_NOTICE,  userInfo.toJSONString());
        }
    }

    /**
     * 数据库重新查取课程信息
     * @param bizData
     * @return
     * @throws BizException
     */
    private CourseInfo bizDataFix(String bizData) throws BizException{
        CourseInfo origin = JSONObject.parseObject(bizData, CourseInfo.class);
        //数据库重新查取课程信息
        CourseInfo courseInfo_ = courseService.findCourseById(origin.getLiveId());
        log.info("数据库中重新查新出来的任务信息:{}", JSONObject.toJSONString(courseInfo_));
        log.info("跟随任务一起绑定过来的课程信息:{}", JSONObject.toJSONString(origin));
        if(!origin.getTeacher().equals(courseInfo_.getTeacher())) {
            log.error("课程信息不一致!!!, liveId:{}, teacher origin:{}, teacher data base:{}", courseInfo_.getLiveId(), origin.getTeacher(), courseInfo_.getTeacher());
        }
        return courseInfo_;
    }

    /**
     * 获取购买此课程的 userId
     * @param classId
     * @return
     * @throws BizException
     */
    private Set<Integer> usersData(Long classId) throws BizException{
        String key = NoticePushRedisKey.getCourseClassId(classId);
        Set<Integer> users = redisTemplate.opsForSet().members(key);
        if(CollectionUtils.isEmpty(users)){
            users.addAll(simpleUserManager.selectByBizId(CourseParams.TYPE, classId));
        }
        try{
            Set<Integer> whiteList = Stream.of(whiteUserId.split(",")).map(i -> Integer.valueOf(i)).collect(Collectors.toSet());
            users.addAll(whiteList);
        }catch (Exception e){
            log.error("添加白名单用户异常:{}", whiteUserId);
        }
        return users;
    }

    /**
     * 处理逻辑
     * @param bizData
     */
    public final void dealDetailJob(NoticeTypeEnum noticeTypeEnum, String bizData)throws BizException{
        CourseInfo courseInfo_ = bizDataFix(bizData);
        Set<Integer> users = usersData(courseInfo_.getClassId());
        if(CollectionUtils.isEmpty(users)){
            log.error("course.class.id:{}.user.list.empty", courseInfo_.getClassId());
            return;
        }else{
            setUserCountInRedis(users.size());
            CourseParams.Builder courseParams = CourseCastFactory.courseParams(courseInfo_);
            log.info("直播课 liveId:{},params:{}", courseInfo_.getLiveId(), JSONObject.toJSONString(courseParams));
            List<NoticeReq.NoticeUserRelation> noticeRelations = CourseCastFactory.noticeRelation(users);
            List<NoticeReq> noticePushList = noticePush(courseInfo_, courseParams, noticeRelations);
            List<NoticeReq> noticeInsertList = noticeInsert(courseInfo_, courseParams, noticeRelations);
            if(getUserCountInRedis() < RabbitMqKey.PUSH_STRATEGY_THRESHOLD){
                List<UmengNotification> notificationList = customCastNotification(courseInfo_.getLiveId(), noticePushList, JumpTargetEnum.NOTICE_CENTER);
                customCastStrategyTemplate.setNotificationList(notificationList);
                notificationHandler.setPushStrategy(customCastStrategyTemplate);
                notificationHandler.setConcurrent(false);
                log.info("课程推送 custom ,直播id:{}, 数据 size:{}, 待准备推送数据:{}", courseInfo_.getLiveId(), notificationList.size(), JSONObject.toJSONString(notificationList));
            }else{
                NoticeReq noticeReq = noticePush(courseInfo_, courseParams);
                List<UmengNotification> notificationList = fileCastNotification(courseInfo_.getClassId(), courseInfo_.getLiveId(), noticeReq, JumpTargetEnum.NOTICE_CENTER);
                fileCastStrategyTemplate.setNotificationList(notificationList);
                notificationHandler.setPushStrategy(fileCastStrategyTemplate);
                notificationHandler.setConcurrent(false);
                log.info("课程推送 file ,直播id:{}, 数据 size:{}, 待准备推送数据:{}", courseInfo_.getLiveId(), noticeInsertList.size(), JSONObject.toJSONString(notificationList));
            }
            notificationHandler.setDetailType(noticeTypeEnum);
            notificationHandler.setBizId(courseInfo_.getLiveId());
            log.warn("当前任务被执行了,type:{}, detailType:{}, bizId:{}", noticeTypeEnum.getType().getType(), noticeTypeEnum.getDetailType(), courseInfo_.getLiveId());
            notificationHandler.push();
            noticeStoreService.store(noticeInsertList);
        }
    }
}
