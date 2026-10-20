# Day 1 · 2026-10-20

## 学了什么

索引要按查询顺序建：chunk 表永远是按文档取全部块，所以是 (doc_id, seq) 而不是只 doc_id，这样不用额外排序
配置文件改完要 Build 才生效，因为运行时读的是 target/classes 里的副本

## 卡在哪

IDEA 的 Maven 设置在 workspace.xml 里有个项目级 mavenHomeTypeForPersistence=WRAPPER，它优先于全局 Settings，所以改全局没用
报 ArtifactHandlerManager was bound 时，是 wrapper 拉 Maven 3.10.0 的装配问题，删掉 .mvn 就好了

## 明天第一步

打开 LeetCode，25 分钟内写完字母异位词分组
打开 javaguide 的 HashMap 章节，读完画一张 put 流程图

---

<!-- 写完之后自己检查三件事：
     1. 第一行有没有出现具体名词（表名、类名、配置项、报错关键字）？
     2. 第二行如果明天再遇到，能不能靠这句话自己解决？
     3. 第三行是不是一个"动词 + 对象"的句子？
-->
