package me.cyh2.cyan.filetransporter.routers;

import com.eternalstarmc.modulake.api.network.ApiRouter;
import com.eternalstarmc.modulake.api.network.RoutingData;
import com.eternalstarmc.modulake.login.user.Token;
import com.eternalstarmc.modulake.login.user.User;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import me.cyh2.cyan.filetransporter.Kernel;
import me.cyh2.cyan.filetransporter.utils.WebErrorUtils;

import java.io.File;

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
        for (FileUpload fileUpload : routingData.context().fileUploads()) {
            String name = fileUpload.fileName();
            String tmpFilePath = fileUpload.uploadedFileName();
            File finalFileSave = new File(Kernel.fileManager.getUserFilesFolder(user), name);
            FileSystem fs = VERTX.fileSystem();
            fs.move(tmpFilePath, finalFileSave.getAbsolutePath(), moveRes -> {
                if (moveRes.succeeded()) {
                    Kernel.logger.info("用户 {} 的文件 {} 上传成功！", user.getUsername(), name);
                } else {

                    Kernel.logger.error("用户 {} 的文件 {} 上传失败！", user.getUsername(), name);
                }
            });
        }
    }
}
