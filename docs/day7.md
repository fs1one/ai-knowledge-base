## 11/10 Day7（跨 11/05 与 11/10 两天）
- 做了什么：抽出 EmbeddingService 接口（加 prepare 默认方法），新增 OllamaEmbeddingService
  （HTTP 调本地 bge 服务）+ DenseVectorCodec（Base64 float32 存 MySQL JSON 列），
  CHUNK_SIZE 800 -> 400（块数 20 -> 37）
- 四组数字：TF-IDF@800: 68/84/0.753 | bge-small@400: 72/92/0.800 | 拒答 0/5
- 踩过的坑（这段最值钱）：
  ① MySQL 的 JSON 列不接受裸 Base64，必须包成 JSON 字符串字面量，否则 ERROR 3141
  ② RestClient.body(Map) 会把 body 截成十几字节，改成手拼 JSON 字符串才正常
  ③ transformers 5.x 移除了慢速 tokenizer，bge-small-zh 必须配 transformers 4.x
  ④ sentence-transformers 启动会去 huggingface.co 检查更新，国内必须设 HF_HUB_OFFLINE=1
- 被证实的假设：语义向量 + 小块对 recall@3 有明显收益（+8pt）
- 被推翻的假设：相似度阈值拒答不可行，三种配置下分数分布全部重叠
- 卡在哪：① ___
- 明天第一步：接 LLM 做引用回答，prompt 里硬约束拒答话术
