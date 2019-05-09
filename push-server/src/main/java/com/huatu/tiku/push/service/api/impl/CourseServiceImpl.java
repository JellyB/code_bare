package com.huatu.tiku.push.service.api.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.huatu.common.ErrorResult;
import com.huatu.common.SuccessMessage;
import com.huatu.common.exception.BizException;
import com.huatu.tiku.push.constant.NoticePushErrors;
import com.huatu.tiku.push.dao.CourseInfoMapper;
import com.huatu.tiku.push.entity.CourseInfo;
import com.huatu.tiku.push.enums.JobScannedEnum;
import com.huatu.tiku.push.enums.NoticeStatusEnum;
import com.huatu.tiku.push.request.CourseInfoReq;
import com.huatu.tiku.push.response.CourseInfoResp;
import com.huatu.tiku.push.service.api.CourseService;
import com.huatu.tiku.push.service.api.QuartzJobInfoService;
import com.huatu.tiku.push.util.NoticeTimeParseUtil;
import com.huatu.tiku.push.util.PageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-07 下午3:43
 **/

@Slf4j
@Service
public class CourseServiceImpl implements CourseService {

    private static final int PHP_TIME_LENGTH = 10;
    private static final String PHP_JAVA_TIME = "000";

    @Autowired
    private CourseInfoMapper courseInfoMapper;

    @Autowired
    private QuartzJobInfoService quartzJobInfoService;

    /**
     * 新增直播课信息到本地库
     *
     * @param req
     * @return
     */
    @Override
    public Object saveCourseInfo(CourseInfoReq.Model req) throws BizException{
        CourseInfo courseInfo = getCourseInfo(req);
        /**
         * 如果直播课存在，删除直播课 & 推送任务，重建
         */
        try{
            if(checkLiveExist(courseInfo.getLiveId())){
                Example example = new Example(CourseInfo.class);
                example.and()
                        .andEqualTo("liveId", courseInfo.getLiveId())
                        .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());
                CourseInfo origin = courseInfoMapper.selectOneByExample(example);
                courseInfoMapper.deleteByPrimaryKey(origin.getId());
                quartzJobInfoService.deleteJobByBizData(String.valueOf(courseInfo.getLiveId()));
                log.info("直播课更新------>任务移除:{}", courseInfo.getLiveId());
            }
            return courseInfoMapper.insert(courseInfo);
        }catch (Exception e){
            log.error("更新直播课信息失败:{}", JSONObject.toJSONString(req));
            return 0;
        }
    }


    /**
     * 更新直播课信息
     * 更新课程信息，重新创建 job
     * @param courseInfo
     * @return
     */
    @Deprecated
    public int updateCourseInfo(CourseInfo courseInfo){
        Example example = new Example(CourseInfo.class);
        example.and()
                .andEqualTo("liveId", courseInfo.getLiveId())
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());
        CourseInfo origin = courseInfoMapper.selectOneByExample(example);
        CourseInfo courseInfo_ = new CourseInfo();
        /**
         * 如果直播课上课时间被修改，重新构建任务
         */
        if(courseInfo.getStartTime().getTime() != origin.getStartTime().getTime() || courseInfo.getEndTime().getTime() != origin.getEndTime().getTime()){
            log.info("直播课上课时间被更改: old:{}, new:{}", JSONObject.toJSONString(origin), JSONObject.toJSONString(courseInfo));
            /**
             * 移除任务
             */
            quartzJobInfoService.deleteJobByBizData(String.valueOf(courseInfo.getLiveId()));
            courseInfo_.setScanned(JobScannedEnum.NOT_YET_SCANNED.getValue());
        }
        BeanUtils.copyProperties(courseInfo, courseInfo_);
        courseInfo_.setId(origin.getId());
        courseInfo_.setStatus(null);
        courseInfo_.setCreateTime(null);
        courseInfo_.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return courseInfoMapper.updateByPrimaryKeySelective(courseInfo_);
    }


    /**
     * 批量新增直播信息
     *
     * @param batch
     * @return
     */
    @Override
    @Transactional(rollbackFor = BizException.class)
    public Object saveCourseInfoBatch(CourseInfoReq.Batch batch) throws BizException{
        if(CollectionUtils.isEmpty(batch.getList())){
            throw new BizException(ErrorResult.create(1000010, "直播列表不能为空！"));
        }

        List<CourseInfo> list = batch.getList().stream()
                .map(CourseServiceImpl::getCourseInfo)
                .collect(Collectors.toList());


        List<CourseInfo> existCourseInfo = list.stream().filter(item-> checkLiveExist(Long.valueOf(item.getLiveId())))
                .collect(Collectors.toList());
        /**
         * 批量更新操作
         */
        if(CollectionUtils.isEmpty(existCourseInfo)){
            list.forEach(info -> courseInfoMapper.insert(info));

        }else{
            existCourseInfo.forEach(item-> updateCourseInfo(item));
        }
        return list.size();
    }

    /**
     * 分页查询添加的推送课程list
     * 创建时间倒排
     *
     * @param page
     * @param size
     * @param startTime
     * @return
     * @throws BizException
     */
    @Override
    public Object list(int page, int size, String startTime) throws BizException {
        Example example = new Example(CourseInfo.class);
        Example.Criteria criteria = example.and();
        if (StringUtils.isNotBlank(startTime)) {
            criteria.andLike("startTime", startTime + "%");
        }
        example.orderBy("startTime").desc();

        PageInfo pageInfo = PageHelper.startPage(page, size).doSelectPageInfo(() -> courseInfoMapper.selectByExample(example));
        if(!CollectionUtils.isEmpty(pageInfo.getList())){
            List<CourseInfoResp> courseInfoResps = Lists.newArrayList();
            buildCourseResp(pageInfo.getList(), courseInfoResps);
            pageInfo.setList(courseInfoResps);
        }

        return PageUtil.parsePageInfo(pageInfo);
    }

    /**
     * 逻辑删除直播课
     *
     * @param liveId
     * @return
     * @throws BizException
     */
    @Override
    @Transactional
    public Object removeCourseInfo(long liveId) throws BizException {
        if(!checkLiveExist(liveId)){
            throw new BizException(NoticePushErrors.COURSE_INFO_UN_EXIST);
        }else{
            Example example = new Example(CourseInfo.class);
            example.and()
                    .andEqualTo("liveId", liveId)
                    .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());
            CourseInfo courseInfo = new CourseInfo();
            courseInfo.setStatus(NoticeStatusEnum.DELETE_LOGIC.getValue());
            courseInfo.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            quartzJobInfoService.deleteJobByBizData(String.valueOf(liveId));
            int execute = courseInfoMapper.updateByExampleSelective(courseInfo, example);
            log.info("删除直播课程推送信息:直播id:{}", liveId);
            return execute;

        }
    }

    /**
     * check live exist
     * @param liveId
     * @return
     */
    public boolean checkLiveExist(long liveId){
        Example example = new Example(CourseInfo.class);
        example.and()
                .andEqualTo("liveId", liveId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());
        List<CourseInfo> list = courseInfoMapper.selectByExample(example);
        return !CollectionUtils.isEmpty(list);
    }


    /**
     *
     * @param courseInfos
     * @param courseInfoResps
     * @throws BizException
     */
    private void buildCourseResp(List<CourseInfo> courseInfos, List<CourseInfoResp> courseInfoResps)throws BizException{
        courseInfos.forEach(item -> {
            CourseInfoResp courseInfoResp = new CourseInfoResp();
            BeanUtils.copyProperties(item, courseInfoResp);
            courseInfoResp.setStartDate(NoticeTimeParseUtil.localDateFormat.print(item.getStartTime().getTime()));
            courseInfoResp.setEndDate(NoticeTimeParseUtil.localDateFormat.print(item.getEndTime().getTime()));
            courseInfoResp.setCreateDate(NoticeTimeParseUtil.localDateFormat.print(item.getCreateTime().getTime()));
            courseInfoResp.setUpdateDate(NoticeTimeParseUtil.localDateFormat.print(item.getUpdateTime().getTime()));
            courseInfoResps.add(courseInfoResp);
        });
    }
    /**
     * model --> courseInfo
     * @param req
     * @return
     */
    private static CourseInfo getCourseInfo(CourseInfoReq.Model req) throws BizException{
        CourseInfo courseInfo = new CourseInfo();
        formatStartTime(req);
        courseInfo.setClassTitle(req.getClassTitle());
        courseInfo.setClassId(Long.valueOf(req.getClassId()));
        courseInfo.setSection(req.getSection());
        courseInfo.setStartTime(new Timestamp(Long.valueOf(req.getStartTime())));
        parseCourseStartTimeIllegal(courseInfo.getStartTime().getTime());
        courseInfo.setEndTime(new Timestamp(Long.valueOf(req.getEndTime())));
        courseInfo.setIsLive(req.getIsLive());
        courseInfo.setTeacher(req.getTeacher());
        courseInfo.setClassImg(req.getClassImg());
        courseInfo.setLiveId(Long.valueOf(req.getId()));
        courseInfo.setCreateTime(new Timestamp(System.currentTimeMillis()));
        courseInfo.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        courseInfo.setCreator(0L);
        courseInfo.setModifier(0L);
        courseInfo.setBizStatus(1);
        courseInfo.setStatus(NoticeStatusEnum.NORMAL.getValue());
        JobScannedEnum jobScannedEnum = NoticeTimeParseUtil.obtainScannedEnumWhenCourseCreateOrUpdate(courseInfo.getStartTime().getTime());
        courseInfo.setScanned(jobScannedEnum.getValue());
        return courseInfo;
    }

    /**
     * 格式化php过来的时间格式
     * @param req
     */
    private static void formatStartTime(CourseInfoReq.Model req){

        if(req.getStartTime().length() == PHP_TIME_LENGTH){
            req.setStartTime(req.getStartTime() + PHP_JAVA_TIME);
        }
        if(req.getEndTime().length() == PHP_TIME_LENGTH){
            req.setEndTime(req.getEndTime() + PHP_JAVA_TIME);
        }
    }


    /**
     * 校验课程开课时间是否合法
     *
     * @param startTime
     */
    private static void parseCourseStartTimeIllegal(long startTime)throws BizException{
        final long currentTime = System.currentTimeMillis();
        long difference = NoticeTimeParseUtil.COURSE_READY_MINIMUM_DATE
                + NoticeTimeParseUtil.DEVIATION_DATE_TIME
                + NoticeTimeParseUtil.COURSE_USER_FETCH_INTERVAL;

        if(startTime - currentTime < difference){
            throw new BizException(NoticePushErrors.COURSE_START_TIME_ILLEGAL);
        }
    }

    /**
     * 批量删除直播课信息
     *
     * @param liveIds
     * @return
     * @throws BizException
     */
    @Override
    public Object removeCourseInfoBatch(String liveIds) throws BizException {

       List<String> list = Arrays.asList(liveIds.split(","));
       if(CollectionUtils.isEmpty(list)){
          return SuccessMessage.create("数据为空！");
       }else{
           list.forEach(item -> {
               Long liveId = Long.valueOf(item);
               removeCourseInfo(liveId);
           });
       }
       return SuccessMessage.create("操作成功");
    }


    /**
     * 根据 id 查询 课程信息
     *
     * @param liveId
     * @return
     * @throws BizException
     */
    @Override
    public CourseInfo findCourseById(long liveId) throws BizException {
        Example example = new Example(CourseInfo.class);
        example.and()
                .andEqualTo("liveId", liveId)
                .andEqualTo("status", NoticeStatusEnum.NORMAL.getValue());
        return courseInfoMapper.selectOneByExample(example);
    }
}
