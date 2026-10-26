package com.first.aikb.chunk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.first.aikb.entity.Chunk;
import com.first.aikb.entity.Doc;
import com.first.aikb.mapper.ChunkMapper;
import com.first.aikb.mapper.DocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChunkWriter {

    private final ChunkMapper chunkMapper;
    private final DocMapper docMapper;

    @Transactional
    public void replaceAll(Long docId, String strategy, List<String> pieces){
        chunkMapper.delete(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getDocId, docId)
                .eq(Chunk::getStrategy, strategy));

        for(int i = 0; i < pieces.size(); i++){
            String content = pieces.get(i);
            Chunk chunk = new Chunk();
            chunk.setDocId(docId);
            chunk.setStrategy(strategy);
            chunk.setSeq(i);
            chunk.setContent(content);
            chunk.setCharCount(content.codePointCount(0, content.length()));
            chunkMapper.insert(chunk);
        }

        // 更新 doc 表：状态置为 2（完成），并记录块数
        docMapper.update(null, new LambdaUpdateWrapper<Doc>()
                .eq(Doc::getId, docId)
                .set(Doc::getStatus, 2)
                .set(Doc::getStrategy, strategy)
                .set(Doc::getChunkCount, pieces.size()));
    }
}
