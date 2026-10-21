package com.first.aikb.service.impl;

import com.first.aikb.entity.Doc;
import com.first.aikb.mapper.DocMapper;
import com.first.aikb.service.DocService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private static final Set<String> ALLOWED = Set.of("pdf", "md", "txt");

    private final DocMapper docMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public Long upload(MultipartFile file){
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("文件为空");
        }

        String originName = file.getOriginalFilename();
        String suffix = suffixOf(originName);
        if(!ALLOWED.contains(suffix)){
            throw new IllegalArgumentException("不支持的文件类型：" + suffix);
        }

        File dir = new File(uploadDir).getAbsoluteFile();
        if(!dir.exists() && !dir.mkdirs()){
            throw new IllegalStateException("无法创建上传目录：" + dir);
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        try{
            file.transferTo(new File(dir, storedName));
        } catch (IOException e){
            throw new IllegalStateException("文件保存失败", e);
        }


        Doc doc = new Doc();
        doc.setUserId(1L);
        doc.setName(originName);
        doc.setSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(0);
        docMapper.insert(doc);
        return doc.getId();
    }

    private String suffixOf(String name){
        if(name == null || name.lastIndexOf(".") < 0){
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
    }


}
