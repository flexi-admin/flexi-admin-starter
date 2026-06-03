package io.github.zmxckj.flexiadmin.service.impl;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.zmxckj.flexiadmin.config.OssConfig;
import io.github.zmxckj.flexiadmin.entity.Image;
import io.github.zmxckj.flexiadmin.mapper.ImageMapper;
import io.github.zmxckj.flexiadmin.security.SecurityUtils;
import io.github.zmxckj.flexiadmin.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImageServiceImpl extends ServiceImpl<ImageMapper, Image> implements ImageService {

    @Value("${flexi.upload.dir:${java.io.tmpdir}/flexi-upload/images}")
    private String uploadDir;

    @Value("${flexi.upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Autowired
    private OssConfig ossConfig;

    @Autowired(required = false)
    private OSS ossClient;

    @Override
    public Image uploadImage(MultipartFile file) throws IOException {
        return uploadImage(file, null);
    }

    @Override
    public Image uploadImage(MultipartFile file, String customFilename) throws IOException {
        // 检查文件大小
        if (file.getSize() > maxFileSize) {
            throw new IOException("File size exceeds the limit of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Only image files are allowed");
        }

        // 生成文件名：如果传入了filename则使用，否则使用UUID生成
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf('.')) : "";
        String filename;
        if (customFilename != null && !customFilename.isEmpty()) {
            filename = customFilename;
        } else {
            filename = UUID.randomUUID().toString() + extension;
        }
        
        // 确保上传目录和子目录存在
        Path filePath = Paths.get(uploadDir).resolve(filename);
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // 保存文件到本地
        Files.copy(file.getInputStream(), filePath);

        // 创建图片记录
        Image image = new Image();
        image.setTenantId(SecurityUtils.getCurrentTenantId());
        image.setFilename(filename);
        image.setOriginalFilename(originalFilename);
        image.setFilePath(filePath.toString());
        image.setFileSize(file.getSize());
        image.setFileType(contentType);
        image.setStatus(true);

        // 保存到数据库
        save(image);

        return image;
    }

    @Override
    public Image uploadImageToOss(MultipartFile file) throws IOException {
        return uploadImageToOss(file, null);
    }

    @Override
    public Image uploadImageToOss(MultipartFile file, String customFilename) throws IOException {
        // 检查文件大小
        if (file.getSize() > maxFileSize) {
            throw new IOException("File size exceeds the limit of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Only image files are allowed");
        }

        // 生成文件名：如果传入了filename则使用，否则使用UUID生成
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf('.')) : "";
        String filename;
        if (customFilename != null && !customFilename.isEmpty()) {
            filename = "images/" + customFilename;
        } else {
            filename = "images/" + UUID.randomUUID().toString() + extension;
        }

        // 上传到阿里云OSS
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(ossConfig.getBucketName(), filename, inputStream);
        }

        // 构建OSS访问URL
        String ossUrl;
        if (ossConfig.getDomain() != null && !ossConfig.getDomain().isEmpty()) {
            ossUrl = ossConfig.getDomain() + "/" + filename;
        } else {
            ossUrl = "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint().replace("http://", "") + "/" + filename;
        }

        // 创建图片记录
        Image image = new Image();
        image.setTenantId(SecurityUtils.getCurrentTenantId());
        image.setFilename(filename);
        image.setOriginalFilename(originalFilename);
        image.setFilePath(ossUrl);
        image.setFileSize(file.getSize());
        image.setFileType(contentType);
        image.setStatus(true);

        // 保存到数据库
        save(image);

        return image;
    }
}
