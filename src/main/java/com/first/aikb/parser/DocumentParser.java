package com.first.aikb.parser;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String suffix);

    String parse(Path file) throws IOException;
}
