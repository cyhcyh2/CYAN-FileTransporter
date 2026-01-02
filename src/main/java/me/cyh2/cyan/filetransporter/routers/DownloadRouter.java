package me.cyh2.cyan.filetransporter.routers;

import com.eternalstarmc.modulake.api.network.ApiRouter;
import com.eternalstarmc.modulake.api.network.RoutingData;
import com.eternalstarmc.modulake.login.user.Token;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.streams.Pump;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.eternalstarmc.modulake.api.StaticValues.VERTX;


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
        var response = routingData.response();
        String tokenString = routingData.request().getFormAttribute("token");
        if (tokenString == null) {
            response.setStatusCode(401).end("Unauthorized: Invalid Token");
            return;
        }
        Token token = Token.getToken(tokenString);
        String filePath = routingData.request().getFormAttribute("filePath");
        if (filePath == null || filePath.isEmpty()) {
            response.setStatusCode(400).end("Bad Request: Missing filePath");
            return;
        }
        Path path = Paths.get(filePath).normalize();
        if (!path.startsWith(Paths.get("你的文件存储根目录"))) {
            response.setStatusCode(403).end("Forbidden: Invalid file path");
            return;
        }
        FileSystem fs = VERTX.fileSystem();
        if (!fs.exists(path.toString()).result()) {
            response.setStatusCode(404).end("File Not Found");
            return;
        }
        String fileName = path.getFileName().toString();
        response.putHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        response.putHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
        fs.open(path.toString(), new OpenOptions(), res -> {
            if (res.succeeded()) {
                var asyncFile = res.result();
                Pump.pump(asyncFile, response).start();
                response.endHandler(v -> asyncFile.close());
            } else {
                response.setStatusCode(500).end("Error reading file");
            }
        });
    }
}