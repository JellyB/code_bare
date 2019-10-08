package com.huatu.tiku.push.constant;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-14 上午9:40
 **/

@Getter
@Setter
@NoArgsConstructor
public class CorrectCourseWorkPushInfo implements Serializable {

    public static final String RETURN = "N";

    public static final String REPORT = "T";

    public static final Integer IS_LIVE = 1;

    public static final Integer IS_NOT_LIVE = 0;

    //业务id 答题卡id
    private long bizId;

    private long userId;

    private long netClassId;

    private long syllabusId;

    private long lessonId;

    /**
     * N 被退回
     * T 出报告
     */
    private String type;

    private Date submitTime;

    private String stem;

    private String returnContent;

    private String img;

    private int isLive;


    @Builder
    public CorrectCourseWorkPushInfo(long bizId, long userId, long netClassId, long syllabusId, long lessonId, String type, Date submitTime, String stem, String returnContent, String img, int isLive) {
        this.bizId = bizId;
        this.userId = userId;
        this.netClassId = netClassId;
        this.syllabusId = syllabusId;
        this.lessonId = lessonId;
        this.type = type;
        this.submitTime = submitTime;
        this.stem = stem;
        this.returnContent = returnContent;
        this.img = img;
        this.isLive = isLive;
    }
}
