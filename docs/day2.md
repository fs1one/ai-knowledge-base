## 10/21 Day2
- 做了什么：POST /api/doc/upload 返回 200，doc 表 id=1（sample.txt 12B status=0），uploads 落 UUID 文件
- 卡在哪：6 个坑，最坑的是把 DB_PASSWORD 填进 Active profiles → 启动即退出（Invalid profile property value）
- 明天第一步：写 DocParser 读 txt，按 800 字切块写 chunk 表
