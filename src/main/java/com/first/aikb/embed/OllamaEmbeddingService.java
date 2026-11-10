package com.first.aikb.embed;


import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingService implements EmbeddingService{

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final RestClient http;
    private final String model;

    private volatile int dim = 0;
    private volatile String modelId = "bge-m3:untrained";

    public OllamaEmbeddingService(@Value("${app.embedding.ollama.base-url:http://localhost:11434}") String baseUrl,
                                  @Value("${app.embedding.ollama.model:bge-m3}") String model){
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
    }

    @PostConstruct
    public void warmup(){
        try{
            float[] v = embed("warmup");
            log.info("Ollama 就绪：model={} dim={} modelId={}", model, dim, modelId);
        } catch(Exception e){
            log.warn("Ollama 不可用（{}），检索将不可用；先启动 ollama serve 再 rebuild", e.getMessage());
        }
    }

    @Override
    public float[] embed(String text){

        String payload = "{\"model\":\"" + model + "\",\"input\":\"" + jsonEscape(text) + "\"}";

        log.info("embed 请求：textLen={} payloadBytes={}",
                text.length(), payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);

        EmbedResponse resp = http.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(EmbedResponse.class);

        if(resp == null || resp.embeddings() == null || resp.embeddings().isEmpty()){
            throw new IllegalStateException("Ollama 返回空向量，检查模型是否已 pull：" + model);
        }
        float[] v = resp.embeddings().get(0);
        normalize(v);
        this.dim = v.length;
        this.modelId = model + ":" + v.length;
        return v;
    }

    @Override
    public int dim(){
        return dim;
    }

    @Override
    public String modelId(){
        return modelId;
    }

    private static void normalize(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        if (sum <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
    }

    record EmbedResponse(String model, List<float[]> embeddings) {
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
