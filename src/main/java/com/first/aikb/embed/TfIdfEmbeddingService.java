package com.first.aikb.embed;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TfIdfEmbeddingService implements EmbeddingService{

    private static final Logger log = LoggerFactory.getLogger(TfIdfEmbeddingService.class);

    private final ChineseTokenizer tokenizer;

    private volatile Model model;

    public void fit(List<String> corpus){
        if(corpus == null || corpus.isEmpty()){
            throw new IllegalArgumentException("语料为空，无法训练 TF-IDF");
        }
        int n = corpus.size();

        List<List<String>> docTokens = new ArrayList<>(n);
        for(String text : corpus){
            docTokens.add(tokenizer.tokens(text));
        }

        Map<String, Integer> df = new HashMap<>();
        for(List<String> tokens : docTokens){
            for(String word : new HashSet<>(tokens)){
                df.merge(word, 1, Integer::sum);
            }
        }

        List<String> vocab = new ArrayList<>();
        for(Map.Entry<String, Integer> e : df.entrySet()){
            if(e.getValue() < n){
                vocab.add(e.getKey());
            }
        }
        vocab.sort(String::compareTo);

        Map<String, Integer> index = new HashMap<>(vocab.size() * 2);
        double[] idf = new double[vocab.size()];
        for(int i = 0; i < vocab.size(); i++){
            String word = vocab.get(i);
            index.put(word, i);
            idf[i] = Math.log((double) n / df.get(word));
        }

        this.model = new Model(vocab, index, idf, fingerprint(vocab, n));
        log.info("TF-IDF 训练完成：{} 块语料 -> 词表 {} 维，modelId={}", n, vocab.size(),model.modelId);
    }

    @Override
    public float[] embed(String text){
        Model m = this.model;
        if(m == null){
            throw new IllegalStateException("TF-IDF 尚未训练，先调用 fit(corpus)");
        }
        Map<Integer, Integer> tf = new LinkedHashMap<>();
        for(String word : tokenizer.tokens(text)){
            Integer i = m.index.get(word);
            if(i != null){
                tf.merge(i, 1, Integer::sum);
            }
        }
        float[] vector = new float[m.vocab.size()];
        for(Map.Entry<Integer, Integer> e : tf.entrySet()){
            int i = e.getKey();
            vector[i] = (float)((1 + Math.log(e.getValue())) * m.idf[i]);
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dim(){
        Model m = this.model;
        return m == null ? 0 : m.vocab.size();
    }

    @Override
    public String modelId(){
        Model m = this.model;
        return m == null ? "tfidf:untrained" : m.modelId;
    }

    public Snapshot snapshot(){
        Model m = this.model;
        if(m == null){
            throw new IllegalStateException("TF-IDF 尚未训练，没有可导出模型");
        }
        return new Snapshot(m.modelId, List.copyOf(m.vocab), m.idf.clone());
    }

    public void restore(String modelId, List<String> vocab, double[] idf){
        if(vocab.size() != idf.length){
            throw new IllegalStateException("词表与 IDF 长度不一致：" + vocab.size() + " vs " + idf.length);
        }
        Map<String, Integer> index = new HashMap<>(vocab.size() * 2);
        for(int i = 0; i < vocab.size(); i++){
            index.put(vocab.get(i), i);
        }
        this.model = new Model(List.copyOf(vocab), index, idf.clone(), modelId);
        log.info("TF-IDF 模型已从库里恢复：词表 {} 维，modelId={}", vocab.size(), modelId);
    }

    private static void normalize(float[] v){
        double sum = 0;
        for(float x : v){
            sum += (double) x * x;
        }
        if(sum <= 0){
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for(int i = 0; i < v.length; i++){
            v[i] /= norm;
        }
    }

    private static String fingerprint(List<String> vocab, int n){
        int h = 17;
        for(String w : vocab){
            h = h * 31 + w.hashCode();
        }
        return "tfidf:" + vocab.size() + ":" + n + ":" + Integer.toHexString(h);
    }

    private record Model(List<String> vocab, Map<String, Integer> index, double[] idf, String modelId){
    }

    public record Snapshot(String modelId, List<String> vocab, double[] idf){
    }
}
