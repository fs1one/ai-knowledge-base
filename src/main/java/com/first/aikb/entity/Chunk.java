package com.first.aikb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chunk")
public class Chunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;
    private Integer seq;
    private String strategy;
    private String content;
    private Integer charCount;
    private String embeddingJson;
    // 字段名必须叫 createdAt：MP 靠驼峰转下划线映射到 created_at。
    // 写错一个字母不会影响 insert（值为 null 时 MP 会跳过该列），但 select 时会拼出不存在的列名直接报错
    private LocalDateTime createdAt;
}
