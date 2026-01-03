package me.cyh2.cyan.filetransporter;

import com.eternalstarmc.modulake.api.commands.CommandManager;
import com.eternalstarmc.modulake.api.network.ApiRouterManager;
import com.eternalstarmc.modulake.api.network.Server;
import com.eternalstarmc.modulake.api.plugin.AbsPlugin;
import com.eternalstarmc.modulake.api.plugin.PluginBase;
import com.eternalstarmc.modulake.api.plugin.PluginManager;
import com.eternalstarmc.modulake.login.user.UserManager;
import me.cyh2.cyan.filetransporter.file.FileManager;
import me.cyh2.cyan.filetransporter.routers.DownloadRouter;
import me.cyh2.cyan.filetransporter.routers.FilesRouter;
import org.slf4j.Logger;

import java.io.File;

public class Kernel extends AbsPlugin {
    public static Logger logger;
    public static Server server;
    public static PluginBase plugin;
    public static PluginManager pluginManager;
    public static CommandManager commandManager;
    public static ApiRouterManager arManager;
    public static File dataFolder;
    public static UserManager userManager;
    public static FileManager fileManager;


    @Override
    protected void onEnable() {
        logger = getLogger();
        server = getServer();
        plugin = this;
        pluginManager = getPluginManager();
        commandManager = getCommandManager();
        arManager = getApiRouterManager();
        dataFolder = getDataFolder();
        userManager = UserManager.INSTANCE;
        fileManager = FileManager.INSTANCE;
        arManager.registerApiRouter(new FilesRouter());
        arManager.registerApiRouter(new DownloadRouter());
        logger.info("CYAN-FileTransporter启动成功啦！");
    }

    @Override
    protected void onDisable() {
        logger.info("CYAN-FileTransporter关闭成功啦！");
    }
}
