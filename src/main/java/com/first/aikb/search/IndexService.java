package com.first.aikb.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.first.aikb.embed.DenseVectorCodec;
import com.first.aikb.embed.EmbeddingService;
import com.first.aikb.embed.SparseVectorCodec;
import com.first.aikb.embed.TfIdfEmbeddingService;
import com.first.aikb.entity.Chunk;
import com.first.aikb.mapper.ChunkMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private static final String SEP = "\\|";

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final ChunkIndex chunkIndex;
    private final SparseVectorCodec codec = new SparseVectorCodec();
    private final DenseVectorCodec denseVectorCodec = new DenseVectorCodec();

    @Value("${app.index-strategy:paragraph}")
    private String defaultStrategy;

    @Value("${app.index-dir:./index}")
    private String indexDir;

    public RebuildResult rebuild(String strategy){
        long start = System.currentTimeMillis();

        List<Chunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getStrategy, strategy)
                .orderByAsc(Chunk::getDocId)
                .orderByAsc(Chunk::getSeq));
        if(chunks.isEmpty()){
            throw new IllegalStateException("没有 strategy=" + strategy + " 的块，先解析文档："
                    + "POST /api/doc/{id}/parse?strategy=" + strategy);
        }

        List<String> corpus = new ArrayList<>(chunks.size());
        for(Chunk c : chunks){
            corpus.add(c.getContent());
        }
        embeddingService.prepare(corpus);

        String modelId = embeddingService.modelId();
        boolean sparse = modelId.startsWith("tfidf");
        Set<Long> docIds = new LinkedHashSet<>();
        for(Chunk c : chunks){
            float[] vec = embeddingService.embed(c.getContent());
            log.info("chunk={} head={} vec[0]={} vec[1]={}",
                    c.getId(),
                    c.getContent().substring(0, Math.min(12, c.getContent().length())),
                    vec[0], vec[1]);
            c.setEmbeddingJson(sparse ? codec.encode(vec) : denseVectorCodec.encode(vec));
            c.setEmbeddingModel(modelId);
            chunkMapper.updateById(c);
            docIds.add(c.getDocId());
        }

        saveModel(strategy);

        int indexed = chunkIndex.reload(strategy);

        long elapsed = System.currentTimeMillis() - start;
        log.info("索引重建完成：strategy={} 文档 {} 篇 / 块 {} 条 / 维度 {} / 耗时 {} ms",
                strategy, docIds.size(), chunks.size(), embeddingService.dim(), elapsed);
        return new RebuildResult(strategy, docIds.size(), chunks.size(), indexed, embeddingService.dim(), modelId,
                elapsed);
    }

    @PostConstruct
    public void restoreOnStartup(){
        try{
            String vectorModelId = restore(defaultStrategy);

            if(vectorModelId == null){
                log.warn("启动恢复跳过：先调 POST /api/index/rebuild?strategy={} 建索引", defaultStrategy);
                return;
            }

            if(embeddingService.dim() > 0){
                int n = chunkIndex.reload(defaultStrategy);
                log.info("启动载入索引：strategy={} {} 块 / modelId={}",
                        defaultStrategy, n, embeddingService.modelId());
            } else {
                log.warn("向量模型未就绪（modelId{}），检索不可用：先确认 embedding 服务已启动",
                        embeddingService.modelId());
            }
        } catch (Exception e){
            log.warn("启动恢复索引失败，检索暂不可用；{}", e.getMessage());
        }
    }

    public String restore(String strategy){

        if(!(embeddingService instanceof TfIdfEmbeddingService tfidf)){
            return null;
        }

        TfIdfEmbeddingService tfidfService = (TfIdfEmbeddingService) embeddingService;

        Path file = modelPath(strategy);
        if(!Files.exists(file)){
            return null;
        }
        try{
            String[] parts = Files.readString(file).split(SEP, 3);
            if(parts.length != 3){
                throw new IllegalStateException("模型文件格式不对：" + file);
            }
            List<String> vocab = new ArrayList<>(Arrays.asList(parts[1].split("\n", -1)));
            String[] idfParts = parts[2].split(",");
            double[] idf = new double[idfParts.length];
            for(int i = 0; i< idfParts.length; i++){
                idf[i] = Double.parseDouble(idfParts[i]);
            }
            tfidf.restore(parts[0], vocab, idf);
        } catch (IOException e){
            throw new IllegalStateException("模型文件读取失败：" + file, e);
        }
        chunkIndex.reload(strategy);
        return tfidfService.modelId();
    }

    public String defaultStrategy(){
        return defaultStrategy;
    }

    private void saveModel(String strategy){
        
        if (!(embeddingService instanceof TfIdfEmbeddingService)) {
            return;
        }
        TfIdfEmbeddingService tfidfService = (TfIdfEmbeddingService) embeddingService;
        TfIdfEmbeddingService.Snapshot snapshot = tfidfService.snapshot();

        StringBuilder idfText = new StringBuilder(snapshot.idf().length * 8);
        for(int i = 0; i< snapshot.idf().length; i++){
            if(i > 0){
                idfText.append(',');
            }
            idfText.append(String.format("%.6f", snapshot.idf()[i]));
        }
        String content = snapshot.modelId() + "|" + String .join("\n", snapshot.vocab()) + "|" + idfText;
        try{
            Path dir = Path.of(indexDir);
            Files.createDirectories(dir);
            Files.writeString(modelPath(strategy), content);
        } catch(IOException e){
            throw new IllegalStateException("模型文件写入失败：" + modelPath(strategy), e);
        }
        log.info("模型已落盘；{}（词表 {} 维）", modelPath(strategy), snapshot.vocab().size());
    }

    private Path modelPath(String strategy){
        return Path.of(indexDir, "tfidf-" + strategy + ".model");
    }

    public record RebuildResult(
            String strategy,
            int docs,
            int chunksWritten,
            int chunksIndexed,
            int dim,
            String modelId,
            long elapsedMs
    ){
    }
}
