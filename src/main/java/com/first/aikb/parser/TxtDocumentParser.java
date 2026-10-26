package com.first.aikb.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TxtDocumentParser implements DocumentParser{

    @Override
    public boolean supports(String suffix){
        return "txt".equals(suffix) || "md".equals(suffix);
    }

    @Override
    public String parse(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
