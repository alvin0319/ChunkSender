package ChunkSender;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunkSender extends JavaPlugin {

    private static ChunkSender instance = null;

    public static List<String> allowedAddresses = new ArrayList<>();

    ChunkWebSocketServer server;


    @Override
    public void onEnable() {
        instance = this;

        this.saveDefaultConfig();
        allowedAddresses = getConfig().getStringList("allowedAddresses");
        allowedAddresses.add("/0:0:0:0:0:0:0:1");
        allowedAddresses.add("/127.0.0.1");
        allowedAddresses.add("/0.0.0.0");

        server = new ChunkWebSocketServer(getLogger(), getConfig().getInt("port"));
        server.start();
    }

    @Override
    public void onDisable() {
        try {
            server.stop();
        } catch (InterruptedException e) {
            getLogger().warning("Failed to stop WebSocket server");
        }
    }

    public static ChunkSender getInstance() {
        return instance;
    }
}
