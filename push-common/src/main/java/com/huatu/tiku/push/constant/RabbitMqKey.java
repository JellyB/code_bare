package com.huatu.tiku.push.constant;


/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-13 上午9:50
 **/
public class RabbitMqKey {

    public static final long NOTICE_STORE_THRESHOLD = 100;

    public static final long USER_STORE_THRESHOLD = 100;

    public static final long PUSH_STRATEGY_THRESHOLD = 500;

    public static final String NOTICE_PUSH_LANDING = "notice_push_landing";

    public static final String NOTICE_USER_STORING = "notice_push_storing";

    public static final String NOTICE_USER_LANDING_HIKARICP_TEST = "notice_push_landing_HikariCp_test";

    public static final String NOTICE_FEEDBACK_CORRECT = "notice_push_feedback_correct";

    //申论批改退回队列
    public static final String NOTICE_CORRECT_RETURN = "notice_push_correct_return";
    //申论批改查看报告队列
    public static final String NOTICE_CORRECT_REPORT = "notice_push_correct_report";

    public static final String NOTICE_CORRECT_COURSE_WORK = "notice_push_correct_course_work";

    public static final String NOTICE_FEEDBACK_SUGGEST = "notice_push_feedback_suggest";

    public static final String NOTICE_COURSE_USER_INFO_UNAME = "notice_course_userInfo_uName";

    public static final String NOTICE_COURSE_USER_INFO_UiD = "notice_course_userInfo_uId";

    /**
     * 直播结束后定时任务通知
     */
    public static final String LIVE_COURSE_END_NOTICE = "live.course.end.notice";
    /**
     * 数据迁移队列
     */

    @Deprecated
    public static final String NOTICE_DATA_MOVE_FROM_PANDORA_2_PUSH = "notice_data_move_from_pandora_2_push";

}
