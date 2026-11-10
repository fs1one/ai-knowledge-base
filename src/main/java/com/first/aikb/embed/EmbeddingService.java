package com.first.aikb.embed;

import java.util.List;

public interface EmbeddingService {

    float[] embed(String text);

    int dim();

    String modelId();

    default void prepare(List<String> corpus){

    }
}
