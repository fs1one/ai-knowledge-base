-- ============================================================
-- AI Knowledge Base · 数据库结构 v1
-- 执行方式：IDEA 里打开本文件 → Ctrl+A 全选 → Ctrl+Enter
-- ============================================================

CREATE DATABASE IF NOT EXISTS ai_knowledge_base
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE ai_knowledge_base;

-- ① 文档表
CREATE TABLE IF NOT EXISTS doc (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL                  COMMENT '上传者',
  name        VARCHAR(255) NOT NULL                  COMMENT '原始文件名',
  size        BIGINT       NOT NULL DEFAULT 0        COMMENT '字节数',
  chunk_count INT          NOT NULL DEFAULT 0        COMMENT '切分块数，上传时先置 0',
  status      TINYINT      NOT NULL DEFAULT 0        COMMENT '0待解析 1解析中 2完成 3失败',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档';

-- ② 分块表（核心表）
CREATE TABLE IF NOT EXISTS chunk (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  doc_id         BIGINT   NOT NULL                   COMMENT '所属文档',
  seq            INT      NOT NULL                   COMMENT '块内序号，从 0 开始',
  content        TEXT     NOT NULL                   COMMENT '块正文',
  char_count     INT      NOT NULL DEFAULT 0,
  embedding_json JSON     NULL                       COMMENT '向量 JSON 数组，未向量化时为 NULL',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_doc_seq (doc_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块';

-- ③ 问答日志（评测 + 成本 + 延迟的唯一数据来源）
CREATE TABLE IF NOT EXISTS qa_log (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  question        VARCHAR(1000) NOT NULL,
  answer          TEXT          NOT NULL,
  cited_chunk_ids VARCHAR(255)  NULL                 COMMENT '命中的 chunk id，逗号分隔 12,15,18',
  latency_ms      INT           NOT NULL DEFAULT 0,
  token_in        INT           NOT NULL DEFAULT 0,
  token_out       INT           NOT NULL DEFAULT 0,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_created (created_at),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答日志';

-- ④ 评测用例
CREATE TABLE IF NOT EXISTS eval_case (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  question          VARCHAR(1000) NOT NULL,
  expected_chunk_id BIGINT        NULL               COMMENT '期望命中的块',
  answerable        TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '1 可答 / 0 应拒答',
  note              VARCHAR(500)  NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测用例';

ALTER TABLE doc ADD COLUMN stored_name VARCHAR(64) NULL COMMENT '磁盘文件名' AFTER name;

DELETE FROM doc WHERE name IN ('fake.pdf', 'blank.txt');
DELETE FROM chunk WHERE doc_id NOT IN (SELECT id FROM doc);

-- chunk 表：记录这一行是哪套策略切出来的；默认 fixed 是为了让 Day3 的旧数据不用回填
ALTER TABLE chunk ADD COLUMN strategy VARCHAR(16) NOT NULL DEFAULT 'fixed'
    COMMENT '切分策略：fixed 定长 / paragraph 段落感知' AFTER seq;

-- 同一文档同一策略下 seq 必须唯一，所以索引要把 strategy 带进去
ALTER TABLE chunk DROP INDEX idx_doc_seq;
ALTER TABLE chunk ADD KEY idx_doc_strategy_seq (doc_id, strategy, seq);

-- doc 表：记录这份文档最近一次用的是哪套（chunk_count 也跟着它的语义走）
ALTER TABLE doc ADD COLUMN strategy VARCHAR(16) NOT NULL DEFAULT 'fixed'
    COMMENT '最近一次切分策略' AFTER chunk_count;

USE ai_knowledge_base;

-- ① 两套是否并存
SELECT strategy, COUNT(*) AS 行数, MIN(seq) AS 起始seq, MAX(seq) AS 结束seq
FROM chunk WHERE doc_id = 10 GROUP BY strategy;

-- ② 对比表：块数 / 长度分布 / 边界完整性
SELECT strategy                                             AS 策略,
       COUNT(*)                                             AS 块数,
       ROUND(AVG(char_count))                               AS 平均长度,
       MIN(char_count)                                      AS 最短,
       MAX(char_count)                                      AS 最长,
       ROUND(100 * SUM(CASE WHEN TRIM(content) REGEXP '[。！？.!?；;…]$'
                                THEN 1 ELSE 0 END) / COUNT(*), 1) AS 句末结束占比
FROM chunk
WHERE doc_id = 10
GROUP BY strategy;

-- ③ 看块尾到底断在哪（人眼确认，这比数字有说服力）
SELECT strategy, seq, RIGHT(TRIM(content), 18) AS 块尾
FROM chunk WHERE doc_id = 10 ORDER BY strategy, seq;

-- ④ doc 表：应记录最后一次用的策略
SELECT id, name, chunk_count, strategy, status FROM doc WHERE id = 10;