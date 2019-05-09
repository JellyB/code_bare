package com.huatu.tiku.push.util;

import com.huatu.tiku.push.enums.JobScannedEnum;
import com.huatu.tiku.push.enums.NoticeParentTypeEnum;
import com.huatu.tiku.push.enums.NoticeTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-12 上午10:19
 **/
@Slf4j
public class NoticeTimeParseUtil {

    private static final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("MM-dd HH:mm");
    public static final DateTimeFormatter wholeDateFormat = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");
    public static final DateTimeFormatter localDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter simpleDateFormat = DateTimeFormat.forPattern("HH:mm");
    private static final DateTimeFormatter noSecondDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter dayTimeDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter minuteSecond = DateTimeFormat.forPattern("mm:ss");

    private static final long HOUR_1 = 60 * 60 * 1000L;
    private static final long MINUTE_30 = 30 * 60 * 1000L;
    private static final long MINUTE_10 = 10 * 60 * 1000L;
    private static final long MINUTE_5 = 5 * 60 * 1000L;
    private static final long MINUTE_3 = 3 * 60 * 1000L;
    private static final long MINUTE_1 = 1 * 60 * 1000L;
    /**
     * 提醒时间
     */
    public static final long COURSE_REMIND_MINIMUM_DATE = MINUTE_30;
    /**
     * 准备时间
     */
    public static final long COURSE_READY_MINIMUM_DATE = MINUTE_5;
    /**
     * 拉取用户信息时间
     */
    public static final long COURSE_USER_FETCH_INTERVAL = MINUTE_10;

    /**
     * boss job 的误差扫描时间；
     */
    public static final long DEVIATION_DATE_TIME = MINUTE_5;

    private static final String JUST_BEFORE = "刚刚";
    private static final String MANY_MINUTES_BEFORE = "%s分钟前";

    private static final String DEFAULT = "05-15 10:00";

    /**
     * 解析 返回给移动端的消息时间
     * @param noticeTime
     * @return
     */
    public static String parseTime(long noticeTime){
        Calendar todayCalender = Calendar.getInstance();
        todayCalender.setTime(new Date());
        todayCalender.set(Calendar.HOUR_OF_DAY, 0);
        todayCalender.set(Calendar.MINUTE, 0);
        todayCalender.set(Calendar.SECOND, 0);
        final Date today = todayCalender.getTime();

        Calendar yesterdayCalendar = Calendar.getInstance();
        yesterdayCalendar.setTime(today);
        yesterdayCalendar.set(Calendar.DAY_OF_MONTH, todayCalender.get(Calendar.DAY_OF_MONTH) -1);
        final Date yesterday = yesterdayCalendar.getTime();

        Calendar thisYear = Calendar.getInstance();
        thisYear.setTime(new Date());
        thisYear.set(todayCalender.get(Calendar.YEAR), 00, 01,0,0,0);
        final Date thisYearDay = thisYear.getTime();

        log.debug("today 0 clock:{}", localDateFormat.print(today.getTime()));
        log.debug("yesterday 0 clock:{}", localDateFormat.print(yesterday.getTime()));
        log.debug("this year's firs day:{}", localDateFormat.print(thisYearDay.getTime()));

        final long currentTime = System.currentTimeMillis();
        final long difference = currentTime - noticeTime;
        if(difference <= 0){
            return DEFAULT;
        }
        if(difference < MINUTE_1){
            return JUST_BEFORE;
        }else if(difference < HOUR_1 && difference > MINUTE_1){
            long minutes = difference/1000/60;
            return String.format(MANY_MINUTES_BEFORE, minutes);
        }else if((noticeTime > today.getTime()) && difference > HOUR_1){
            return simpleDateFormat.print(noticeTime);
        }else if(noticeTime < today.getTime() && noticeTime > yesterday.getTime()){
            return "昨天" + simpleDateFormat.print(noticeTime);
        }else if(noticeTime < yesterday.getTime() && noticeTime > thisYearDay.getTime()){
            return dateFormat.print(noticeTime);
        }else{
            return noSecondDateFormat.print(noticeTime);
        }
    }

    /**
     * 根据消息类型解析是否需要生成job
     * 以及要生成的job类型
     * @param starTime
     * @param noticeParentTypeEnum
     * @return
     */
    public static NoticeTypeEnum parseTime2NoticeType(long starTime, NoticeParentTypeEnum noticeParentTypeEnum, JobScannedEnum jobScannedEnum){
        log.info("start time:{}", wholeDateFormat.print(starTime));
        final long currentTime = System.currentTimeMillis();
        if(starTime < currentTime){
            return null;
        }
        switch (noticeParentTypeEnum){
            case COURSE:
                return judgeCourseAvailable(currentTime, starTime, jobScannedEnum);
            case MOCK:
                return judgeMockAvailable(currentTime, starTime);
                default:
                    return null;
        }
    }

    /**
     * 根据时间返回课程消息类型
     * @param currentTime
     * @param starTime
     * @param jobScannedEnum
     * @return
     */
    public static NoticeTypeEnum judgeCourseAvailable(long currentTime, long starTime, JobScannedEnum jobScannedEnum){
        final long difference = starTime - currentTime;
        switch (jobScannedEnum){
            case NOT_YET_SCANNED:
                if(difference < COURSE_REMIND_MINIMUM_DATE && difference > COURSE_READY_MINIMUM_DATE){
                    return NoticeTypeEnum.COURSE_READY;
                }else if(difference > COURSE_REMIND_MINIMUM_DATE){
                    return NoticeTypeEnum.COURSE_REMIND;
                }
                break;
            case REMIND_JOB_HAS_CREATED_OR_OBSOLETE:
                if(difference > COURSE_READY_MINIMUM_DATE){
                    return NoticeTypeEnum.COURSE_READY;
                }
                break;
                default:
                    return null;
        }
        return null;
    }

    /**
     * 根据课程创建时间生成job类型
     * 排除掉boss 扫描时间误差
     * @param startTime
     * @return
     */
    public static JobScannedEnum obtainScannedEnumWhenCourseCreateOrUpdate(long startTime){
        final long currentTime = System.currentTimeMillis();
        long difference = startTime - currentTime;
        difference = difference - DEVIATION_DATE_TIME;
        if(difference < 0){
            return JobScannedEnum.SCANNED_CAN_NOT_CREATE_JOB;
        }else if(difference < COURSE_REMIND_MINIMUM_DATE && difference > COURSE_READY_MINIMUM_DATE){
            return JobScannedEnum.REMIND_JOB_HAS_CREATED_OR_OBSOLETE;
        }else{
            return JobScannedEnum.NOT_YET_SCANNED;
        }
    }

    public static String noticeViewTime(Timestamp timestamp){
        return minuteSecond.print(timestamp.getTime());
    }
    /**
     * 根据时间返回模考消息类型
     * @param currentTime
     * @param time
     * @return
     */
    public static NoticeTypeEnum judgeMockAvailable(long currentTime, long time){
        return null;
    }
}
