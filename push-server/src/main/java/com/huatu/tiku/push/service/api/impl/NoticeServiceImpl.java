package com.huatu.tiku.push.service.api.impl;


import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.huatu.common.exception.BizException;
import com.huatu.springboot.degrade.core.Degrade;
import com.huatu.tiku.push.annotation.SplitParam;
import com.huatu.tiku.push.constant.*;
import com.huatu.tiku.push.dao.CourseInfoMapper;
import com.huatu.tiku.push.dao.NoticeEntityMapper;
import com.huatu.tiku.push.dao.NoticeUserMapper;
import com.huatu.tiku.push.entity.CourseInfo;
import com.huatu.tiku.push.entity.NoticeEntity;
import com.huatu.tiku.push.entity.NoticeUserRelation;
import com.huatu.tiku.push.enums.NoticeReadEnum;
import com.huatu.tiku.push.enums.NoticeStatusEnum;
import com.huatu.tiku.push.enums.NoticeTypeEnum;
import com.huatu.tiku.push.manager.NoticeEntityManager;
import com.huatu.tiku.push.manager.NoticeViewManager;
import com.huatu.tiku.push.request.NoticeRelationReq;
import com.huatu.tiku.push.request.NoticeReq;
import com.huatu.tiku.push.response.NoticeResp;
import com.huatu.tiku.push.service.api.NoticeService;
import com.huatu.tiku.push.service.api.UserInfoComponent;
import com.huatu.tiku.push.service.api.strategy.NoticeRespAppStrategy;
import com.huatu.tiku.push.service.api.strategy.NoticeRespHandler;
import com.huatu.tiku.push.service.api.strategy.NoticeRespPcStrategy;
import com.huatu.tiku.push.util.NoticeTimeParseUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-07 上午10:48
 **/
@Slf4j
@Service(value = "noticeService")
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private NoticeEntityMapper noticeEntityMapper;

    @Autowired
    private NoticeUserMapper noticeUserMapper;

    @Autowired
    private NoticeRespHandler noticeRespHandler;

    @Autowired
    private CourseInfoMapper courseInfoMapper;

    @Autowired
    private UserInfoComponent userInfoComponent;

    @Autowired
    @Qualifier(value = "noticeRespAppStrategy")
    private NoticeRespAppStrategy noticeRespAppStrategy;

    @Autowired
    @Qualifier(value = "noticeRespPcStrategy")
    private NoticeRespPcStrategy noticeRespPcStrategy;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private NoticeViewManager noticeViewManager;

    @Autowired
    private NoticeEntityManager noticeEntityManager;
    /**
     * 我的消息列表封装
     * @param userId
     * @param page
     * @param size
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam
    public PageInfo selectUserNotice(long userId, int page, int size) throws BizException{

        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("userId", userId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());

        example.orderBy("createTime").desc();

        PageInfo pageInfo = PageHelper
                .startPage(page, size)
                .doSelectPageInfo(()->noticeUserMapper.selectByExample(example));

        if(CollectionUtils.isEmpty(pageInfo.getList())){
            return pageInfo;
        }

        Set<Long> noticeIds = Sets.newHashSet();
        List<NoticeUserRelation> noticeUserRelations = pageInfo.getList();
        noticeUserRelations.forEach(noticeUserRelation -> {
            noticeIds.add(noticeUserRelation.getNoticeId());
        });
        noticeRespHandler.setAbstractNoticeResp(noticeRespAppStrategy);
        Map<Long, NoticeEntity> maps = noticeEntityManager.obtainNoticeMaps(noticeIds);
        return noticeRespHandler.build(pageInfo, maps);
    }


    /**
     * 保存消息列表
     *
     * @param req
     * @return
     * @throws BizException
     */
    @Override
    @Transactional(rollbackFor = BizException.class)
    public Object saveNotices(NoticeReq req) throws BizException {
        if(CollectionUtils.isEmpty(req.getUsers())){
            return null;
        }
        NoticeEntity noticeEntity = NoticeEntity
                .builder()
                .type(req.getType())
                .detailType(req.getDetailType())
                .title(req.getTitle())
                .text(req.getText4Data())
                .custom(JSONObject.toJSON(req.getCustom()).toString())
                .createTime(new Timestamp(System.currentTimeMillis()))
                .updateTime(new Timestamp(System.currentTimeMillis()))
                .displayType(req.getDisplayType())
                .build();

        noticeEntityMapper.insertSelective(noticeEntity);
        long noticeId = noticeEntity.getId();

        String key = NoticePushRedisKey.getNoticeEntityKey(noticeId);
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        valueOperations.set(key, JSONObject.toJSONString(noticeEntity), 1 , TimeUnit.DAYS);

        //final Set<Long> userIds = obtainUsersByNotice(noticeEntity.getId());
        for(NoticeReq.NoticeUserRelation user : req.getUsers()){
            NoticeUserRelation noticeUserRelation = NoticeUserRelation.
                    builder()
                    .type(req.getType())
                    .detailType(req.getDetailType())
                    .noticeId(noticeEntity.getId())
                    .userId(user.getUserId())
                    .createTime(new Timestamp(System.currentTimeMillis()))
                    .updateTime(new Timestamp(System.currentTimeMillis()))
                    .isRead(NoticeReadEnum.UN_READ.getValue())
                    .build();
            log.debug("noticeUserRelation:{}", JSONObject.toJSONString(noticeUserRelation));
            noticeViewManager.saveOrUpdate(noticeUserRelation.getUserId(), noticeUserRelation.getNoticeId());
            ((NoticeServiceImpl) AopContext.currentProxy()).insertNoticeRelation(user.getUserId(), noticeUserRelation);
        }
        return noticeEntity.getId();
    }

    @SplitParam
    public int insertNoticeRelation(long userId, NoticeUserRelation noticeUserRelation){
        log.info("user.id.value:{}", userId);
        return noticeUserMapper.insertSelective(noticeUserRelation);
    }

    /**
     * 添加user notice关系
     *
     * @param noticeRelationReq
     * @return
     * @throws BizException
     */
    @Override
    public Object addUsers(NoticeRelationReq noticeRelationReq) throws BizException {
        if(checkNoticeExist(noticeRelationReq.getNoticeId())){
            throw new BizException(NoticePushErrors.NOTICE_ENTITY_UN_EXIST);
        }
        AtomicInteger count = new AtomicInteger(0);
        if(CollectionUtils.isEmpty(noticeRelationReq.getUsers())){
            throw new BizException(NoticePushErrors.NOTICE_USER_RELATIONS_EMPTY);
        }else{
            final Set<Long> userIds = obtainUsersByNotice(noticeRelationReq.getNoticeId());

            noticeRelationReq.getUsers().forEach( user -> {
                if(userIds.contains(user)){
                    return;
                }
                NoticeUserRelation noticeUserRelation = NoticeUserRelation.
                        builder()
                        .noticeId(noticeRelationReq.getNoticeId())
                        .type(noticeRelationReq.getType())
                        .detailType(noticeRelationReq.getDetailType())
                        .userId(user)
                        .isRead(NoticeReadEnum.UN_READ.getValue())
                        .build();
                count.incrementAndGet();
                noticeViewManager.saveOrUpdate(noticeUserRelation.getUserId(), noticeUserRelation.getNoticeId());
                noticeUserMapper.insertSelective(noticeUserRelation);
            });
        }
        return count.get();
    }

    /**
     * 检查notice entity 是否存在
     * @param noticeId
     */
    private boolean checkNoticeExist(long noticeId){
        Example example = new Example(NoticeEntity.class);
        example.and()
                .andEqualTo("id", noticeId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());

        List<NoticeEntity> noticeEntityList = noticeEntityMapper.selectByExample(example);
        return CollectionUtils.isEmpty(noticeEntityList);
    }

    /**
     * 获取此消息id 的所有userId
     * @param noticeId
     * @return
     */
    private Set<Long> obtainUsersByNotice(long noticeId){
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("noticeId", noticeId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());

        List<NoticeUserRelation> noticeUserRelations = noticeUserMapper.selectByExample(example);
        return noticeUserRelations.stream()
                .map(NoticeUserRelation::getUserId)
                .collect(Collectors.toSet());
    }


    /**
     * 消息已读
     *
     * @param noticeId
     * @return
     * @throws BizException
     */
    @Override
    @Deprecated
    public Object hasRead(long noticeId) throws BizException {
        return null;
    }

    /**
     * 获取我的消息未读数
     *
     * @param userId
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam
    @Degrade(key="unReadCountV2", name="我的未读消息数")
    public int unReadNum(long userId) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("userId", userId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue())
                .andEqualTo("isRead", NoticeReadEnum.UN_READ.getValue());

        return noticeUserMapper.selectCountByExample(example);
    }

    /**
     * 我的未读消息降级处理 -- 针对学习界面降级
     * @param userId
     * @return
     * @throws BizException
     */
    public int unReadNumDegrade(long userId)throws BizException{
        return 0;
    }

    /**
     * 消息已读
     *
     * @param userId
     * @param noticeId
     * @return
     * @throws BizException
     */
    @Override
    public Object hasRead(long userId, long noticeId) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("id", noticeId)
                .andEqualTo("userId", userId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());

        NoticeUserRelation noticeUserRelation = NoticeUserRelation
                .builder()
                .isRead(NoticeReadEnum.READ.getValue())
                .updateTime(new Timestamp(System.currentTimeMillis()))
                .build();

        noticeViewManager.resetViewUnReadCount(userId, noticeId);
        return ((NoticeServiceImpl)AopContext.currentProxy()).updateByExampleSelective(userId, noticeUserRelation, example);
    }

    /**
     * 更新消息已读
     * @param userId
     * @param noticeUserRelation
     * @param example
     * @return
     */
    @SplitParam
    public int updateByExampleSelective(long userId, NoticeUserRelation noticeUserRelation, Example example){
        return noticeUserMapper.updateByExampleSelective(noticeUserRelation, example);
    }

    /**
     * 获取用户指定type类型的消息列表
     *
     * @param userId
     * @param type
     * @param page
     * @param size
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam
    public PageInfo noticeList4Pc(long userId, String type, int page, int size) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("userId", userId)
                .andEqualTo("type", type)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());
        example.orderBy("createTime").desc();

        PageInfo pageInfo = PageHelper.startPage(page, size).doSelectPageInfo(() -> noticeUserMapper.selectByExample(example));
        if(CollectionUtils.isEmpty(pageInfo.getList())){
            return pageInfo;
        }
        Set<Long> noticeIds = Sets.newHashSet();
        List<NoticeUserRelation> noticeUserRelations = pageInfo.getList();
        noticeUserRelations.forEach(noticeUserRelation -> noticeIds.add(noticeUserRelation.getNoticeId()));
        Map<Long, NoticeEntity> maps = noticeEntityManager.obtainNoticeMaps(noticeIds);
        noticeRespHandler.setAbstractNoticeResp(noticeRespPcStrategy);
        return noticeRespHandler.build(pageInfo, maps);
    }


    /**
     * 返回移动端和pc消息list
     * @param noticeUserRelations
     * @param maps
     * @return
     */
    private List<NoticeResp> getNoticeResps(List<NoticeUserRelation> noticeUserRelations, Map<Long, NoticeEntity> maps) {
        List<NoticeResp> list = Lists.newArrayList();
        noticeUserRelations.forEach(noticeUserRelation -> {
            NoticeEntity noticeEntity = maps.get(noticeUserRelation.getNoticeId());

            if(null == noticeEntity){
                throw new BizException(NoticePushErrors.NOTICE_ENTITY_UN_EXIST);
            }
            BaseMsg baseMsg = BaseMsg
                    .builder()
                    .title(noticeEntity.getTitle())
                    .text(noticeEntity.getText())
                    .build();

            if(StringUtils.isNoneBlank(noticeEntity.getCustom())){
                JSONObject jsonObject = JSONObject.parseObject(noticeEntity.getCustom());
                Map custom = jsonObject;
                baseMsg.setCustom(custom);
            }
            String noticeTime = NoticeTimeParseUtil.parseTime(noticeUserRelation.getCreateTime().getTime());
            NoticeResp noticeResp = NoticeResp
                    .builder()
                    .noticeId(noticeUserRelation.getId())
                    .noticeTime(noticeTime)
                    .display_type(1)
                    .isRead(noticeUserRelation.getIsRead())
                    .type(noticeEntity.getType())
                    .detailType(noticeEntity.getDetailType())
                    .userId(noticeUserRelation.getUserId())
                    .payload(baseMsg)
                    .build();

            list.add(noticeResp);
        });
        return list;
    }

    /**
     * 逻辑删除noticeId
     *
     * @param userId
     * @param noticeId
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam
    public Object deleteNotice(long userId, long noticeId) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("id", noticeId);

        NoticeUserRelation noticeUserRelation = NoticeUserRelation
                .builder()
                .updateTime(new Timestamp(System.currentTimeMillis()))
                .status(NoticeStatusEnum.DELETE_LOGIC.getValue())
                .build();
        return noticeUserMapper.updateByExampleSelective(noticeUserRelation, example);
    }


    /**
     * 数据刷新接口
     *
     * @param secret
     * @param type
     * @param detailType
     * @return
     * @throws BizException
     */
    @Override
    public Object refresh(String secret, String type, String detailType) throws BizException {
        NoticeTypeEnum noticeTypeEnum = NoticeTypeEnum.create(type, detailType);
        if(null == noticeTypeEnum){
            throw new BizException(NoticePushErrors.NOTICE_TYPE_CREATE_ERROR);
        }
        List<NoticeEntity> list = searchNoticeList(type, detailType);
        switch (noticeTypeEnum){
            case COURSE_REMIND:
            case COURSE_READY:
                dealCourseInfo(list);
                break;
            case SUGGEST_FEEDBACK:
            case CORRECT_FEEDBACK:
                dealFeedbackInfo(list);
                break;
                default:
                    break;
        }

        return null;
    }


    /**
     * 根据type处理消息
     * @param type
     * @param detailType
     * @return
     */
    private final List<NoticeEntity> searchNoticeList(String type, String detailType){
        Example example = new Example(NoticeEntity.class);
        example.and()
                .andEqualTo("type", type)
                .andEqualTo("detailType", detailType);

        example.orderBy("id").asc();

        List<NoticeEntity> noticeEntities = noticeEntityMapper.selectByExample(example);
        log.info("refresh data type:{}, detailType:{}, size:{}", type, detailType, noticeEntities.size());
        return noticeEntities;
    }

    private final void dealFeedbackInfo(List<NoticeEntity> list){
        for(NoticeEntity noticeEntity : list){

        }
    }


    private final void dealCourseInfo(List<NoticeEntity> list){
        for(NoticeEntity noticeEntity : list){
            String custom = noticeEntity.getCustom();
            if(StringUtils.isEmpty(custom)){
                return;
            }
            try{
                JSONObject jsonObject = JSONObject.parseObject(custom);
                Object bizId = jsonObject.get(CourseParams.BIZ_ID);
                String classId = String.valueOf(bizId);
                CourseInfo courseInfo = searchCourseInfo(Long.valueOf(classId));
                if(courseInfo == null){
                    jsonObject.put(CourseParams.CLASS_TITLE, "测试数据，没有课程标题");
                    jsonObject.put(CourseParams.START_TIME, System.currentTimeMillis());
                }else{
                    jsonObject.put(CourseParams.CLASS_TITLE, courseInfo.getClassTitle());
                    jsonObject.put(CourseParams.START_TIME, courseInfo.getStartTime());
                }
                custom = jsonObject.toJSONString();
                updateNoticeEntity(noticeEntity.getId(), custom);
            }catch (Exception e){
                log.error("parseObject error", e);
            }
        }
    }

    private CourseInfo searchCourseInfo(long class_id){
        Example example = new Example(CourseInfo.class);
        example.and()
                .andEqualTo("classId", class_id);
        List<CourseInfo> courseInfos = courseInfoMapper.selectByExample(example);
        if(courseInfos.size() == 0){
            return null;
        }else{
            return courseInfos.get(0);
        }
    }

    private void updateNoticeEntity(long id, String custom){
        NoticeEntity noticeEntity = new NoticeEntity();
        noticeEntity.setId(id);
        noticeEntity.setCustom(custom);
        noticeEntityMapper.updateByPrimaryKeySelective(noticeEntity);
    }

    /**
     * 根据uName获取未读消息数
     *
     * @param nName 用户昵称
     * @return
     * @throws BizException
     */
    @Override
    public Object unreadCountForPhp(String nName) throws BizException {
        if(StringUtils.isEmpty(nName)){
            return 0;
        }else{
            try{
                List<String> list = Lists.newArrayList();
                list.add(nName);
                UserResponse userResponse = userInfoComponent.getUserIdResponse(list);
                if(null == userResponse || CollectionUtils.isEmpty(userResponse.getData())){
                    return 0;
                }
                long userId = userResponse.getData().get(0).getUserId();
                return ((NoticeServiceImpl)AopContext.currentProxy()).unReadNum(userId);
            }catch (Exception e){
                return 0;
            }
        }
    }

    /**
     * 左滑消息逻辑删除操作
     *
     * @param noticeId
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam
    public Object deleteByLogic(long userId, long noticeId) throws BizException {
        NoticeUserRelation noticeUserRelation = NoticeUserRelation
                .builder()
                .id(noticeId)
                .updateTime(new Timestamp(System.currentTimeMillis()))
                .status(NoticeStatusEnum.DELETE_LOGIC.getValue())
                .build();

        return noticeUserMapper.updateByPrimaryKeySelective(noticeUserRelation);
    }

    /**
     * 全部已读
     *
     * @param userId
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam
    public Object readAll(long userId) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("userId", userId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue())
                .andEqualTo("isRead", NoticeReadEnum.UN_READ.getValue());

        NoticeUserRelation noticeUserRelation = NoticeUserRelation
                .builder()
                .isRead(NoticeReadEnum.READ.getValue())
                .updateTime(new Timestamp(System.currentTimeMillis()))
                .build();

        return noticeUserMapper.updateByExampleSelective(noticeUserRelation, example);
    }
}
