## 10/26 Day4
- 做了什么：给 chunk 和 doc 各加 strategy 字段，索引改成 (doc_id, strategy, seq)，
  ChunkWriter 的删除条件带上 strategy，两套切分结果能在同一份文档上并存；
  写了 ParagraphChunker：先按空行拆段，整篇没空行就降级按单换行，然后贪心合并到不超过 800，
  超长段落退回首刀 FixedSizeChunker 并保留 overlap；
  实测同一份 big.txt：fixed 3 块 444/800/681（SQL 口径句末 66.7%），
  paragraph 3 块 345/782/616（句末 100%）
- 卡在哪：三个。① 抄代码时 IDEA 把 flush 自动导成 jdk.jfr.internal.JVM.flush（JDK 内部包，不导出），
  编译报包不可见 —— 以后 Alt+Enter 先看包名，jdk.*/sun.* 一律不选；
  ② doc.http 里的 {{cmpDocId}} 一直没被赋值，Day4-1 的请求在 IDE 里就被拦下没发出去，
  查 http-requests-log 才发现 21:09 那条一片空白 —— 换成 docId 并加 client.log 才好定位；
  ③ overlap 判断抄成了 <= 0，overlap=0 会误抛，正确是 < 0
- 明天第一步：写 Embedding 接口，先只拿 10 条 chunk 试水，跑通「文本 → 向量 → 写 embedding_json」

### 代码复述（15 分钟机制，3–6 行，只写因果不贴代码）

<!-- 下面四个问题自己答，不要照抄卡片 —— 抄的复述等于没做，晚上会抽查 -->

1. ParagraphChunker.split 的输入可能是什么形态？（空文本 / CRLF / 没有空行 / 一整段超长无换行）
2. 一共几步？为什么必须是这个顺序？（归一化 → 拆段 → 贪心合并 → 超长回退）
3. 删掉归一化那步，会出什么具体故障？（拿 CRLF 的文件说，正则 \n\s*\n 为什么匹配不到）
4. 删掉超长回退分支，1900 字的单段会发生什么？（超过 embedding 上下文会怎样，丢的是哪部分）

<!-- 自己写满这四行，再删掉这段注释 -->
