package com.first.aikb.embed;

import java.nio.ByteBuffer;
import java.util.Base64;

public class DenseVectorCodec {

    public String encode(float[] vector) {
        ByteBuffer buf = ByteBuffer.allocate(vector.length * Float.BYTES);
        for(float v : vector){
            buf.putFloat(v);
        }
        String b64 = Base64.getEncoder().encodeToString(buf.array());

        return "\"" + b64 + "\"";
    }

    public float[] decode(String raw, int dim) {
        if (raw == null || raw.isBlank()) {
            return new float[dim];
        }
        String b64 = raw.trim();
        // JSON 列取出来的值带双引号，解 Base64 前先剥掉
        if (b64.length() >= 2 && b64.startsWith("\"") && b64.endsWith("\"")) {
            b64 = b64.substring(1, b64.length() - 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            // 库里可能还留着 TF-IDF 时代的稀疏 JSON（{"12":0.31}），解不了就当空向量，
            // 不要让它把整个 reload 带崩
            return new float[dim];
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] out = new float[dim];
        int n = Math.min(dim, bytes.length / Float.BYTES);
        for (int i = 0; i < n; i++) {
            out[i] = buf.getFloat();
        }
        return out;
    }

}
