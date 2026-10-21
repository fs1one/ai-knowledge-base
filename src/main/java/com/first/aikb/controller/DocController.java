package com.first.aikb.controller;

import com.first.aikb.common.Result;
import com.first.aikb.service.DocService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("api/doc")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @PostMapping("/upload")
    public Result<Long> upload(@RequestParam("file")MultipartFile file){
        return Result.ok(docService.upload(file));
    }
}
