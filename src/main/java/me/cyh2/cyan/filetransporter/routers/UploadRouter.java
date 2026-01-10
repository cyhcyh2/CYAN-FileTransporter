package me.cyh2.cyan.filetransporter.routers;

import com.eternalstarmc.modulake.api.network.ApiRouter;
import com.eternalstarmc.modulake.api.network.RoutingData;
import com.eternalstarmc.modulake.login.user.Token;
import com.eternalstarmc.modulake.login.user.User;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import me.cyh2.cyan.filetransporter.Kernel;
import me.cyh2.cyan.filetransporter.utils.WebErrorUtils;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eternalstarmc.modulake.api.StaticValues.GSON;
import static com.eternalstarmc.modulake.api.StaticValues.VERTX;

public class UploadRouter extends ApiRouter {
    public UploadRouter() {
        super("upload", HttpMethod.POST);
    }

    @Override
    public void handler(RoutingData routingData, HttpMethod httpMethod) {
        var response = routingData.response();
        var context = routingData.context();
        var request = routingData.request();
        String tokenString = request.getHeader("Authorization");
        if (tokenString == null) {
            response
                    .setStatusCode(400)
                    .end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Request Header")));
            return;
        }
        Token token = Token.getToken(tokenString);
        if (token == null) {
            response
                    .setStatusCode(401)
                    .end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Token")));
            return;
        }
        User user = token.user();
        if (context.fileUploads().isEmpty()) {
            response
                    .setStatusCode(400)
                    .end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Request Body")));
            return;
        }
        Set<FileUpload> fileUploads = context.fileUploads();
        AtomicInteger pendingCount = new AtomicInteger(0);
        AtomicBoolean hasError = new AtomicBoolean(false);
        for (FileUpload fileUpload : fileUploads) {
            String name = fileUpload.fileName();
            String tmpFilePath = fileUpload.uploadedFileName();
            File userDir = Kernel.fileManager.getUserFilesFolder(user);
            FileSystem fs = VERTX.fileSystem();
            resolveAvailableFileName(userDir, name, 0, filename -> {
                if (filename == null) {
                    hasError.set(true);
                    pendingCount.decrementAndGet();
                    return;
                }
                File finalFileSave = new File(Kernel.fileManager.getUserFilesFolder(user), filename);
                fs.move(tmpFilePath, finalFileSave.getAbsolutePath(), moveRes -> {
                    if (moveRes.succeeded()) {
                        Kernel.logger.info("用户 {} 的文件 {} 上传成功！", user.getUsername(), name);
                    } else {
                        Kernel.logger.error("用户 {} 的文件 {} 上传失败！", user.getUsername(), name);
                        hasError.set(true);
                    }
                    if (pendingCount.decrementAndGet() == 0) {
                        if (hasError.get()) {
                            response.setStatusCode(500).end(GSON.toJson(Map.of("response", "error", "msg", "部分文件上传失败")));
                        } else {
                            response.setStatusCode(200).end(GSON.toJson(Map.of("response", "success", "msg", "上传完成！")));
                        }
                    }
                });
            });
        }
    }

    private void resolveAvailableFileName(File dir, String baseName, int index, Handler<String> handler) {
        FileSystem fs = VERTX.fileSystem();
        String candidateName;
        if (index == 0) {
            candidateName = baseName;
        } else {
            String namePart = baseName;
            String extPart = "";
            int dotIndex = baseName.lastIndexOf(".");
            if (dotIndex > 0) {
                namePart = baseName.substring(0, dotIndex);
                extPart = baseName.substring(dotIndex); // 包含 "."
            }
            candidateName = namePart + " (" + index + ")" + extPart;
        }
        File targetFile = new File(dir, candidateName);
        fs.exists(targetFile.getAbsolutePath(), existsRes -> {
            if (existsRes.failed()) {
                handler.handle(null);
                return;
            }
            if (!existsRes.result()) {
                handler.handle(candidateName);
            } else {
                if (index < 1000) {
                    resolveAvailableFileName(dir, baseName, index + 1, handler);
                } else {
                    handler.handle(null);
                }
            }
        });
    }
}
