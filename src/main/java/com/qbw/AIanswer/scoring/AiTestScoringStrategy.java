package com.qbw.AIanswer.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.qbw.AIanswer.manager.AiManager;
import com.qbw.AIanswer.model.dto.question.QuestionAnswerDTO;
import com.qbw.AIanswer.model.dto.question.QuestionContentDTO;
import com.qbw.AIanswer.model.entity.App;
import com.qbw.AIanswer.model.entity.Question;
import com.qbw.AIanswer.model.entity.UserAnswer;
import com.qbw.AIanswer.model.vo.QuestionVO;
import com.qbw.AIanswer.service.QuestionService;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 测评类应用评分策略
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@ScoringStrategyConfig(appType = 1, scoringStrategy = 1)
public class AiTestScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedissonClient redissonClient;

    // 分布式锁的 key
    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";

    /**
     * AI 评分结果本地缓存
     */
    private final Cache<String, String> answerCacheMap =
            Caffeine.newBuilder().initialCapacity(1024)
                    // 缓存 5 分钟移除
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .build();

    /**
     * AI 评分系统消息
     */
    private static final String AI_TEST_SCORING_SYSTEM_MESSAGE = "你是一位严谨的判题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "题目和用户回答的列表：格式为 [{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来对用户进行评价：\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，大于 200 字）\n" +
            "2. 严格按照下面的 json 格式输出评价名称和评价描述\n" +
            "```\n" +
            "{\"resultName\": \"评价名称\", \"resultDesc\": \"评价描述\"}\n" +
            "```\n" +
            "3. 返回格式必须为 JSON 对象";

    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        Long appId = app.getId();
        String jsonStr = JSONUtil.toJsonStr(choices);
        String cacheKey = buildCacheKey(appId, jsonStr);
        // 1. 先从缓存中获取
        UserAnswer answerJson = getUserAnswerFromCache(app, cacheKey);
        if (answerJson != null) return answerJson;
        // 2. 缓存中没有，则调用 AI 获取结果
        // 使用redissonClient的分布式锁,防止同一时刻 多个用户对同一个应用产生的相同选项结果集
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + cacheKey);
        // 根据 id 查询到题目
        try {
            // 竞争锁
            boolean tryLock = lock.tryLock(3, 15, TimeUnit.SECONDS);
            // 没抢到锁，会查询缓存是否已经被其他用户的AI判题结果写入
            if(!tryLock){
                // 判断该选项集合是否已经被AI判题过，如果已经判过，则直接从缓存中获取，没有抢到锁也会从缓存中查一次
                UserAnswer userAnswer = getUserAnswerFromCache(app, cacheKey);
                if (userAnswer != null) return userAnswer;
            }
            // 抢到锁以后，也要从缓存中查一次，防止有人提交过相同选项集，且已经写入缓存
            UserAnswer userAnswerAfterlock = getUserAnswerFromCache(app, cacheKey);
            if (userAnswerAfterlock != null) return userAnswerAfterlock;
            // 否则进行AI判题
            Question question = questionService.getOne(
                    Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, appId)
            );
            QuestionVO questionVO = QuestionVO.objToVo(question);
            List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();

            // 调用 AI 获取结果
            // 封装 Prompt
            String userMessage = getAiTestScoringUserMessage(app, questionContent, choices);
            // AI 生成
            String result = aiManager.doSyncStableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, userMessage);
            // 截取需要的 JSON 信息
            int start = result.indexOf("{");
            int end = result.lastIndexOf("}");
            String json = result.substring(start, end + 1);

            // 缓存结果
            answerCacheMap.put(cacheKey, json);

            // 3. 构造返回值，填充答案对象的属性
            UserAnswer userAnswer = JSONUtil.toBean(json, UserAnswer.class);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            return userAnswer;
        } finally {
            if(lock!= null && lock.isLocked() && lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

    @Nullable
    private UserAnswer getUserAnswerFromCache(App app, String cacheKey) {
        String answerJson = answerCacheMap.getIfPresent(cacheKey);
        if (StrUtil.isNotBlank(answerJson)) {
            // 构造返回值，填充答案对象的属性
            UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            return userAnswer;
        }
        return null;
    }

    /**
     * AI 评分用户消息封装
     *
     * @param app
     * @param questionContentDTOList
     * @param choices
     * @return
     */
    private String getAiTestScoringUserMessage(App app, List<QuestionContentDTO> questionContentDTOList, List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        List<QuestionAnswerDTO> questionAnswerDTOList = new ArrayList<>();
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i)); //设个默认值
            for(QuestionContentDTO.Option option: questionContentDTOList.get(i).getOptions()){
                if(option.getKey().equals(choices.get(i))){
                    questionAnswerDTO.setUserAnswer(option.getValue());
                    break;
                }
            }
            questionAnswerDTOList.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOList));
        return userMessage.toString();
    }

    private String buildCacheKey(Long appId, String choices){
        return DigestUtil.md5Hex(appId + ":" + choices);
    }

}
