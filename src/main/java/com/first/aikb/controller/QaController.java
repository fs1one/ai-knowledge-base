package com.first.aikb.controller;

import com.first.aikb.common.Result;
import com.first.aikb.qa.QaResult;
import com.first.aikb.qa.QaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    /**
     * 带引用的问答。
     * 注意这个方法在 CPU 上要跑 8~12 秒（1.5B 模型约 3.4 字/秒），
     * 前端必须给足够的超时时间，默认的 5 秒会断。
     */
    @GetMapping("/qa")
    public Result<QaResult> ask(@RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            throw new IllegalArgumentException("q 不能为空");
        }
        return Result.ok(qaService.ask(q));
    }
}
