package com.first.aikb.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.first.aikb.chunk.ChunkWriter;
import com.first.aikb.chunk.FixedSizeChunker;
import com.first.aikb.chunk.ParagraphChunker;
import com.first.aikb.entity.Doc;
import com.first.aikb.mapper.DocMapper;
import com.first.aikb.parser.DocumentParser;
import com.first.aikb.service.DocService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private static final Logger log = LoggerFactory.getLogger(DocServiceImpl.class);

    private static final Set<String> ALLOWED = Set.of("pdf", "md", "txt");

    private static final int CHUNK_SIZE = 800;

    private static final int CHUNK_OVERLAP = 100;

    private final DocMapper docMapper;
    private final ChunkWriter chunkWriter;
    private final List<DocumentParser> parsers;

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
        // 必须存磁盘文件名：解析时要用它拼出 uploadDir/stored_name，不存就永远找不到文件
        doc.setStoredName(storedName);
        doc.setSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(0);
        docMapper.insert(doc);
        return doc.getId();
    }

    private static final Set<String> STRATEGIES = Set.of("fixed", "paragraph");

    @Override
    public int parse(Long docId, String strategy){
        String mode = normalizeStrategy(strategy);

        Doc doc = docMapper.selectById(docId);
        if(doc == null){
            throw new IllegalArgumentException("文档不存在：" + docId);
        }
        if(doc.getStoredName() == null || doc.getStoredName().isBlank()){
            throw new IllegalStateException("文档缺少 stored_name, 无法定位磁盘文件");
        }

        String suffix = suffixOf(doc.getName());
        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("暂不支持解析该类型：" + suffix));

        Path file = uploadDir().resolve(doc.getStoredName());
        if(!Files.exists(file)){
            throw new IllegalStateException("磁盘文件不存在：" + file);
        }

        updateStatus(docId, 1);

        try{
            String text = parser.parse(file);

            List<String> pieces = "fixed".equals(mode)
                    ? FixedSizeChunker.split(text, CHUNK_SIZE, CHUNK_OVERLAP)
                    : ParagraphChunker.split(text, CHUNK_SIZE, CHUNK_OVERLAP);

            if(pieces.isEmpty()){
                throw new IllegalStateException("解析结果为空：文件里没可切分的正文");
            }

            chunkWriter.replaceAll(docId, mode, pieces);
            return pieces.size();
        } catch (IOException e){
            updateStatus(docId, 3);
            throw new IllegalStateException("读取文件夹失败：" + e.getMessage(), e);
        } catch (RuntimeException e){
            updateStatus(docId, 3);
            log.warn("解析失败 docId={} strategy={}: {}", docId, mode, e.getMessage());
            throw e;
        }
    }

    private String normalizeStrategy(String raw){
        if(raw == null || raw.isBlank()){
            return "paragraph";
        }
        String mode = raw.trim().toLowerCase();
        if(!STRATEGIES.contains(mode)){
            throw new IllegalArgumentException("不支持的切分策略：" + raw + "(可选 fixed / paragraph)");
        }
        return mode;
    }

    private void updateStatus(Long docId, int status){
        docMapper.update(null, new LambdaUpdateWrapper<Doc>()
                .eq(Doc::getId, docId)
                .set(Doc::getStatus, status));
    }

    private Path uploadDir(){
        return new File(uploadDir).getAbsoluteFile().toPath();
    }

    private String suffixOf(String name){
        if(name == null || name.lastIndexOf(".") < 0){
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
    }


}
