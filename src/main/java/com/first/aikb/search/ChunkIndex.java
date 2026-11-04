package com.first.aikb.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.first.aikb.embed.EmbeddingService;
import com.first.aikb.embed.SparseVectorCodec;
import com.first.aikb.entity.Chunk;
import com.first.aikb.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkIndex {

    private static final Logger log = LoggerFactory.getLogger(ChunkIndex.class);

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final SparseVectorCodec codec = new SparseVectorCodec();

    private volatile List<Entry> entries = List.of();

    private record Entry(Long chunkId, Long docId, String strategy, Integer seq, String content, float[] vector){
    }

    public int reload(String strategy){
        String currentModel = embeddingService.modelId();
        if(currentModel.startsWith("tfidf:untrained")){
            entries = List.of();
            log.warn("TF-IDF 未训练，索引为空 —— 先调 POST /api/index/rebuild?strategy=" + strategy);
            return 0;
        }
        int dim = embeddingService.dim();

        List<Chunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getStrategy, strategy)
                .isNotNull(Chunk::getEmbeddingJson)
                .eq(Chunk::getEmbeddingModel, currentModel));

        List<Entry> loaded = new ArrayList<> (rows.size());
        int skipped = 0;
        for(Chunk row :rows){
            float[] v = codec.decode(row.getEmbeddingJson(), dim);
            boolean empty = true;
            for(float x : v){
                if(x != 0f){
                    empty = false;
                    break;
                }
            }
            if(empty) {
                skipped++;
                continue;
            }
            loaded.add(new Entry(row.getId(), row.getDocId(), row.getStrategy(), row.getSeq(),
                    row.getContent(), v));
        }
        this.entries = loaded;
        log.info("索引载入完成：{} 块可用，跳过 {} 块向量（模型 {}，维度 {}）",
                loaded.size(), skipped, currentModel, dim);
        return loaded.size();
    }
    public List<SearchHit> search(String question, int topK){
        List<Entry> snapshot = this.entries;
        if(snapshot.isEmpty()){
            return List.of();
        }
        int k = Math.max(1, Math.min(topK, 20));
        float[] q = embeddingService.embed(question);

        List<Scored> scored = new ArrayList<>(snapshot.size());
        for(Entry e : snapshot){
            double dot = 0;
            float[] v = e.vector();
            int len = Math.min(q.length, v.length);
            for(int i = 0; i < len; i++){
                dot += (double) v[i] * q[i];
            }
            if(dot > 0){
                scored.add(new Scored(e, dot));
            }
        }
        if(scored.isEmpty()){
            return List.of();
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        List<SearchHit> hits = new ArrayList<>(k);
        for(int i = 0; i < Math.min(k, scored.size()); i++){
            Entry e = scored.get(i).entry();
            hits.add(SearchHit.of(e.chunkId(), e.docId(), e.strategy(), e.seq(), scored.get(i).score(),
                    e.content()));
        }
        return hits;
    }

    public int size(){
        return entries.size();
    }

    private record Scored(Entry entry, double score){
    }
}
