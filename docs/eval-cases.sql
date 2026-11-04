-- ============================================================
-- Day6 · 30 条评测集（25 可答 + 5 不可答）
-- 用法：IDEA 里打开本文件 → Ctrl+A 全选 → Ctrl+Enter 执行
--
-- 设计要点：只存 anchor（期望块里的一段稳定原文），不存 chunk id
--   实测：同一批 7 篇文档，粒度从 800 改成 300 再改回来，
--   chunk id 从 34~53 变成 55~110 又变成 111~130。
--   anchor 是文档原文，永远不变 → 靠它反查，评测集才能跨粒度复用。
-- ============================================================

USE ai_knowledge_base;

-- ① 加 anchor 列
--    !!! 如果报 1060 Duplicate column name 'anchor'，说明列已存在，
--    !!! 直接跳过本段、从 ② 开始执行。mysql 客户端遇到错误会中断整个脚本，
--    !!! 不跳过的话 ③ 的 INSERT 根本不会执行，评测集还是空的（11/04 踩过）
ALTER TABLE eval_case ADD COLUMN anchor VARCHAR(120) NULL
    COMMENT '期望块内的一段稳定原文，换粒度后靠它重算 chunk id' AFTER expected_chunk_id;

-- ② 清空旧用例
DELETE FROM eval_case;

-- ③ 写入 30 条（answerable=1 可答 / 0 应拒答）
INSERT INTO eval_case(question, answerable, anchor, note) VALUES
('RAG 的核心思路是什么', 1, '检索增强生成', 'A1 原词命中'),
('路径穿越漏洞怎么防', 1, '路径穿越', 'A2 原词命中'),
('段落感知切分为什么不加 overlap', 1, '段落边界本身就是语义边界', 'A3 原词命中'),
('IDEA 社区版怎么执行 SQL 脚本', 1, '社区版IDEA缺少完整Database Tools插件', 'A4 原词命中'),
('最长连续序列怎么优化到 O(n)', 1, '判断集合里面是否不存在num-1', 'A5 原词命中'),
('LlamaIndex 这类框架有什么问题', 1, '企业落地经常需要自定义改造', 'A6 原词命中'),
('为什么要用 UUID 做磁盘文件名', 1, '生产项目一般使用UUID作为磁盘存储文件名', 'A7 原词命中'),
('embedding 是什么', 1, '把自然语言文本转换成固定维度的数字数组', 'A8 原词命中'),
('怎么防止用户传上来的文件写到别的目录', 1, '跳出目标目录', 'B1 改写：路径穿越'),
('把文字转成数字数组到底有什么用', 1, '语义相近的两段文字，计算出来的向量距离就会比较近', 'B2 改写：embedding'),
('面试一问底层原理就答不上来怎么办', 1, '项目用来证明工程实践能力', 'B3 改写：项目 vs 八股'),
('文本切得太长会有什么坏处', 1, '单个chunk里面包含太多无关内容', 'B4 改写：块太大'),
('两个切分方案块数一样，差距在哪', 1, '差别在边界质量', 'B5 改写：边界质量'),
('服务端该不该直接信任用户提供的路径', 1, '如果直接使用用户原始文件名拼接磁盘存储路径', 'B6 改写：不信任路径'),
('只看视频不动手会怎样', 1, '只看教程视频可以理解大概', 'B7 改写：动手实践'),
('为什么切分要保留一部分重复文本', 1, '相邻两个chunk保留一部分重复文本', 'B8 改写：overlap'),
('定长切分比段落感知多出多少字符', 1, '2044 − 1844 = 200', 'C1 数字 200'),
('段落感知的块长极差是多少', 1, '极差', 'C2 数字 437'),
('Java 在 JD 里出现几次', 1, 'Java | 17 | 100', 'C3 频次 17'),
('MySQL 在 JD 里的占比是多少', 1, 'MySQL | 14 | 82', 'C4 占比 82%'),
('可投率大概多少', 1, '可投率约', 'C5 30%'),
('JD 是什么意思', 1, 'Job Description', 'D1 术语'),
('有多少岗位要求每周 5 天', 1, '15/17 要求 5 天/周', 'D2 15/17'),
('有岗位要求会用 Cursor 吗', 1, '熟练使用 Cursor', 'D3 AI 工具'),
('哪些公司不卡届', 1, '字节（全体在校生）', 'D4 不卡届'),
('红烧肉怎么做', 0, NULL, 'E1 语料外·菜谱'),
('Kubernetes 怎么做滚动更新', 0, NULL, 'E2 语料外·K8s'),
('Python 装饰器的原理', 0, NULL, 'E3 语料外·Python'),
('go 的 goroutine 和线程有什么区别', 0, NULL, 'E4 语料外·Go'),
('今天上证指数收盘多少点', 0, NULL, 'E5 语料外·行情');

-- ④ 用 anchor 反查当前粒度下的 chunk id（子查询取最小 id 兜底）
UPDATE eval_case e
   SET e.expected_chunk_id = (
       SELECT MIN(c.id) FROM chunk c
        WHERE c.strategy = 'paragraph'
          AND c.content LIKE CONCAT('%', e.anchor, '%')
   )
 WHERE e.answerable = 1;

-- ⑤ 核对：应 2 行，可答题 25 条全部有 id，不可答 5 条为 0
SELECT answerable AS 可答, COUNT(*) AS 条数, COUNT(expected_chunk_id) AS 已填id
FROM eval_case GROUP BY answerable;

-- ⑥ 没反查到 id 的（应为空，否则 anchor 写错或该块已被重切成两半）
SELECT id, note, anchor FROM eval_case
 WHERE answerable = 1 AND expected_chunk_id IS NULL;

-- ⑦ 一个 anchor 命中多块的情况（应全部为 1，>1 说明 anchor 不够唯一）
SELECT e.note, COUNT(c.id) AS 命中块数
  FROM eval_case e JOIN chunk c
    ON c.strategy = 'paragraph' AND c.content LIKE CONCAT('%', e.anchor, '%')
 WHERE e.answerable = 1
 GROUP BY e.id, e.note HAVING COUNT(c.id) > 1;