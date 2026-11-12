package com.first.aikb.qa;

import java.util.List;

public record QaResult(
        String answer,
        List<Source> sources,
        long elapsedMs
) {
    public record Source(
            int index,
            Long chunkId,
            Long docId,
            double score,
            String content
    ){
    }
}
