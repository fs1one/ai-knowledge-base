## 10/23 Day3
- 做了什么：FixedSizeChunker 用 code point 切分（不是 charAt，中文才不会被切坏），
  归一化 \r\n 后按 800/重叠 100 切，big.txt 切成 3 块（800/800/444，合计 2044 = 1844 + 2 次重叠）；
  ChunkWriter 在一个事务里删旧块 + 插新块 + 更新 doc.status=2，parse 走这条链路；
  GlobalExceptionHandler 分了 4 档：IllegalArgumentException→400、IllegalStateException→500、
  超大小→413、Exception→500 兜底
- 卡在哪：4 个验收用例（幂等 / 文档不存在 / 假 pdf / 空白 txt）都跑过了，
  但当天真正的坑没记 —— 从 http-requests-log 只能看到 19:01 连着三个 500 到 20:09 全绿的过程，
  具体卡在哪一步没有文字记录
- 明天第一步：写段落感知切分 ParagraphChunker，和定长做两刀对比

<!-- 补记（10/26）：这次 commit 267a1d1 只提交了空类，FixedSizeChunker 里只有一行 class 声明，
     真实实现留在工作区没提交，直到 10/26 才补上。以后 commit 前先看 git diff 里有没有内容，
     只看 git log 会以为自己写完了。 -->
