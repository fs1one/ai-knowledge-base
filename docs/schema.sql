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
