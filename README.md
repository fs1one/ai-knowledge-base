# AI Knowledge Base

个人文档知识库：上传文档 → 切分 → 向量化 → 检索 → 带引用回答。

## 技术栈
- Java 17 / Spring Boot 4.1.1 / MyBatis-Plus 3.5.17
- MySQL 8（向量先存 JSON，v1 用内存余弦相似度）
- LLM：手写 HTTP 调国产 API（不引 Spring AI）

## 计划功能
- [ ] 文档上传与解析（PDF / MD / TXT）
- [ ] 两种切分策略对比（定长 vs 结构感知）
- [ ] Top-K 检索 + 带引用回答
- [ ] 30 条评测集 + 召回率基线

## 设计记录
技术选型的「它的做法 / 我的做法 / 为什么」见 `docs/decisions.md`。
