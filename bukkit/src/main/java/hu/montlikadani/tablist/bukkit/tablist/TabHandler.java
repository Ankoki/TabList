package hu.montlikadani.tablist.bukkit.tablist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionDefault;

import hu.montlikadani.tablist.bukkit.TabList;
import hu.montlikadani.tablist.bukkit.utils.PluginUtils;
import hu.montlikadani.tablist.bukkit.utils.Variables;

public class TabHandler implements ITabHandler {

	private final TabList plugin;

	private UUID playerUUID;
	private TabBuilder builder;

	private boolean worldEnabled = false;
	private boolean tabEmpty = false;
	private String usedPermission = "";

	private final List<String> worldList = new ArrayList<>();

	public TabHandler(TabList plugin, UUID playerUUID) {
		this(plugin, playerUUID, null);
	}

	public TabHandler(TabList plugin, UUID playerUUID, TabBuilder builder) {
		this.plugin = plugin;
		this.playerUUID = playerUUID;
		this.builder = builder == null ? TabBuilder.builder().build() : builder;
	}

	@Override
	public Player getPlayer() {
		return Bukkit.getPlayer(playerUUID);
	}

	@Override
	public UUID getPlayerUUID() {
		return playerUUID;
	}

	@Override
	public TabBuilder getBuilder() {
		return builder;
	}

	public void updateTab() {
		worldList.clear();
		worldEnabled = false;
		tabEmpty = false;
		usedPermission = "";

		final Player player = getPlayer();
		if (player == null || !player.isOnline()) {
			return;
		}

		TabTitle.sendTabTitle(player, "", "");

		if (!plugin.getConf().getTablistFile().exists()) {
			return;
		}

		final FileConfiguration c = plugin.getConf().getTablist();
		if (!c.getBoolean("enabled")) {
			return;
		}

		final String world = player.getWorld().getName();
		final String pName = player.getName();

		if (c.getStringList("disabled-worlds").contains(world) || c.getStringList("blacklisted-players").contains(pName)
				|| TabManager.TABENABLED.getOrDefault(playerUUID, false) || PluginUtils.isInGame(player)) {
			return;
		}

		String path = "";

		if (c.contains("per-world")) {
			if (c.contains("per-world." + world + ".per-player." + pName)) {
				path = "per-world." + world + ".per-player." + pName + ".";
				worldEnabled = true;
			}

			if (path.isEmpty()) {
				if (c.isConfigurationSection("per-world")) {
					t: for (String s : c.getConfigurationSection("per-world").getKeys(false)) {
						for (String split : s.split(", ")) {
							if (world.equals(split)) {
								path = "per-world." + s + ".";
								worldEnabled = worldList.add(split);
								break t;
							}
						}
					}
				}

				if (worldList.isEmpty() && c.contains("per-world." + world)) {
					path = "per-world." + world + ".";
					worldEnabled = true;
				}
			}

			if (path.isEmpty() && plugin.hasVault() && c.contains("per-world." + world + ".per-group")) {
				String group = plugin.getVaultPerm().getPrimaryGroup(world, player);
				if (group != null) {
					group = group.toLowerCase();

					if (c.contains("per-world." + world + ".per-group." + group)) {
						path = "per-world." + world + ".per-group." + group + ".";
						worldEnabled = true;
					}
				}
			}
		}

		if (path.isEmpty() && c.isConfigurationSection("permissions")) {
			for (String name : c.getConfigurationSection("permissions").getKeys(false)) {
				Permission permission = new Permission(name.startsWith("tablist.") ? name : "tablist." + name,
						PermissionDefault.NOT_OP);
				if (PluginUtils.hasPermission(player, permission.getName())) {
					path = "permissions." + name + ".";
					usedPermission = permission.getName();
					break;
				}
			}
		}

		if (path.isEmpty() && c.contains("per-player." + pName)) {
			path = "per-player." + pName + ".";
		}

		if (path.isEmpty() && plugin.hasVault() && c.contains("per-group")) {
			String group = plugin.getVaultPerm().getPrimaryGroup(player);
			if (group != null) {
				group = group.toLowerCase();

				if (c.contains("per-group." + group)) {
					path = "per-group." + group + ".";
				}
			}
		}

		List<String> header = null, footer = null;
		if (!path.isEmpty()) {
			header = c.isList(path + "header") ? c.getStringList(path + "header")
					: c.isString(path + "header") ? Arrays.asList(c.getString(path + "header")) : null;
			footer = c.isList(path + "footer") ? c.getStringList(path + "footer")
					: c.isString(path + "footer") ? Arrays.asList(c.getString(path + "footer")) : null;
		}

		if (header == null && footer == null) {
			header = c.isList("header") ? c.getStringList("header")
					: c.isString("header") ? Arrays.asList(c.getString("header")) : null;
			footer = c.isList("footer") ? c.getStringList("footer")
					: c.isString("footer") ? Arrays.asList(c.getString("footer")) : null;
		}

		this.builder = TabBuilder.builder().header(header).footer(footer).random(c.getBoolean("random")).build();
	}

	protected void sendTab() {
		final Player player = getPlayer();
		if (player == null || !player.isOnline()) {
			return;
		}

		final FileConfiguration c = plugin.getConf().getTablist();

		if ((c.getBoolean("hide-tab-when-player-vanished") && PluginUtils.isVanished(player))
				|| c.getStringList("disabled-worlds").contains(player.getWorld().getName())
				|| c.getStringList("blacklisted-players").contains(player.getName()) || PluginUtils.isInGame(player)
				|| TabManager.TABENABLED.getOrDefault(playerUUID, false)) {
			if (!tabEmpty) { // Only send it once to allow other plugins to overwrite tablist
				TabTitle.sendTabTitle(player, "", "");
				tabEmpty = true;
			}

			return;
		}

		// Track player permissions change and update tab if required
		// Note: avoid using Player#hasPermission which calls multiple times to prevent
		// verbose logging
		if (c.isConfigurationSection("permissions")) {
			boolean foundPerm = false;
			eff: for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
				if (!info.getPermission().startsWith("tablist.")) {
					continue;
				}

				if (info.getPermission().equalsIgnoreCase(usedPermission)) {
					foundPerm = true;
					break;
				}

				for (String name : c.getConfigurationSection("permissions").getKeys(false)) {
					String node = name.startsWith("tablist.") ? name : "tablist." + name;
					if (node.equalsIgnoreCase(info.getPermission()) || node.equalsIgnoreCase(usedPermission)) {
						foundPerm = true;
						updateTab();
						break eff;
					}
				}
			}

			if (!foundPerm && !usedPermission.isEmpty()) {
				updateTab(); // Back to the original tab
			}
		}

		final List<String> header = builder.getHeader(), footer = builder.getFooter();
		if (header.isEmpty() && footer.isEmpty()) {
			return;
		}

		String he = "";
		String fo = "";

		if (builder.isRandom()) {
			he = header.get(ThreadLocalRandom.current().nextInt(header.size()));
			fo = footer.get(ThreadLocalRandom.current().nextInt(footer.size()));
		}

		int r = 0;

		if (he.isEmpty()) {
			for (String line : header) {
				r++;

				if (r > 1) {
					he += "\n\u00a7r";
				}

				he += line;
			}
		}

		if (fo.isEmpty()) {
			r = 0;

			for (String line : footer) {
				r++;

				if (r > 1) {
					fo += "\n\u00a7r";
				}

				fo += line;
			}
		}

		he = plugin.makeAnim(he);
		fo = plugin.makeAnim(fo);

		final Variables v = plugin.getPlaceholders();

		if (!worldEnabled) {
			TabTitle.sendTabTitle(player, v.replaceVariables(player, he), v.replaceVariables(player, fo));
			return;
		}

		if (worldList.isEmpty()) {
			for (Player all : player.getWorld().getPlayers()) {
				TabTitle.sendTabTitle(all, v.replaceVariables(all, he), v.replaceVariables(all, fo));
			}

			return;
		}

		for (String l : worldList) {
			if (Bukkit.getWorld(l) == null)
				continue;

			for (Player all : Bukkit.getWorld(l).getPlayers()) {
				TabTitle.sendTabTitle(all, v.replaceVariables(all, he), v.replaceVariables(all, fo));
			}
		}
	}
}
