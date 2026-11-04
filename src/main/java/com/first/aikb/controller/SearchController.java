package com.first.aikb.controller;

import com.first.aikb.common.Result;
import com.first.aikb.search.ChunkIndex;
import com.first.aikb.search.IndexService;
import com.first.aikb.search.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {
    private final ChunkIndex chunkIndex;
    private final IndexService indexService;

    @GetMapping("/search")
    public Result<List<SearchHit>> search(@RequestParam("q") String q, @RequestParam(value = "topK", defaultValue = "3") int topK){
        if(q == null || q.isBlank()){
            throw new IllegalArgumentException("q 不能为空");
        }
        return Result.ok(chunkIndex.search(q, topK));
    }

    @PostMapping("/index/rebuild")
    public Result<IndexService.RebuildResult> rebuild(
            @RequestParam(value = "strategy", defaultValue = "paragraph") String strategy){
        return Result.ok(indexService.rebuild(strategy));
    }

    @GetMapping("/index/status")
    public Result<Integer> status(){
        return Result.ok(chunkIndex.size());
    }
}
