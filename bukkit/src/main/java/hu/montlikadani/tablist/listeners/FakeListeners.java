package hu.montlikadani.tablist.listeners;

import hu.montlikadani.tablist.TabList;
import hu.montlikadani.tablist.config.FakeConfiguration;
import hu.montlikadani.tablist.tablist.fakeplayers.FakePlayerHandler;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeListeners implements Listener {

    private final TabList plugin;

    public FakeListeners(TabList plugin) {
        this.plugin = plugin;
    }

    public final List<String> fakeOnline = new ArrayList<>();

    public void join(int amount) {
        if (!FakeConfiguration.ENABLED)
            return;
        Collections.shuffle(FakeConfiguration.FAKE_PLAYER_NAMES);
        for (int i = 0; i < amount; i++)
            this.join0(FakeConfiguration.FAKE_PLAYER_NAMES.get(i));
    }

    public void quit(int amount) {
        if (!FakeConfiguration.ENABLED)
            return;
        Collections.shuffle(this.fakeOnline);
        for (int i = 0; i < amount; i++)
            this.quit0(FakeConfiguration.FAKE_PLAYER_NAMES.get(i));
    }

    private void join0(String name) {
        if (!FakeConfiguration.ENABLED)
            return;
        FakeConfiguration.FAKE_PLAYER_NAMES.remove(name);
        FakePlayerHandler handler = this.plugin.getFakePlayerHandler();
        List<Integer> pings = List.of(149, 299, 599);
        Collections.shuffle(pings);
        handler.createPlayer(name,
                FakeConfiguration.DEFAULT_TAG + " " + name,
                "",
                pings.get(0));
        this.fakeOnline.add(name);
    }

    private void quit0(String name) {
        if (!FakeConfiguration.ENABLED)
            return;
        this.fakeOnline.remove(name);
        FakePlayerHandler handler = this.plugin.getFakePlayerHandler();
        handler.removePlayer(name);
        FakeConfiguration.FAKE_PLAYER_NAMES.add(name);
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        int size = Bukkit.getOnlinePlayers().size();
        int increase = (int) Math.floor(size * FakeConfiguration.INCREASE);
        if (increase > this.fakeOnline.size())
            this.join(increase - size);
        else if (increase < this.fakeOnline.size())
            this.quit(size - increase);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        int size = Bukkit.getOnlinePlayers().size();
        int increase = (int) Math.floor(size * FakeConfiguration.INCREASE);
        if (increase > this.fakeOnline.size())
            this.join(increase - size);
        else if (increase < this.fakeOnline.size())
            this.quit(size - increase);
    }

    @EventHandler
    private void onCommand(PlayerCommandPreprocessEvent event) {
        for (String name : this.fakeOnline) {
            if (StringUtils.containsIgnoreCase(name, event.getMessage())) {
                this.quit0(name);
                return;
            }
        }
    }

    @EventHandler
    private void onMessage(AsyncPlayerChatEvent event) {
        for (String name : this.fakeOnline) {
            if (StringUtils.containsIgnoreCase(name, event.getMessage())) {
                this.quit0(name);
                return;
            }
        }
    }

}
