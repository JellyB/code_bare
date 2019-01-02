package com.huatu.tiku.push.constant;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 描述：
 *
 * @author biguodong
 * Create time 2018-11-13 上午10:30
 **/
@Configuration
public class NoticePushRabbitConfig {

    @Bean
    public Queue getNoticePushLanding(){
        return new Queue(RabbitMqKey.NOTICE_PUSH_LANDING);
    }

    @Bean
    public Queue getNoticeUserStoring(){
        return new Queue(RabbitMqKey.NOTICE_USER_STORING);
    }

    @Bean
    public Queue getNoticeCourseUserInfoUName(){
        return new Queue(RabbitMqKey.NOTICE_COURSE_USER_INFO_UNAME);
    }

    @Bean
    public Queue getNoticeCourseUserInfoUiD(){
        return new Queue(RabbitMqKey.NOTICE_COURSE_USER_INFO_UiD);
    }

    @Bean
    public Queue getNoticeCorrectFeedback(){
        return new Queue(RabbitMqKey.NOTICE_FEEDBACK_CORRECT);
    }

    @Bean
    public Queue getNoticeSuggestFeedback(){
        return new Queue(RabbitMqKey.NOTICE_FEEDBACK_SUGGEST);
    }


    @Bean
    public Queue getNoticeLandingHikariCpTest(){
        return new Queue(RabbitMqKey.NOTICE_USER_LANDING_HIKARICP_TEST);
    }
}
