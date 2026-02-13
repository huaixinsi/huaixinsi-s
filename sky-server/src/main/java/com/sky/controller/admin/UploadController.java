package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@ApiOperation("文件上传接口")
public class UploadController {
    @Autowired
    private AliOssUtil aliOssUtil;
    @PostMapping("/admin/common/upload")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}",file);
        //将文件交给oss存储
        String fileName = file.getOriginalFilename();
        //截取后缀
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        //生成新的文件名
        fileName = UUID.randomUUID().toString() + suffix;
        String url = null;
        try {
            url = aliOssUtil.upload(file.getBytes(), fileName);
        } catch (IOException e) {
            log.info("文件上传失败");
            throw new RuntimeException(e);
        }
        log.info("文件上传成功，文件访问链接：{}",url);
        return Result.success(url);
    }
}
