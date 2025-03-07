package cn.ruleengine.core.rule.strategy;

import cn.ruleengine.core.Input;
import cn.ruleengine.core.RuleEngineConfiguration;
import cn.ruleengine.core.rule.Rule;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 * <p>
 * 当有一个规则执行结果被命中，则终止执行
 *
 * @author 丁乾文
 * @create 2020/12/29
 * @since 1.0.0
 */
@Slf4j
public class WhenARuleIsHitStrategy implements RuleSetStrategy {

    private static final WhenARuleIsHitStrategy WHEN_A_RULE_IS_HIT_STRATEGY = new WhenARuleIsHitStrategy();

    private WhenARuleIsHitStrategy() {
    }

    public static WhenARuleIsHitStrategy getInstance() {
        return WHEN_A_RULE_IS_HIT_STRATEGY;
    }


    @Override
    public List<Object> compute(List<Rule> rules, Input input, RuleEngineConfiguration configuration) {
        for (Rule rule : rules) {
            log.debug("执行规则：" + rule.getName());
            Object action = rule.execute(input, configuration);
            // 当有一个规则执行结果被命中，则终止执行
            if (action != null) {
                if (log.isDebugEnabled()) {
                    log.debug("规则：{} 命中结果：{}", rule.getName(), action);
                }
                return Collections.singletonList(action);
            }
        }
        return null;
    }

}
