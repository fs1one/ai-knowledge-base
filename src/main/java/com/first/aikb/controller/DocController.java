package com.first.aikb.controller;

import com.first.aikb.common.Result;
import com.first.aikb.service.DocService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @PostMapping("/upload")
    public Result<Long> upload(@RequestParam("file")MultipartFile file){
        return Result.ok(docService.upload(file));
    }

    // 路径里的变量名必须和 @PathVariable 的 name 完全一致，否则 Spring 找不到 'id' 会抛 MissingPathVariableException
    @PostMapping("/{id}/parse")
    public Result<Integer> parse(@PathVariable("id") Long id,
                                 @RequestParam(value = "strategy", required = false, defaultValue =
                                         "paragraph") String strategy){
        return Result.ok(docService.parse(id, strategy));
    }
}
