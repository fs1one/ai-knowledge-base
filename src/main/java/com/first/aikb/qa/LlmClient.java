package com.first.aikb.qa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    /**
     * 必须显式带 charset。
     * MediaType.APPLICATION_JSON 不带 charset 时，StringHttpMessageConverter 会用默认的
     * ISO-8859-1 编码 String body，中文全被破坏，服务端 json.loads 在第一个字符就报
     * "Expecting value: line 1 column 1"。这个错误不会说"编码问题"，只会说 JSON 非法。
     */
    private static final MediaType JSON_UTF8 =
            new MediaType("application", "json", StandardCharsets.UTF_8);

    private final RestClient http;

    public LlmClient(@Value("${app.llm.base-url:http://localhost:8091}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 调本地 LLM 服务。
     * body 直接发拼好的 JSON 字符串，不用 Map —— Day7 踩过：
     * RestClient.body(Map) 会把 body 截成几十字节，服务端只收到前十几个字。
     * String body 走 StringHttpMessageConverter，按 UTF-8 整块发，长度按字节算。
     */
    public String chat(String prompt, int maxTokens) {
        // 注意 content 的值必须用引号包住：jsonEscape 只负责转义字符串内部的字符，
        // 外层的 " 要在拼接时自己写。少了它，中文会裸露在 JSON 里，
        // 服务端 json.loads 直接报 Expecting value: line 1 column 1。
        String payload = "{\"messages\":[{\"role\":\"user\",\"content\":\""
                + jsonEscape(prompt) + "\"}],\"max_tokens\":" + maxTokens + "}";

        // 用 byte[] 读、自己按 UTF-8 解码。
        // body(String.class) 会走 StringHttpMessageConverter，它默认用 ISO-8859-1 解码响应，
        // 服务端发来的 UTF-8 中文会变成乱码 —— 而且 JSON 结构仍然正确，
        // 只有中文内容坏掉，很容易误判成"模型输出乱码"。
        byte[] raw = http.post()
                .uri("/chat")
                .contentType(JSON_UTF8)
                .body(payload)
                .retrieve()
                .body(byte[].class);

        String respBody = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
        log.info("LLM 返回 {} 字节 / {} 字符", raw == null ? 0 : raw.length, respBody.length());
        return parseContent(respBody);
    }

    /**
     * 只取 content 字段，不引 Jackson —— Spring Boot 4 用的是 Jackson 3（tools.jackson 包），
     * com.fasterxml.jackson.databind.ObjectMapper 不在 classpath 上，import 会爆红。
     */
    private String parseContent(String json) {
        if (json == null || json.isBlank()) {
            return QaService.REFUSE;
        }
        int i = json.indexOf("\"content\"");
        if (i < 0) {
            return QaService.REFUSE;
        }
        int start = json.indexOf('"', json.indexOf(':', i) + 1);
        if (start < 0) {
            return QaService.REFUSE;
        }
        StringBuilder sb = new StringBuilder();
        for (int p = start + 1; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '\\' && p + 1 < json.length()) {
                char n = json.charAt(p + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        if (p + 5 < json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(p + 2, p + 6), 16));
                            p += 4;
                        }
                    }
                    default -> sb.append(n);
                }
                p++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 转义必须手写，理由同上（不引 Jackson） */
    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // 0x20 以下的控制字符必须转成 u+XXXX 形式（反斜杠写法见上方 case），
                    // 否则 JSON 非法。注意：注释里不能直接写反斜杠 + u，javac 会当 Unicode 转义解析
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
