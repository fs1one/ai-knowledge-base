package com.first.aikb.search;

public record SearchHit(
    Long chunkId,
    Long docId,
    String strategy,
    Integer seq,
    double score,
    String snippet
){
    public static SearchHit of(Long chunkId, Long docId, String strategy, Integer seq, double score,
                               String content) {
        String text = content == null ? "" : content;
        String snippet = text.codePointCount(0, text.length()) <= 120
                ? text
                : text.substring(0, text.offsetByCodePoints(0, 120)) + "…";
        return new SearchHit(chunkId, docId, strategy, seq, score, snippet);
    }
}
