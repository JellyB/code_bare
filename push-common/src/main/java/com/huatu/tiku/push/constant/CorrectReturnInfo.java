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
public class CorrectReturnInfo implements Serializable {

    //业务id 答题卡id
    private long bizId;
    //试题类型 0 单题 1 套题
    private int topicType;

    private long answerCardId;

    private Date submitTime;

    private long userId;

    private String returnContent;

    private Date dealDate;

    @Builder
    public CorrectReturnInfo(long bizId, int topicType, long answerCardId, Date submitTime, long userId, String returnContent, Date dealDate) {
        this.bizId = bizId;
        this.topicType = topicType;
        this.answerCardId = answerCardId;
        this.submitTime = submitTime;
        this.userId = userId;
        this.returnContent = returnContent;
        this.dealDate = dealDate;
    }
}
