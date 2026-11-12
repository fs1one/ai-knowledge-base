package com.first.aikb.qa;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.first.aikb.entity.Chunk;
import com.first.aikb.mapper.ChunkMapper;
import com.first.aikb.search.ChunkIndex;
import com.first.aikb.search.SearchHit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    /** 固定拒答话术，必须和 prompt 里写死的一字不差 —— 评测脚本靠字符串匹配判分 */
    public static final String REFUSE = "知识库中没有找到相关内容";

    /** 匹配回答里的引用标号，用于剥掉模型自己编的越界编号 */
    private static final Pattern CITE = Pattern.compile("\\[(\\d+)]");

    private final ChunkIndex chunkIndex;
    private final ChunkMapper chunkMapper;
    private final LlmClient llmClient;

    @Value("${app.qa.top-k:3}")
    private int topK;

    @Value("${app.qa.max-tokens:512}")
    private int maxTokens;

    @Value("${app.qa.max-context-chars:2000}")
    private int maxContextChars;

    public QaResult ask(String question) {
        long start = System.currentTimeMillis();

        // 泛型必须写全：写成裸 List 的话，下面 hits.get(i) 取出来是 Object，编译不过
        List<SearchHit> hits = chunkIndex.search(question, topK);
        if (hits.isEmpty()) {
            return new QaResult(REFUSE, List.of(), System.currentTimeMillis() - start);
        }

        List<QaResult.Source> sources = loadSources(hits);
        String prompt = buildPrompt(question, sources);
        String answer = llmClient.chat(prompt, maxTokens);

        // 三步后处理，顺序不能换：
        // ① 剥越界编号 ② 语义拒答归一 ③ 补来源行（拒答时第 ③ 步会自动跳过）
        answer = sanitizeCitations(answer, sources.size());
        answer = normalizeRefusal(answer);
        answer = appendSources(answer, sources);

        return new QaResult(answer, sources, System.currentTimeMillis() - start);
    }

    /**
     * 按 hits 的顺序取全文。两个坑都在这里：
     * ① 一次 IN 查询，不能 for 循环单查（N+1 次数据库往返）
     * ② 必须按 hits 顺序重排 —— IN 查询的返回顺序不保证与传入 ids 一致，
     *    直接 zip 会让 [1] 指向第 3 块的内容，而且不报错，只有人工核对才发现
     */
    private List<QaResult.Source> loadSources(List<SearchHit> hits) {
        List<Long> ids = hits.stream().map(SearchHit::chunkId).toList();

        Map<Long, Chunk> byId = new LinkedHashMap<>();
        for (Chunk c : chunkMapper.selectList(
                new LambdaQueryWrapper<Chunk>().in(Chunk::getId, ids))) {
            byId.put(c.getId(), c);
        }

        List<QaResult.Source> sources = new ArrayList<>(hits.size());
        int budget = maxContextChars;
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            Chunk c = byId.get(h.chunkId());
            if (c == null) {
                continue;   // 块被删了（改粒度后重 parse），跳过但不打乱编号
            }
            String text = c.getContent();
            if (text.length() > budget) {
                text = text.substring(0, Math.max(0, budget));
            }
            budget -= text.length();
            sources.add(new QaResult.Source(sources.size() + 1, c.getId(), c.getDocId(),
                    h.score(), text));
            if (budget <= 0) {
                break;
            }
        }
        return sources;
    }

    private String buildPrompt(String question, List<QaResult.Source> sources) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("你是一个只依据给定资料回答问题的助手。\n\n")
          .append("规则：\n")
          .append("1. 只能使用下面「资料」中的内容回答，禁止使用你自己的知识补充。\n")
          .append("2. 不要复述问题，不要写「根据资料」这类开场白，直接给答案。\n\n")
          .append("资料：\n");
        for (QaResult.Source s : sources) {
            sb.append('[').append(s.index()).append("] ").append(s.content()).append('\n');
        }
        // 拒答指令放在最靠后的位置（紧挨着「回答：」）——
        // 实测 1.5B 对远离生成位置的规则遵循率明显下降，放在开头会自己发挥成
        // 「抱歉，我无法提供…」，放在末尾才会照抄这句固定话术
        sb.append("\n问题：").append(question).append("\n\n")
          .append("注意：如果上面的资料里没有能回答这个问题的内容，")
          .append("只输出这一句话，不要加任何其他文字：").append(REFUSE).append("\n\n")
          .append("回答：");
        return sb.toString();
    }

    /**
     * 把「语义上的拒答」统一成固定话术。
     * 即使把规则放在最后，小模型仍可能说出「抱歉，我无法提供…」这类同义表达，
     * 意图是对的但格式不对，导致 appendSources 误加「依据」后缀、评测脚本无法精确匹配。
     * 这一步是兜底，不是替代 prompt —— 两者都要有。
     */
    private static final String[] REFUSE_HINTS = {
            "无法提供", "无法回答", "不在我的知识范围", "不在知识范围",
            "没有找到相关", "资料中没有", "没有相关信息", "资料里没有"
    };

    private String normalizeRefusal(String answer) {
        if (answer == null || answer.isBlank()) {
            return REFUSE;
        }
        // 已经是对的就不动
        if (REFUSE.equals(answer.trim())) {
            return REFUSE;
        }
        for (String hint : REFUSE_HINTS) {
            if (answer.contains(hint)) {
                log.info("拒答归一：命中关键词「{}」，原始回答前 40 字：{}", hint,
                        answer.length() > 40 ? answer.substring(0, 40) : answer);
                return REFUSE;
            }
        }
        return answer;
    }

    /**
     * 剥掉超过 sources 数量的引用标号，防止模型自己编 [4] [5]。
     * 实测 1.5B 根本不标号，这个方法目前是防御性的 —— 换大模型后才有用。
     */
    private String sanitizeCitations(String answer, int max) {
        if (answer == null || answer.isBlank()) {
            return REFUSE;
        }
        Matcher m = CITE.matcher(answer);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            m.appendReplacement(sb, n >= 1 && n <= max ? Matcher.quoteReplacement(m.group()) : "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 1.5B 实测不会自己标 [1][2]，所以在末尾统一列来源。
     * 好处是用户仍能追溯，代价是不能精确到句 —— 这个取舍比换 7B 模型划算。
     * 拒答时不加后缀，保持和 REFUSE 完全相等，方便自动判分。
     */
    private String appendSources(String answer, List<QaResult.Source> sources) {
        if (sources.isEmpty() || REFUSE.equals(answer)) {
            return answer;
        }
        StringBuilder nums = new StringBuilder();
        for (QaResult.Source s : sources) {
            if (nums.length() > 0) {
                nums.append('、');
            }
            nums.append(s.index());
        }
        log.info("问答完成：{} 条来源，回答 {} 字", sources.size(), answer.length());
        return answer + "\n\n（依据：资料 " + nums + "）";
    }
}
