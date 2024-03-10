package hu.montlikadani.tablist.config;

import hu.montlikadani.tablist.TabList;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FakeConfiguration {

    public static boolean ENABLED;
    public static String DEFAULT_TAG;
    public static double INCREASE;
    public static final List<String> FAKE_PLAYER_NAMES = new ArrayList<>();

    private final TabList plugin;
    private final File file;
    @NotNull
    private YamlConfiguration configuration;

    public FakeConfiguration(TabList plugin) throws IOException {
        this.plugin = plugin;
        this.file = new File(this.plugin.getDataFolder(), "fake-config.yml");
        this.loadFiles();
    }

    public void loadFiles() throws IOException {
        if (!file.exists()) {
            file.createNewFile();
            plugin.saveResource("fake-config.yml", false);
        }
        this.configuration = YamlConfiguration.loadConfiguration(this.file);
        ENABLED = this.configuration.getBoolean("enabled");
        DEFAULT_TAG = this.configuration.getString("default-tag");
        INCREASE = this.configuration.getDouble("increase");
        FAKE_PLAYER_NAMES.clear();
        FAKE_PLAYER_NAMES.addAll(this.configuration.getStringList("fake-player-names"));
    }

}
