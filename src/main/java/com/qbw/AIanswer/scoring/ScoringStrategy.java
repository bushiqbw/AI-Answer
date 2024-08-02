package com.qbw.AIanswer.scoring;

import com.qbw.AIanswer.model.entity.App;
import com.qbw.AIanswer.model.entity.UserAnswer;

import java.util.List;

/**
 * 评分策略
 *
 * @author <a href="https://github.com/bushiqbw">qbw</a>
 * @author <a href="https://github.com/bushiqbw/springboot-init-pure">springboot-init-pure</a>
 */
public interface ScoringStrategy {

    /**
     * 执行评分
     *
     * @param choices
     * @param app
     * @return
     * @throws Exception
     */
    UserAnswer doScore(List<String> choices, App app) throws Exception;
}