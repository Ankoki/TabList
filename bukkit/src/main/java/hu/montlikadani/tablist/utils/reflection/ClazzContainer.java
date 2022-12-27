package hu.montlikadani.tablist.utils.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.authlib.GameProfile;
import hu.montlikadani.tablist.utils.ServerVersion;

public final class ClazzContainer {

	private static Field infoList, scoreboardTeamName, scoreboardTeamDisplayName, scoreboardTeamNames, scoreboardTeamMode, nameTagVisibility,
			playerInfoDataProfileField, playerInfoDataGameMode, nameTagVisibilityNameField, actionField, playerInfoDataPing, playerInfoDisplayName;

	private static Class<?> iChatBaseComponent, packet, enumPlayerInfoAction, packetPlayOutScoreboardTeam;

	private static Object addPlayer, removePlayer, updateLatency, updateDisplayName, updateGameMode, enumScoreboardHealthDisplayInteger, enumScoreboardActionChange,
			enumScoreboardActionRemove, iScoreboardCriteriaDummy, gameModeSpectator, gameModeCreative;

	private static Method scoreboardTeamSetNameTagVisibility, scoreboardTeamSetDisplayName, packetScoreboardTeamRemove, packetScoreboardTeamUpdateCreate,
			playerInfoDataProfileMethod, playerNameSetMethod;

	private static Constructor<?> playerInfoDataConstr, playOutPlayerInfoConstructor, scoreboardConstructor, scoreboardTeamConstructor, packetPlayOutScoreboardTeamConstructor,
			packetPlayOutScoreboardScoreConstructor, packetPlayOutScoreboardObjectiveConstructor, firstScoreboardObjectiveConstructor,
			packetPlayOutScoreboardDisplayObjectiveConstructor;

	private static Object[] scoreboardNameTagVisibilityEnumConstants;

	private static boolean isTeamOptionStatusEnumExist = false;

	static {
		try {
			iChatBaseComponent = classByName("net.minecraft.network.chat", "IChatBaseComponent");
			packet = classByName("net.minecraft.network.protocol", "Packet");
			Class<?> packetPlayOutPlayerInfo = classByName("net.minecraft.network.protocol.game", "PacketPlayOutPlayerInfo");
			packetPlayOutScoreboardTeam = classByName("net.minecraft.network.protocol.game", "PacketPlayOutScoreboardTeam");

			// Somehow the 1.8.8 server realizes that Team.OptionStatus enum class is exists
			//Class.forName("org.bukkit.scoreboard.Team$OptionStatus");
			try {
				org.bukkit.scoreboard.Team.class.getDeclaredMethod("getOption", org.bukkit.scoreboard.Team.Option.class);
				isTeamOptionStatusEnumExist = true;
			} catch (Throwable t) {
			}

			try {
				enumPlayerInfoAction = classByName(null, "EnumPlayerInfoAction");
			} catch (ClassNotFoundException c) {
				for (Class<?> clazz : packetPlayOutPlayerInfo.getClasses()) {
					if (clazz.getName().contains("EnumPlayerInfoAction")) {
						enumPlayerInfoAction = clazz;
						break;
					}
				}
			}

			try {
				addPlayer = enumPlayerInfoAction.getDeclaredField("ADD_PLAYER").get(enumPlayerInfoAction);
				updateLatency = enumPlayerInfoAction.getDeclaredField("UPDATE_LATENCY").get(enumPlayerInfoAction);
				updateDisplayName = enumPlayerInfoAction.getDeclaredField("UPDATE_DISPLAY_NAME").get(enumPlayerInfoAction);
				updateGameMode = enumPlayerInfoAction.getDeclaredField("UPDATE_GAME_MODE").get(enumPlayerInfoAction);
				removePlayer = enumPlayerInfoAction.getDeclaredField("REMOVE_PLAYER").get(enumPlayerInfoAction);
			} catch (NoSuchFieldException ex) {
				addPlayer = enumPlayerInfoAction.getDeclaredField("a").get(enumPlayerInfoAction);
				updateLatency = enumPlayerInfoAction.getDeclaredField("c").get(enumPlayerInfoAction);
				updateDisplayName = enumPlayerInfoAction.getDeclaredField("d").get(enumPlayerInfoAction);
				updateGameMode = enumPlayerInfoAction.getDeclaredField("b").get(enumPlayerInfoAction);
				removePlayer = enumPlayerInfoAction.getDeclaredField("e").get(enumPlayerInfoAction);
			}

			Class<?> scoreboardClass = classByName("net.minecraft.world.scores", "Scoreboard");
			scoreboardConstructor = scoreboardClass.getConstructor();

			if (ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_17_R1)) {
				Class<?> scoreboardNameTagVisibility = classByName("net.minecraft.world.scores", "ScoreboardTeamBase$EnumNameTagVisibility");
				Class<?> scoreboardTeamClass = classByName("net.minecraft.world.scores", "ScoreboardTeam");

				if (ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_18_R1)) {
					scoreboardTeamSetDisplayName = scoreboardTeamClass.getMethod("a", iChatBaseComponent);
					scoreboardTeamSetNameTagVisibility = scoreboardTeamClass.getMethod("a", scoreboardNameTagVisibility);
					playerNameSetMethod = scoreboardTeamClass.getMethod("g"); // getPlayers
					(scoreboardTeamNames = scoreboardTeamClass.getDeclaredField("e")).setAccessible(true); // players
				} else {
					scoreboardTeamSetDisplayName = scoreboardTeamClass.getMethod("setDisplayName", iChatBaseComponent);
					scoreboardTeamSetNameTagVisibility = scoreboardTeamClass.getMethod("setNameTagVisibility", scoreboardNameTagVisibility);
					playerNameSetMethod = scoreboardTeamClass.getMethod("getPlayerNameSet");
					(scoreboardTeamNames = scoreboardTeamClass.getDeclaredField("f")).setAccessible(true);
				}

				packetScoreboardTeamRemove = packetPlayOutScoreboardTeam.getMethod("a", scoreboardTeamClass);
				packetScoreboardTeamUpdateCreate = packetPlayOutScoreboardTeam.getMethod("a", scoreboardTeamClass, boolean.class);

				scoreboardNameTagVisibilityEnumConstants = scoreboardNameTagVisibility.getEnumConstants();

				Class<?> enumConstantClass = scoreboardNameTagVisibilityEnumConstants[0].getClass();
				try {
					nameTagVisibilityNameField = enumConstantClass.getDeclaredField("name");
				} catch (NoSuchFieldException ns) { // In case if name field not exist
					for (Field fields : enumConstantClass.getDeclaredFields()) {
						if (fields.getType() == String.class) {
							nameTagVisibilityNameField = fields;
							break;
						}
					}
				}

				scoreboardTeamConstructor = scoreboardTeamClass.getConstructor(scoreboardClass, String.class);
			} else {
				try {
					packetPlayOutScoreboardTeamConstructor = packetPlayOutScoreboardTeam.getDeclaredConstructor();
				} catch (NoSuchMethodException e) {
					packetPlayOutScoreboardTeamConstructor = packetPlayOutScoreboardTeam.getDeclaredConstructor(classByName(null, "ScoreboardTeam"), int.class);
				}

				packetPlayOutScoreboardTeamConstructor.setAccessible(true);

				(scoreboardTeamName = packetPlayOutScoreboardTeam.getDeclaredField("a")).setAccessible(true);
				(scoreboardTeamDisplayName = packetPlayOutScoreboardTeam.getDeclaredField("b")).setAccessible(true);
				(nameTagVisibility = packetPlayOutScoreboardTeam.getDeclaredField("e")).setAccessible(true);
				(scoreboardTeamMode = packetPlayOutScoreboardTeam.getDeclaredField(ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_9_R1) ? "i" : "h")).setAccessible(true);
				(scoreboardTeamNames = packetPlayOutScoreboardTeam.getDeclaredField(ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_9_R1) ? "h" : "e")).setAccessible(true); // players
			}

			// Objectives
			Class<?> scoreboardObjective = classByName("net.minecraft.world.scores", "ScoreboardObjective");

			packetPlayOutScoreboardDisplayObjectiveConstructor = classByName("net.minecraft.network.protocol.game", "PacketPlayOutScoreboardDisplayObjective")
					.getConstructor(int.class, scoreboardObjective);

			Class<?> enumScoreboardHealthDisplay;
			try {
				enumScoreboardHealthDisplay = classByName("net.minecraft.world.scores.criteria", "IScoreboardCriteria$EnumScoreboardHealthDisplay");
			} catch (ClassNotFoundException e) {
				enumScoreboardHealthDisplay = classByName("net.minecraft.world.scores.criteria", "EnumScoreboardHealthDisplay");
			}

			Class<?> iScoreboardCriteria = classByName("net.minecraft.world.scores.criteria", "IScoreboardCriteria");

			iScoreboardCriteriaDummy = getFieldByType(iScoreboardCriteria, iScoreboardCriteria).get(iScoreboardCriteria);

			try {
				enumScoreboardHealthDisplayInteger = enumScoreboardHealthDisplay.getDeclaredField("a").get(enumScoreboardHealthDisplay);
			} catch (NoSuchFieldException e) {
				enumScoreboardHealthDisplayInteger = enumScoreboardHealthDisplay.getDeclaredField("INTEGER").get(enumScoreboardHealthDisplay);
			}

			firstScoreboardObjectiveConstructor = scoreboardObjective.getConstructors()[0];

			if (ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_13_R2)) {
				Class<?> enumScoreboardAction;

				try {
					enumScoreboardAction = classByName("net.minecraft.server", "ScoreboardServer$Action");
				} catch (ClassNotFoundException e) {
					try {
						enumScoreboardAction = classByName("net.minecraft.server", "PacketPlayOutScoreboardScore$EnumScoreboardAction");
					} catch (ClassNotFoundException ex) {
						enumScoreboardAction = classByName("net.minecraft.server", "EnumScoreboardAction");
					}
				}

				try {
					enumScoreboardActionChange = enumScoreboardAction.getDeclaredField("a").get(enumScoreboardAction);
					enumScoreboardActionRemove = enumScoreboardAction.getDeclaredField("b").get(enumScoreboardAction);
				} catch (NoSuchFieldException e) {
					enumScoreboardActionChange = enumScoreboardAction.getDeclaredField("CHANGE").get(enumScoreboardAction);
					enumScoreboardActionRemove = enumScoreboardAction.getDeclaredField("REMOVE").get(enumScoreboardAction);
				}

				packetPlayOutScoreboardObjectiveConstructor = classByName("net.minecraft.network.protocol.game", "PacketPlayOutScoreboardObjective")
						.getConstructor(scoreboardObjective, int.class);
				packetPlayOutScoreboardScoreConstructor = classByName("net.minecraft.network.protocol.game", "PacketPlayOutScoreboardScore")
						.getConstructor(enumScoreboardAction, String.class, String.class, int.class);
			}

			(infoList = packetPlayOutPlayerInfo.getDeclaredField("b")).setAccessible(true);
			(actionField = packetPlayOutPlayerInfo.getDeclaredField("a")).setAccessible(true);

			Class<?> playerInfoData;
			try {
				playerInfoData = classByName("net.minecraft.network.protocol.game", "PacketPlayOutPlayerInfo$PlayerInfoData");
			} catch (ClassNotFoundException e) {
				playerInfoData = classByName(null, "PlayerInfoData");
			}

			for (Constructor<?> constr : playerInfoData.getConstructors()) {
				int paramCount = constr.getParameterCount();

				if (paramCount == 4 || paramCount == 5) {
					(playerInfoDataConstr = constr).setAccessible(true);
					break;
				}
			}

			if (ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_17_R1)) {
				(playerInfoDataPing = playerInfoData.getDeclaredField("a")).setAccessible(true);
				(playerInfoDataProfileField = playerInfoData.getDeclaredField("c")).setAccessible(true);
				(playerInfoDataGameMode = playerInfoData.getDeclaredField("b")).setAccessible(true);
				(playerInfoDisplayName = playerInfoData.getDeclaredField("d")).setAccessible(true);
			} else {
				(playerInfoDataProfileMethod = playerInfoData.getDeclaredMethod("a")).setAccessible(true);
				(playerInfoDataPing = playerInfoData.getDeclaredField("b")).setAccessible(true);
				(playerInfoDataGameMode = playerInfoData.getDeclaredField("c")).setAccessible(true);
				(playerInfoDisplayName = playerInfoData.getDeclaredField("e")).setAccessible(true);
			}

			for (Constructor<?> constructor : packetPlayOutPlayerInfo.getConstructors()) {
				if (constructor.getParameterCount() == 2 && constructor.getParameters()[1].getType().isArray()) {
					(playOutPlayerInfoConstructor = constructor).setAccessible(true);
					break;
				}
			}

			Class<?> enumGameMode;
			try {
				enumGameMode = classByName("net.minecraft.world.level", "EnumGamemode");
			} catch (ClassNotFoundException e) {
				enumGameMode = classByName(null, "WorldSettings$EnumGamemode");
			}

			try {
				gameModeCreative = enumGameMode.getDeclaredField("CREATIVE").get(enumGameMode);
				gameModeSpectator = enumGameMode.getDeclaredField("SPECTATOR").get(enumGameMode);
			} catch (NoSuchFieldException ex) {
				Field field = enumGameMode.getDeclaredField("d");
				field.setAccessible(true);

				gameModeSpectator = field.get(enumGameMode);

				(field = enumGameMode.getDeclaredField("b")).setAccessible(true);
				gameModeCreative = field.get(enumGameMode);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private ClazzContainer() {
	}

	public static Class<?> classByName(String newPackageName, String name) throws ClassNotFoundException {
		if (ServerVersion.isCurrentLower(ServerVersion.v1_17_R1) || newPackageName == null) {
			newPackageName = "net.minecraft.server." + ServerVersion.getArrayVersion()[3];
		}

		return Class.forName(newPackageName + "." + name);
	}

	public static Field getFieldByType(Class<?> from, Class<?> type) {
		for (Field field : from.getDeclaredFields()) {
			if (field.getType() == type) {
				field.setAccessible(true);
				return field;
			}
		}

		return null;
	}

	public static Object scoreboardTeamPacketByAction(Object scoreboardTeam, int action) throws Exception {
		switch (action) {
		case 0:
			return packetScoreboardTeamUpdateCreate.invoke(packetPlayOutScoreboardTeam, scoreboardTeam, true);
		case 1:
			return packetScoreboardTeamRemove.invoke(packetPlayOutScoreboardTeam, scoreboardTeam);
		case 2:
			return packetScoreboardTeamUpdateCreate.invoke(packetPlayOutScoreboardTeam, scoreboardTeam, false);
		default:
			return null;
		}
	}

	public static GameProfile getPlayerInfoDataProfile(Object infoData) {
		try {
			if (ServerVersion.isCurrentEqualOrHigher(ServerVersion.v1_17_R1)) {
				return (GameProfile) playerInfoDataProfileField.get(infoData);
			}

			return (GameProfile) playerInfoDataProfileMethod.invoke(infoData);
		} catch (IllegalArgumentException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
			e.printStackTrace();
		}

		return null;
	}

	public static boolean isTeamOptionStatusEnumExist() {
		return isTeamOptionStatusEnumExist;
	}

	public static Class<?> getPacket() {
		return packet;
	}

	public static Field getInfoList() {
		return infoList;
	}

	public static Constructor<?> getScoreboardConstructor() {
		return scoreboardConstructor;
	}

	public static Constructor<?> getPacketPlayOutScoreboardTeamConstructor() {
		return packetPlayOutScoreboardTeamConstructor;
	}

	public static Constructor<?> getScoreboardTeamConstructor() {
		return scoreboardTeamConstructor;
	}

	public static Constructor<?> getPlayerInfoDataConstructor() {
		return playerInfoDataConstr;
	}

	public static Constructor<?> getPlayOutPlayerInfoConstructor() {
		return playOutPlayerInfoConstructor;
	}

	public static Object getAddPlayer() {
		return addPlayer;
	}

	public static Object getRemovePlayer() {
		return removePlayer;
	}

	public static Object getUpdateLatency() {
		return updateLatency;
	}

	public static Object getUpdateDisplayName() {
		return updateDisplayName;
	}

	public static Class<?> getIChatBaseComponent() {
		return iChatBaseComponent;
	}

	public static Method getScoreboardTeamSetNameTagVisibility() {
		return scoreboardTeamSetNameTagVisibility;
	}

	public static Method getScoreboardTeamSetDisplayName() {
		return scoreboardTeamSetDisplayName;
	}

	public static Field getScoreboardTeamName() {
		return scoreboardTeamName;
	}

	public static Field getScoreboardTeamDisplayName() {
		return scoreboardTeamDisplayName;
	}

	public static Field getScoreboardTeamNames() {
		return scoreboardTeamNames;
	}

	public static Field getScoreboardTeamMode() {
		return scoreboardTeamMode;
	}

	public static Field getNameTagVisibility() {
		return nameTagVisibility;
	}

	public static Object[] getScoreboardNameTagVisibilityEnumConstants() {
		return scoreboardNameTagVisibilityEnumConstants;
	}

	public static Field getPlayerInfoDataGameMode() {
		return playerInfoDataGameMode;
	}

	public static Method getPlayerNameSetMethod() {
		return playerNameSetMethod;
	}

	public static Field getNameTagVisibilityNameField() {
		return nameTagVisibilityNameField;
	}

	public static Constructor<?> getPacketPlayOutScoreboardScoreConstructor() {
		return packetPlayOutScoreboardScoreConstructor;
	}

	public static Constructor<?> getPacketPlayOutScoreboardObjectiveConstructor() {
		return packetPlayOutScoreboardObjectiveConstructor;
	}

	public static Constructor<?> getFirstScoreboardObjectiveConstructor() {
		return firstScoreboardObjectiveConstructor;
	}

	public static Object getEnumScoreboardHealthDisplayInteger() {
		return enumScoreboardHealthDisplayInteger;
	}

	public static Constructor<?> getPacketPlayOutScoreboardDisplayObjectiveConstructor() {
		return packetPlayOutScoreboardDisplayObjectiveConstructor;
	}

	public static Object getEnumScoreboardActionChange() {
		return enumScoreboardActionChange;
	}

	public static Object getEnumScoreboardActionRemove() {
		return enumScoreboardActionRemove;
	}

	public static Object getiScoreboardCriteriaDummy() {
		return iScoreboardCriteriaDummy;
	}

	public static Object getEnumUpdateGameMode() {
		return updateGameMode;
	}

	public static Field getActionField() {
		return actionField;
	}

	public static Object getGameModeSpectator() {
		return gameModeSpectator;
	}

	public static Object getGameModeCreative() {
		return gameModeCreative;
	}

	public static Field getPlayerInfoDataPing() {
		return playerInfoDataPing;
	}

	public static Field getPlayerInfoDisplayName() {
		return playerInfoDisplayName;
	}
}
