package me.cyh2.cyan.filetransporter.routers;

import com.eternalstarmc.modulake.api.network.ApiRouter;
import com.eternalstarmc.modulake.api.network.RoutingData;
import com.eternalstarmc.modulake.login.user.Token;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import me.cyh2.cyan.filetransporter.Kernel;
import me.cyh2.cyan.filetransporter.utils.WebErrorUtils;

import java.io.File;

import static com.eternalstarmc.modulake.api.StaticValues.GSON;


/**
 * DownloadRouter - 文件列表路由
 * @version 2025/12/28 (Create at 2025/12/28 JAVA 21)
 * @author CYAN-H(cyh2)
 */
public class DownloadRouter extends ApiRouter {
    public DownloadRouter() {
        super("download", HttpMethod.POST);
    }

    @Override
    public void handler(RoutingData routingData, HttpMethod httpMethod) {
        var request = routingData.request();
        var response = routingData.response();
        String tokenString = request.getFormAttribute("token");
        if (tokenString == null) {
            response
                    .setStatusCode(400)
                    .end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Request Body")));
            return;
        }
        Token token = Token.getToken(tokenString);
        if (token == null) {
            response
                    .setStatusCode(401)
                    .end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Token")));
            return;
        }
        String filename = request.getFormAttribute("filename");
        if (filename == null) {
            response
                    .setStatusCode(401)
                    .end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Request Body")));
            return;
        }
        response.putHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
        response.putHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        File file = Kernel.fileManager.getUserFile(token.user(), filename);
        response.sendFile(file.getPath())
                .onComplete(v -> Kernel.logger.info("用户 {} 的文件 {} 传输完成！", token.user().getUsername(), filename))
                .onFailure(e -> {
            Kernel.logger.error("用户 {} 的文件 {} 传输过程中发生异常，", token.user().getUsername(), filename, e);
            if (!response.ended()) {
                response
                        .setStatusCode(500)
                        .end(GSON.toJson(WebErrorUtils.generateErrorMessage("File Transfer Failed")));
            }
            routingData.context().fail(e);
        });
    }
}
