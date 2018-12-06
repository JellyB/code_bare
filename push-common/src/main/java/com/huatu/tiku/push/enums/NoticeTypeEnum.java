package com.huatu.tiku.push.enums;

import com.huatu.tiku.push.constant.CourseParams;
import com.huatu.tiku.push.constant.FeedBackParams;
import com.huatu.tiku.push.constant.MockParams;
import com.huatu.tiku.push.constant.OrderParams;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-07 下午1:24
 **/

@AllArgsConstructor
@Getter
public enum NoticeTypeEnum{

    COURSE_REMIND("%s 您有一节直播课，不要迟到哦~", "主讲老师:  %s", NoticeParentTypeEnum.COURSE, CourseParams.REMIND),
    COURSE_READY("您的直播课马上开始，立即进入！", "",  NoticeParentTypeEnum.COURSE, CourseParams.READY),
    MOCK_ONLINE("%s报名开始啦~", "", NoticeParentTypeEnum.MOCK, MockParams.ON_LINE),
    MOCK_REMIND("%马上开考！", "", NoticeParentTypeEnum.MOCK, MockParams.REMIND),
    MOCK_REPORT("%报告出炉", "", NoticeParentTypeEnum.MOCK, MockParams.REPORT),
    ORDER_SEND("发货提醒", "", NoticeParentTypeEnum.ORDER, OrderParams.SEND),
    ORDER_SIGN("订单已签收", "", NoticeParentTypeEnum.ORDER, OrderParams.SIGN),
    CORRECT_FEEDBACK("您的纠错有反馈啦~", "%s，图币奖励在【我的图币】-【账户明细】查看哦~", NoticeParentTypeEnum.FEEDBACK, FeedBackParams.CORRECT),
    SUGGEST_FEEDBACK("您的建议有反馈啦~", "非常感谢您的宝贵意见!", NoticeParentTypeEnum.FEEDBACK, FeedBackParams.SUGGEST);

    private String title;

    private String text;

    private NoticeParentTypeEnum type;

    private String detailType;

}