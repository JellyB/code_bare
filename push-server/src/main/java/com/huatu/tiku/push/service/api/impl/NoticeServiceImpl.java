package com.huatu.tiku.push.service.api.impl;


import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.huatu.common.exception.BizException;
import com.huatu.tiku.push.annotation.SplitParam;
import com.huatu.tiku.push.constant.BaseMsg;
import com.huatu.tiku.push.constant.NoticePushErrors;
import com.huatu.tiku.push.dao.NoticeEntityMapper;
import com.huatu.tiku.push.dao.NoticeUserMapper;
import com.huatu.tiku.push.dao.strategy.Strategy;
import com.huatu.tiku.push.entity.NoticeEntity;
import com.huatu.tiku.push.entity.NoticeUserRelation;
import com.huatu.tiku.push.enums.NoticeReadEnum;
import com.huatu.tiku.push.enums.NoticeStatusEnum;
import com.huatu.tiku.push.request.NoticeRelationReq;
import com.huatu.tiku.push.request.NoticeReq;
import com.huatu.tiku.push.response.NoticeResp;
import com.huatu.tiku.push.service.api.NoticeService;
import com.huatu.tiku.push.util.ConsoleContext;
import com.huatu.tiku.push.util.NoticeTimeParseUtil;
import com.huatu.tiku.push.util.ThreadLocalManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-07 上午10:48
 **/
@Service
@Slf4j
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private NoticeEntityMapper noticeEntityMapper;

    @Autowired
    private NoticeUserMapper noticeUserMapper;



    /**
     * 我的消息列表封装
     * @param userId
     * @param page
     * @param size
     * @return
     * @throws BizException
     */
    @Override
    @SplitParam(splitParams = "userId")
    public PageInfo selectUserNotice(long userId, int page, int size) throws BizException{
        ConsoleContext consoleContext = new ConsoleContext();
        ThreadLocalManager.setConsoleContext(consoleContext);
        Map<String, Object> params = Maps.newHashMap();
        params.put(Strategy.USER_ID, userId);
        consoleContext.setRequestHeader(params);
        ThreadLocalManager.setConsoleContext(consoleContext);


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
        pageInfo.getList().forEach(item -> {
            NoticeUserRelation noticeUserRelation = (NoticeUserRelation) item;
            noticeIds.add(noticeUserRelation.getNoticeId());
        });
        Map<Long, NoticeEntity> maps = obtainNoticeMaps(noticeIds);
        List<NoticeResp> list = Lists.newArrayList();
        pageInfo.getList().forEach(relations -> {
            NoticeUserRelation noticeUserRelation = (NoticeUserRelation) relations;
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
        pageInfo.setList(list);
        return pageInfo;
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

        NoticeEntity noticeEntity = NoticeEntity
                .builder()
                .type(req.getType())
                .detailType(req.getDetailType())
                .title(req.getTitle())
                .text(req.getText())
                .custom(JSONObject.toJSON(req.getCustom()).toString())
                .displayType(req.getDisplayType())
                .build();

        noticeEntityMapper.insertSelective(noticeEntity);
        final Set<Long> userIds = obtainUsersByNotice(noticeEntity.getId());
        if(!CollectionUtils.isEmpty(req.getUsers())){
            req.getUsers().forEach( user -> {
                if(userIds.contains(user.getUserId())){
                    return;
                }
                NoticeUserRelation noticeUserRelation = NoticeUserRelation.
                        builder()
                        .noticeId(noticeEntity.getId())
                        .userId(user.getUserId())
                        .isRead(NoticeReadEnum.UN_READ.getValue())
                        .build();
                noticeUserMapper.insertSelective(noticeUserRelation);
            });
        }
        return noticeEntity.getId();
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
                        .userId(user)
                        .isRead(NoticeReadEnum.UN_READ.getValue())
                        .build();
                count.incrementAndGet();
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
     * 根据多个notice id 查询 notice 表信息 返回map
     * @param noticeIds
     * @return
     */
    private Map<Long, NoticeEntity> obtainNoticeMaps(Set<Long> noticeIds){
        if(CollectionUtils.isEmpty(noticeIds)){
            throw new BizException(NoticePushErrors.NOTICE_USER_RELATIONS_LIST_EMPTY);
        }
        Example example = new Example(NoticeEntity.class);
        example.and()
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue())
                .andIn("id", noticeIds);
        List<NoticeEntity> list = noticeEntityMapper.selectByExample(example);
        if(CollectionUtils.isEmpty(list)){
            return Maps.newHashMap();
        }
        return list.stream().collect(Collectors.toMap(i-> i.getId(), i -> i));
    }
    /**
     * 消息已读
     *
     * @param noticeId
     * @return
     * @throws BizException
     */
    @Override
    public Object hasRead(long noticeId) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("id", noticeId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());

        NoticeUserRelation noticeUserRelation = NoticeUserRelation
                .builder()
                .isRead(NoticeReadEnum.READ.getValue())
                .updateTime(new Timestamp(System.currentTimeMillis()))
                .build();
        return noticeUserMapper.updateByExampleSelective(noticeUserRelation, example);
    }

    /**
     * 获取我的消息未读数
     *
     * @param userId
     * @return
     * @throws BizException
     */
    @Override
    public int unReadNum(long userId) throws BizException {
        Example example = new Example(NoticeUserRelation.class);
        example.and()
                .andEqualTo("userId", userId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue())
                .andEqualTo("isRead", NoticeReadEnum.UN_READ.getValue());

        return noticeUserMapper.selectCountByExample(example);
    }
}