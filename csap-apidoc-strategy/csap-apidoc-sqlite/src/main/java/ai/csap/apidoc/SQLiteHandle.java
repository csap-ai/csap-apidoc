package ai.csap.apidoc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import ai.csap.apidoc.handle.IStandardHandle;

import cn.hutool.core.io.FileUtil;
import lombok.SneakyThrows;

/**
 * SQLite 处理
 *
 * @Author ycf
 * @Date 2025/9/13 17:42
 * @Version 1.0
 */
public interface SQLiteHandle extends IStandardHandle {
    Logger LOGGER = LoggerFactory.getLogger(SQLiteHandle.class);
    /**
     * 默认的数据文件名称
     */
    String DEFAULT_DB_NAME = "apidoc";
    String API_CONTROLLER = "api_controller";
    String API_INFO = "api_info";
    String API_METHOD = "api_method";
    String API_REQUEST_PARAM = "api_request_param";
    String API_REQUEST_VALIDATE = "api_request_validate";
    String API_RESPONSE_PARAM = "api_response_param";

    /**
     * 创建临时数据库文件
     *
     * @param folder           文件目录
     * @param originalFileName 原始的文件名称
     */
    default File createDbFile(String folder, String originalFileName) {
        // 获取系统临时目录
        String tempDir = System.getProperty("java.io.tmpdir");
        FileUtil.mkdir(tempDir + folder);
        return new File(tempDir + folder, originalFileName);
    }

    /**
     * 将资源内容复制到目标文件
     *
     * @param resource       资源文件
     * @param folder         目标文件
     * @param targetFileName 目标文件名称
     */
    @SneakyThrows
    default File copyResourceToFile(Resource resource, String folder, String targetFileName) {
        return copyResourceToFile(resource, createDbFile(folder, targetFileName));
    }


    /**
     * 将资源内容复制到目标文件
     *
     * @param resource   资源文件
     * @param targetFile 目标文件
     */
    default File copyResourceToFile(Resource resource, File targetFile) throws IOException {
        if (Objects.isNull(resource) || Objects.isNull(targetFile)) {
            return null;
        }
        // 如果临时文件已存在，先删除（可选：根据需求决定是否覆盖）
        if (targetFile.exists()) {
            if (targetFile.delete()) {
                LOGGER.debug("copyResourceToFile delete file[{}] is success!", targetFile.getPath());
            }
            if (targetFile.createNewFile()) {
                LOGGER.debug("copyResourceToFile create file[{}] is success!", targetFile.getPath());
            }
        }
        // 复制流内容到文件
        try (InputStream in = resource.getInputStream();
                OutputStream out = Files.newOutputStream(targetFile.toPath())) {
            byte[] buffer = new byte[8196];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return targetFile;
    }

}
