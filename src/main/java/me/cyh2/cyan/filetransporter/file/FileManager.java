package me.cyh2.cyan.filetransporter.file;

import com.eternalstarmc.modulake.login.user.User;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import me.cyh2.cyan.filetransporter.Kernel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.eternalstarmc.modulake.api.StaticValues.VERTX;

/**
 * FileManager - 文件管理核心类
 * <p>
 * 负责处理用户文件的存储、检索、列表获取以及文件分享逻辑。
 * 所有涉及文件系统 IO 的操作均建议通过此类进行，以保证安全性和一致性。
 * </p>
 *
 * @version 2025/12/28 (Create at 2025/12/27 JAVA 21)
 * @author CYAN-H(cyh2)
 */
public class FileManager {
    public static final FileManager INSTANCE = new FileManager();

    /**
     * 文件存储的根目录
     */
    private final File baseFolder;

    /**
     * 文件分享映射表
     * 结构: 目标用户UUID -> (分享者UUID -> 文件列表)
     */
    private final Map<UUID, Map<UUID, List<File>>> sharedFiles = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，初始化文件存储根目录
     */
    private FileManager() {
        this.baseFolder = new File(Kernel.dataFolder, "uploads");
        if (!this.baseFolder.mkdirs()) {
            if (this.baseFolder.isFile()) {
                this.baseFolder.delete();
                this.baseFolder.mkdirs();
            }
        }
    }

    /**
     * 获取文件存储的根目录 {@link File} 对象
     *
     * @return 文件存储根目录
     */
    public File getBaseFolder() {
        return baseFolder;
    }

    /**
     * 获取文件存储的根目录 {@link Path} 对象
     *
     * @return 文件存储根目录的 Path
     */
    public Path getBaseFolderPath() {
        return baseFolder.toPath();
    }

    /**
     * 获取指定用户的专属文件存储文件夹
     * <p>
     * 如果文件夹不存在，会自动创建。
     * 如果同名文件存在，会删除该文件并创建文件夹。
     * </p>
     *
     * @param user 目标用户
     * @return 用户的专属文件夹对象
     */
    public File getUserFilesFolder(User user) {
        // 使用 "用户名-UUID" 作为文件夹名，确保唯一性和可读性
        File folder = new File(baseFolder, user.getUsername() + "-" + user.getUuid());
        if (!folder.mkdirs()) {
            // 如果创建失败，检查是否有同名文件阻碍
            if (folder.isFile()) {
                folder.delete();
                folder.mkdirs();
            }
        }
        return folder;
    }

    /**
     * 异步获取用户文件名列表
     * <p>
     * 该操作会阻塞读取磁盘，因此在 Vert.x 的 Worker 线程池中执行。
     * </p>
     *
     * @param user    目标用户
     * @param handler 异步回调，当操作完成后会被调用。result 包含文件列表（String类型的文件名集合）。
     */
    public void getUserFileNameList(User user, Handler<AsyncResult<List<String>>> handler) {
        // 使用 executeBlocking 将阻塞操作放到工作线程，避免阻塞 EventLoop
        VERTX.<List<String>>executeBlocking(promise -> {
            File folder = getUserFilesFolder(user);
            List<String> re = new ArrayList<>();
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    // 仅返回文件名，不包含路径
                    re.add(file.getName());
                }
            }

            promise.complete(re);
        }, res -> {
            // 将 Worker 线程的结果传回主线程
            if (res.succeeded()) {
                handler.handle(res);
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    /**
     * 获取指定用户被分享的文件集合
     * <p>
     * 返回的是一个不可变或空的 Map 视图，如果用户没有任何被分享的文件，返回空 Map。
     * </p>
     *
     * @param user 目标用户
     * @return Map 结构，Key 为分享者的 UUID，Value 为该分享者分享的文件列表
     */
    public Map<UUID, List<File>> getSharedFiles(User user) {
        var re = this.sharedFiles.get(user.getUuid());
        // 如果没有数据，返回空 Map 而不是 null，防止 NPE
        return re == null ? Map.of() : re;
    }

    /**
     * 添加文件分享记录
     * <p>
     * 将 {@code sharer} 的文件分享给 {@code user}。
     * </p>
     *
     * @param user   接收分享的用户
     * @param sharer 发起分享的用户
     * @param files  被分享的文件数组
     */
    public void addSharedFiles(User user, User sharer, File... files) {
        // 获取或创建该用户的分享映射表
        Map<UUID, List<File>> map = sharedFiles.computeIfAbsent(user.getUuid(), k -> new ConcurrentHashMap<>());
        // 获取或创建该分享者对该用户的文件列表
        List<File> list = map.computeIfAbsent(sharer.getUuid(), k -> new CopyOnWriteArrayList<>());
        // 添加文件到列表
        list.addAll(List.of(files));
    }

    /**
     * 获取用户指定名称的文件对象
     * <p>
     * 该方法包含安全检查，防止恶意传入 "../etc/passwd" 等路径穿越字符串。
     * 只有位于用户专属文件夹内的文件才会被返回。
     * </p>
     *
     * @param user     目标用户
     * @param fileName 文件名
     * @return 规范化后的文件对象。如果文件不存在、IO错误或路径非法（试图越权访问），则返回 null。
     */
    public File getUserFile(User user, String fileName) {
        try {
            Path baseDir = getUserFilesFolder(user).toPath().toRealPath();
            Path resolvedPath = baseDir.resolve(fileName).normalize();

            // 确保解析后的路径仍在基础目录内
            if (!resolvedPath.startsWith(baseDir)) {
                return null;
            }

            // 转换为文件前进行最终规范化
            return resolvedPath.toFile().getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
    }

    public void deleteUserFile(User user, String fileName, Handler<AsyncResult<Boolean>> handler) {
        VERTX.executeBlocking(promise -> {
            File file = getUserFile(user, fileName); // 使用上面的安全方法
            if (file == null || !file.exists()) {
                promise.complete(false);
                return;
            }
            boolean success = file.delete();
            promise.complete(success);
        }, handler);
    }
}
