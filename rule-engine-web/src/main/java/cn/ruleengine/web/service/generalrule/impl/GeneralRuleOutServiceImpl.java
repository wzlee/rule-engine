package cn.ruleengine.web.service.generalrule.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.ruleengine.web.service.RuleEngineOutService;
import cn.ruleengine.web.vo.output.BatchExecuteRequest;
import cn.ruleengine.web.vo.generalrule.BatchExecuteRuleResponse;
import cn.ruleengine.web.vo.output.ExecuteRequest;
import cn.ruleengine.web.vo.output.IsExistsRequest;
import cn.ruleengine.web.vo.workspace.AccessKey;
import cn.ruleengine.core.DefaultInput;
import cn.ruleengine.core.Engine;
import cn.ruleengine.core.Input;
import cn.ruleengine.core.exception.EngineException;
import cn.ruleengine.core.exception.ValidException;
import cn.ruleengine.web.service.WorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2020/8/22
 * @since 1.0.0
 */
@Primary
@Slf4j
@Service
public class GeneralRuleOutServiceImpl implements RuleEngineOutService {

    @Resource
    private Engine engine;
    @Resource
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private WorkspaceService workspaceService;

    /**
     * 执行单个规则，获取执行结果
     *
     * @param executeRule 执行规则入参
     * @return 规则执行结果
     */
    @Override
    public Object execute(ExecuteRequest executeRule) {
        log.info("开始执行普通规则，入参：{}", executeRule);
        String workspaceCode = executeRule.getWorkspaceCode();
        AccessKey accessKey = this.workspaceService.accessKey(workspaceCode);
        if (!accessKey.equals(executeRule.getAccessKeyId(), executeRule.getAccessKeySecret())) {
            throw new ValidException("AccessKey Verification failed");
        }
        Input input = new DefaultInput();
        input.putAll(executeRule.getParam());
        return this.engine.execute(input, workspaceCode, executeRule.getCode());
    }

    /**
     * 批量执行多个规则(一次最多2000个)，获取执行结果
     *
     * @param batchExecuteRequest 执行规则入参
     * @return 规则执行结果
     */
    @Override
    public Object batchExecute(BatchExecuteRequest batchExecuteRequest) {
        String workspaceCode = batchExecuteRequest.getWorkspaceCode();
        AccessKey accessKey = this.workspaceService.accessKey(workspaceCode);
        if (!accessKey.equals(batchExecuteRequest.getAccessKeyId(), batchExecuteRequest.getAccessKeySecret())) {
            throw new ValidException("AccessKey Verification failed");
        }
        List<BatchExecuteRequest.ExecuteInfo> executeInfos = batchExecuteRequest.getExecuteInfos();
        Integer threadSegNumber = batchExecuteRequest.getThreadSegNumber();
        log.info("批量执行规则数量：{},单个线程执行{}条规则", executeInfos.size(), threadSegNumber);
        List<BatchExecuteRuleResponse> outPuts = new CopyOnWriteArrayList<>();
        int countDownNumber = executeInfos.size() % threadSegNumber == 0 ? executeInfos.size() / threadSegNumber : (executeInfos.size() / threadSegNumber) + 1;
        CountDownLatch countDownLatch = ThreadUtil.newCountDownLatch(countDownNumber);
        // 批量插入，执行一次批量
        for (int fromIndex = 0; fromIndex < executeInfos.size(); fromIndex += threadSegNumber) {
            int toIndex = Math.min(fromIndex + threadSegNumber, executeInfos.size());
            List<BatchExecuteRequest.ExecuteInfo> infoList = executeInfos.subList(fromIndex, toIndex);
            BatchExecuteGeneralRuleTask batchExecuteRuleTask = new BatchExecuteGeneralRuleTask(workspaceCode, countDownLatch, outPuts, engine, infoList);
            this.threadPoolTaskExecutor.execute(batchExecuteRuleTask);
        }
        // 等待线程处理完毕
        try {
            Long timeout = batchExecuteRequest.getTimeout();
            if (timeout.equals(-1L)) {
                countDownLatch.await();
            } else {
                if (!countDownLatch.await(timeout, TimeUnit.MILLISECONDS)) {
                    throw new EngineException("Execution timeout:{}ms", timeout);
                }
            }
        } catch (InterruptedException e) {
            throw new EngineException("Execution failed, rule execution thread was interrupted", e);
        }
        return outPuts;
    }

    /**
     * 引擎中是否存在这个规则
     *
     * @param isExistsRequest 参数
     * @return true存在
     */
    @Override
    public Boolean isExists(IsExistsRequest isExistsRequest) {
        String workspaceCode = isExistsRequest.getWorkspaceCode();
        AccessKey accessKey = this.workspaceService.accessKey(workspaceCode);
        if (!accessKey.equals(isExistsRequest.getAccessKeyId(), isExistsRequest.getAccessKeySecret())) {
            throw new ValidException("AccessKey Verification failed");
        }
        return this.engine.isExists(isExistsRequest.getWorkspaceCode(), isExistsRequest.getCode());
    }

}
