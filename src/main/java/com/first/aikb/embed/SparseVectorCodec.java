package com.first.aikb.embed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SparseVectorCodec {

    private static final Pattern PAIR = Pattern.compile("\"(\\d+)\"\\s*:\\s*([-+0-9.eE]+)");

    public String encode(float[] vector){
        StringBuilder sb = new StringBuilder(256).append('{');
        boolean first = true;
        for(int i = 0; i < vector.length; i++){
            if(vector[i] == 0f){
                continue;
            }
            if(!first){
                sb.append(',');
            }
            sb.append('"').append(i).append("\":").append(String.format("%.6f", vector[i]));
            first = false;
        }
        return sb.append("}").toString();
    }

    public float[] decode(String json, int dim){
        float[] vector = new float[dim];
        if(json == null || json.isBlank()){
            return vector;
        }
        Matcher m = PAIR.matcher(json);
        while(m.find()){
            int index = Integer.parseInt(m.group(1));
            if(index >= 0 && index < dim){
                vector[index] = Float.parseFloat(m.group(2));
            }
        }
        return vector;
    }
}
