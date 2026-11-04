package com.first.aikb.embed;

public interface EmbeddingService {

    float[] embed(String text);

    int dim();

    String modelId();
}
