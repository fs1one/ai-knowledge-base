package com.first.aikb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc")
public class Doc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String name;
    private Long size;
    private Integer chunkCount;
    private Integer status;
    private LocalDateTime createdAt;
}
