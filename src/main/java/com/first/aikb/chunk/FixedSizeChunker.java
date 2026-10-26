package com.first.aikb.chunk;

import java.util.ArrayList;
import java.util.List;

public final class FixedSizeChunker {

    private FixedSizeChunker(){
    }

    public static List<String> split(String text, int size, int overlap){
        if(size <= 0){
            // 异常必须带消息：否则接口只会返回 500 + 空 msg，调用方完全不知道自己错在哪
            throw new IllegalArgumentException("chunk 大小必须为正数：" + size);
        }
        if(overlap < 0 || overlap >= size){
            throw new IllegalArgumentException("overlap必须 >= 0 且小于 size：" + overlap);
        }

        List<String> pieces = new ArrayList<>();
        if(text == null || text.isBlank()){
            return pieces;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        int[] cps = normalized.codePoints().toArray();
        int total = cps.length;
        int step = size - overlap;

        for(int start = 0; start <total; start +=step){
            int end = Math.min(start + size, total);
            String piece = new String(cps, start, end - start);
            if(!piece.isBlank()){
                pieces.add(piece);
            }
            if(end == total){
                break;
            }
        }
        return pieces;
    }
}
