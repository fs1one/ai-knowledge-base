package com.first.aikb.embed;

import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ChineseTokenizer {

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    public List<String> tokens(String text){
        List<String> out = new ArrayList<>();
        if(text == null || text.isBlank()){
            return out;
        }
        for(String word : segmenter.sentenceProcess(text)){
            String token = word.strip().toLowerCase(Locale.ROOT);

            if(token.length() < 2){
                continue;
            }

            if(!token.matches("[\\p{IsHan}a-z0-9_]+")){
                continue;
            }
            out.add(token);
        }
        return out;
    }
}
