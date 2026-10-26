package com.first.aikb.chunk;

import java.util.ArrayList;
import java.util.List;

public final class ParagraphChunker {

    private ParagraphChunker() {
    }

    public static List<String> split(String text, int size, int overlap){
        if(size <= 0){
            throw new IllegalArgumentException("chunk 大小必须为正数：" + size);
        }
        if(overlap < 0 || overlap >= size){
            throw new IllegalArgumentException("overlap 必须 >= 0 且小于 size：" + overlap);
        }

        List<String> pieces = new ArrayList<>();
        if(text == null || text.isBlank()){
            return pieces;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> paragraphs = toParagraphs(normalized);

        StringBuilder buffer = new StringBuilder();
        for(String para : paragraphs){
            int paraLen = cpLen(para);

            if(paraLen > size){
                flush(buffer, pieces);
                pieces.addAll(FixedSizeChunker.split(para, size, overlap));
                continue;
            }

            if(buffer.length() > 0 && cpLen(buffer.toString()) + 2 + paraLen > size) {
                flush(buffer, pieces);
            }
            if(buffer.length() > 0){
                buffer.append("\n\n");
            }
            buffer.append(para);
        }
        flush(buffer, pieces);
        return pieces;
    }

    private static List<String> toParagraphs(String text){
        List<String> parts = splitAndTrim(text, "\n\\s*\n");
        if(parts.size() <= 1){
            parts = splitAndTrim(text, "\n");
        }
        return parts;
    }

    private static List<String> splitAndTrim(String text, String regex){
        List<String> out = new ArrayList<>();
        for(String s: text.split(regex)){
            String t = s.strip();
            if(!t.isEmpty()){
                out.add(t);
            }
        }
        return out;
    }

    private static void flush(StringBuilder buffer, List<String> pieces){
        if(buffer.length() > 0){
            pieces.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    private static int cpLen(String s){
        return s.codePointCount(0, s.length());
    }
}


