package com.first.aikb.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocService {
    Long upload(MultipartFile file);

    int parse(Long docId, String strategy);
}
