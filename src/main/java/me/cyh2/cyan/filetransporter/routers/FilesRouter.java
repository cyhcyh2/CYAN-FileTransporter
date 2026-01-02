package me.cyh2.cyan.filetransporter.routers;

import com.eternalstarmc.modulake.api.network.ApiRouter;
import com.eternalstarmc.modulake.api.network.RoutingData;
import com.eternalstarmc.modulake.login.user.Token;
import com.eternalstarmc.modulake.login.user.User;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import me.cyh2.cyan.filetransporter.Kernel;
import me.cyh2.cyan.filetransporter.utils.WebErrorUtils;

import java.util.HashMap;
import java.util.Map;

import static com.eternalstarmc.modulake.api.StaticValues.GSON;


/**
 * FilesRouter - 文件列表路由
 * @version 2025/12/27 (Create at 2025/12/27 JAVA 21)
 * @author CYAN-H(cyh2)
 */
public class FilesRouter extends ApiRouter {
    public FilesRouter() {
        super("files", HttpMethod.POST);
    }

    @Override
    public void handler(RoutingData routingData, HttpMethod httpMethod) {
        JsonObject context = routingData.context().getBodyAsJson();

        if (context == null) {
            routingData.response().setStatusCode(400).end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Request Body")));
            return;
        }
        if (context.getString("token") == null) {
            routingData.response().setStatusCode(400).end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Request Body, Authentication token is required")));
            return;
        }
        Token token = Token.getToken(context.getString("token"));
        if (token == null) {
            routingData.response().setStatusCode(401).end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Token")));
            return;
        }
        User user = Kernel.userManager.getUser(token);
        if (user == null) {
            routingData.response().setStatusCode(401).end(GSON.toJson(WebErrorUtils.generateErrorMessage("Invalid Token")));
            return;
        }
        Map<String, Object> responseData = new HashMap<>();
        Kernel.fileManager.getUserFileNameList(user, res -> {
            if (res.succeeded()) {
                responseData.put("files", res.result());
                responseData.put("shared", Kernel.fileManager.getSharedFiles(user));
                routingData.response().end(GSON.toJson(responseData));
            } else {
                Kernel.logger.error("在尝试向客户端返回文件列表数据时发生错误：", res.cause());
                routingData.response().setStatusCode(500).end(GSON.toJson(WebErrorUtils.generateErrorMessage("Internal Server Error")));
            }
        });
    }
}
