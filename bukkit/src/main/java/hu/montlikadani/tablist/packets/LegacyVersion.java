package hu.montlikadani.tablist.packets;

import com.mojang.authlib.GameProfile;
import hu.montlikadani.api.IPacketNM;
import hu.montlikadani.tablist.utils.reflection.ClazzContainer;
import hu.montlikadani.tablist.utils.reflection.ComponentParser;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

public final class LegacyVersion implements IPacketNM {

    private Method playerHandleMethod, sendPacketMethod, getHandleWorldMethod, getServerMethod, jsonComponentMethod,
            chatSerializerMethodA;
    private Field playerConnectionField, headerField, footerField, listNameField, playerTeamNameField, networkManager, channel,
            recentTpsField, pingField;
    private Constructor<?> playerListHeaderFooterConstructor, entityPlayerConstructor, interactManagerConstructor;
    private Class<?> minecraftServer, interactManager, chatSerializer;

    private final List<Object> playerTeams = new ArrayList<>();
    private final java.util.Set<TagTeam> tagTeams = new java.util.HashSet<>();

    public LegacyVersion() {
        try {
            Class<?> networkManagerClass;

            try {
                networkManagerClass = ClazzContainer.classByName("net.minecraft.server.network", "NetworkManager");
            } catch (ClassNotFoundException ex) {
                networkManagerClass = ClazzContainer.classByName("net.minecraft.network", "NetworkManager");
            }

            networkManager = ClazzContainer.fieldByTypeOrName(ClazzContainer.classByName("net.minecraft.server.network", "PlayerConnection"),
                    networkManagerClass);
            channel = ClazzContainer.fieldByTypeOrName(networkManagerClass, Channel.class);

            playerConnectionField = ClazzContainer.fieldByTypeOrName(ClazzContainer.classByName("net.minecraft.server.level", "EntityPlayer"),
                    null, "b", "playerConnection", "connection", "a");

            interactManager = ClazzContainer.classByName("net.minecraft.server.level", "PlayerInteractManager");

            try {
                minecraftServer = ClazzContainer.classByName("net.minecraft.server", "MinecraftServer");
            } catch (ClassNotFoundException c) {
                try {
                    minecraftServer = ClazzContainer.classByName("net.minecraft.server.dedicated", "DedicatedServer");
                } catch (ClassNotFoundException ignored) {
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Class<?> playerListHeaderFooter;
        try {
            playerListHeaderFooter = ClazzContainer.classByName("net.minecraft.network.protocol.game", "PacketPlayOutPlayerListHeaderFooter");
        } catch (ClassNotFoundException e) {
            return;
        }

        try {
            playerListHeaderFooterConstructor = playerListHeaderFooter.getConstructor();
        } catch (NoSuchMethodException s) {
            try {
                playerListHeaderFooterConstructor = playerListHeaderFooter.getConstructor(ClazzContainer.getIChatBaseComponent(), ClazzContainer.getIChatBaseComponent());
            } catch (NoSuchMethodException e) {
                try {
                    playerListHeaderFooterConstructor = playerListHeaderFooter.getConstructor(ClazzContainer.getIChatBaseComponent());
                } catch (NoSuchMethodException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public Object getPlayerHandle(Player player) {
        try {
            if (playerHandleMethod == null) {
                playerHandleMethod = player.getClass().getDeclaredMethod("getHandle");
            }

            return playerHandleMethod.invoke(player);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public int playerPing(Player player) {
        Object entityPlayer = getPlayerHandle(player);

        if (pingField == null) {
            pingField = ClazzContainer.fieldByTypeOrName(entityPlayer.getClass(), int.class, "ping", "latency");
        }

        if (pingField != null) {
            try {
                return pingField.getInt(entityPlayer);
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            }
        }

        return 0;
    }

    @Override
    public double[] serverTps() {
        if (recentTpsField == null) {
            try {
                (recentTpsField = minecraftServer.getField("recentTps")).setAccessible(true);
            } catch (NoSuchFieldException ex) {
                ex.printStackTrace();
            }
        }

        try {
            return (double[]) recentTpsField.get(getServer());
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        }

        return new double[0];
    }

    @Override
    public void addPlayerChannelListener(Player player, List<Class<?>> classesToListen) {
        Channel channel;

        try {
            channel = (Channel) this.channel.get(networkManager.get(playerConnectionField.get(getPlayerHandle(player))));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        if (channel.pipeline().get(PACKET_INJECTOR_NAME) == null) {
            try {
                channel.pipeline().addBefore("packet_handler", PACKET_INJECTOR_NAME,
                        new PacketReceivingListener(player.getUniqueId(), classesToListen));
            } catch (NoSuchElementException ex) {
                // packet_handler not exists, sure then, ignore
            }
        }
    }

    @Override
    public void removePlayerChannelListener(Player player) {
        Object entityPlayer = getPlayerHandle(player);
        Channel channel;

        try {
            channel = (Channel) this.channel.get(networkManager.get(playerConnectionField.get(entityPlayer)));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        if (channel != null) {
            try {
                channel.pipeline().remove(PACKET_INJECTOR_NAME);
            } catch (NoSuchElementException ignored) {
            }
        }
    }

    private Object getServer() {
        if (getServerMethod == null) {
            try {
                getServerMethod = minecraftServer.getMethod("getServer");
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        try {
            return getServerMethod.invoke(Bukkit.getServer());
        } catch (Exception x) {
            try {
                return getServerMethod.invoke(minecraftServer);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        sendPacket(getPlayerHandle(player), packet);
    }

    private void sendPacket(Object handle, Object packet) {
        try {
            Object playerConnection = playerConnectionField.get(handle);

            if (sendPacketMethod == null && (sendPacketMethod = ClazzContainer.methodByTypeAndName(playerConnection.getClass(), null,
                        new Class[] { ClazzContainer.getPacket() }, "a", "sendPacket")) == null) {
                return;
            }

            sendPacketMethod.invoke(playerConnection, packet);
        } catch (InvocationTargetException | IllegalAccessException ignored) {
        }
    }

    @Override
    public Object fromJson(String json) {
        try {
            if (jsonComponentMethod == null) {
                Class<?>[] declaredClasses = ClazzContainer.getIChatBaseComponent().getDeclaredClasses();

                if (declaredClasses.length != 0) {
                    jsonComponentMethod = declaredClasses[0].getMethod("a", String.class);
                }
            }

            if (jsonComponentMethod != null) {
                return jsonComponentMethod.invoke(ClazzContainer.getIChatBaseComponent(), json);
            }
        } catch (ReflectiveOperationException ex) {
            try {
                if (chatSerializer == null) {
                    chatSerializer = Class.forName("net.minecraft.server."
                            + hu.montlikadani.tablist.utils.Util.legacyNmsVersion() + ".ChatSerializer");
                }

                if (chatSerializerMethodA == null) {
                    chatSerializerMethodA = chatSerializer.getMethod("a", String.class);
                }

                return ClazzContainer.getIChatBaseComponent().cast(chatSerializerMethodA.invoke(chatSerializer, json));
            } catch (ReflectiveOperationException ignore) {
            }
        }

        return null;
    }

    @Override
    public void sendTabTitle(Player player, Object header, Object footer) {
        try {
            Object packet;

            if (playerListHeaderFooterConstructor.getParameterCount() == 2) {
                packet = playerListHeaderFooterConstructor.newInstance(header, footer);
            } else {
                packet = playerListHeaderFooterConstructor.newInstance();

                if (headerField == null && (headerField = ClazzContainer.fieldByTypeOrName(packet.getClass(),
                        ClazzContainer.getIChatBaseComponent(), "header", "a")) == null) {
                    return;
                }

                if (footerField == null && (footerField = ClazzContainer.fieldByTypeOrName(packet.getClass(),
                        ClazzContainer.getIChatBaseComponent(), "footer", "b")) == null) {
                    return;
                }

                headerField.set(packet, header);
                footerField.set(packet, footer);
            }

            sendPacket(player, packet);
        } catch (Exception f) {
            Object packet = null;

            try {
                try {
                    packet = playerListHeaderFooterConstructor.newInstance(header);
                } catch (IllegalArgumentException e) {
                    try {
                        packet = playerListHeaderFooterConstructor.newInstance();
                    } catch (IllegalArgumentException ignore) {
                    }
                }

                if (packet != null) {
                    if (footerField == null && (footerField = ClazzContainer.fieldByTypeOrName(packet.getClass(),
                            ClazzContainer.getIChatBaseComponent(), "footer", "b")) == null) {
                        return;
                    }

                    footerField.set(packet, footer);
                    sendPacket(player, packet);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Object getNewEntityPlayer(GameProfile profile) {
        org.bukkit.World world = Bukkit.getServer().getWorlds().get(0);

        try {
            if (getHandleWorldMethod == null) {
                getHandleWorldMethod = world.getClass().getDeclaredMethod("getHandle");
            }

            Object worldServer = getHandleWorldMethod.invoke(world);

            try {
                if (entityPlayerConstructor == null) {
                    try {
                        entityPlayerConstructor = ClazzContainer.classByName("net.minecraft.server.level", "EntityPlayer")
                                .getConstructor(minecraftServer, worldServer.getClass(), profile.getClass(),
                                        ClazzContainer.classByName("net.minecraft.world.entity.player", "ProfilePublicKey"));
                    } catch (NoSuchMethodException ex) {
                        entityPlayerConstructor = ClazzContainer.classByName("net.minecraft.server.level", "EntityPlayer")
                                .getConstructor(minecraftServer, worldServer.getClass(), profile.getClass());
                    }
                }

                return entityPlayerConstructor.newInstance(getServer(), worldServer, profile);
            } catch (NoSuchMethodException | ClassNotFoundException | IllegalArgumentException ex) {
                if (interactManagerConstructor == null) {
                    try {
                        interactManagerConstructor = interactManager.getConstructor(worldServer.getClass());
                    } catch (NoSuchMethodException nos) {
                        interactManagerConstructor = interactManager.getConstructors()[0];
                    }
                }

                if (entityPlayerConstructor == null) {
                    entityPlayerConstructor = ClazzContainer.classByName("net.minecraft.server.level", "EntityPlayer")
                            .getConstructor(minecraftServer, worldServer.getClass(), profile.getClass(), interactManager);
                }

                return entityPlayerConstructor.newInstance(getServer(), worldServer, profile, interactManagerConstructor.newInstance(worldServer));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object updateDisplayNamePacket(Object entityPlayer, Object component, boolean listName) {
        try {
            if (listName) {
                setListName(entityPlayer, component);
            }

            return ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(ClazzContainer.getUpdateDisplayName(), toArray(entityPlayer));
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void setListName(Object entityPlayer, Object component) {
        try {
            if (listNameField == null) {
                (listNameField = entityPlayer.getClass().getDeclaredField("listName")).setAccessible(true);
            }

            listNameField.set(entityPlayer, component);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public Object newPlayerInfoUpdatePacketAdd(Object... entityPlayers) {
        try {
            try {
                return ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(ClazzContainer.getAddPlayer(), toArray(entityPlayers));
            } catch (IllegalArgumentException ex) {
                return ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(ClazzContainer.getAddPlayer(), entityPlayers);
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object updateLatency(Object entityPlayer) {
        try {
            try {
                return ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(ClazzContainer.getUpdateLatency(), toArray(entityPlayer));
            } catch (IllegalArgumentException ex) {
                return ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(ClazzContainer.getUpdateLatency(), entityPlayer);
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object removeEntityPlayers(Object... entityPlayers) {
        try {
            return ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(ClazzContainer.getRemovePlayer(), toArray(entityPlayers));
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    // don't know why this required, but without this "argument type mismatch"
    private Object toArray(Object... arr) {
        Object entityPlayerArray = Array.newInstance(arr[0].getClass(), arr.length);

        for (int i = 0; i < arr.length; i++) {
            Array.set(entityPlayerArray, i, arr[i]);
        }

        return entityPlayerArray;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setInfoData(Object info, UUID id, int ping, Object component) {
        try {
            for (Object infoData : (List<Object>) ClazzContainer.getInfoList().get(info)) {
                GameProfile profile = ClazzContainer.getPlayerInfoDataProfile(infoData);

                if (profile == null || !profile.getId().equals(id)) {
                    continue;
                }

                Constructor<?> playerInfoDataConstr = ClazzContainer.getPlayerInfoDataConstructor();
                Object gameMode = ClazzContainer.getPlayerInfoDataGameMode().get(infoData);
                Object packet;

                if (playerInfoDataConstr.getParameterCount() == 5) {
                    packet = playerInfoDataConstr.newInstance(info, profile, ping, gameMode, component);
                } else {
                    packet = playerInfoDataConstr.newInstance(profile, ping, gameMode, component);
                }

                ClazzContainer.getInfoList().set(info, Collections.singletonList(packet));
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void createBoardTeam(String teamName, Player player, boolean followNameTagVisibility) {
        Object newTeamPacket = null, scoreTeam = null;

        try {
            if (ClazzContainer.getPacketPlayOutScoreboardTeamConstructor() == null) {
                scoreTeam = ClazzContainer.getScoreboardTeamConstructor().newInstance(ClazzContainer.getScoreboardConstructor().newInstance(), teamName);

                @SuppressWarnings("unchecked")
                Collection<String> playerNameSet = (Collection<String>) ClazzContainer.getPlayerNameSetMethod().invoke(scoreTeam);
                playerNameSet.add(player.getName());

                ClazzContainer.getScoreboardTeamNames().set(scoreTeam, playerNameSet);
            } else {
                newTeamPacket = ClazzContainer.getPacketPlayOutScoreboardTeamConstructor().newInstance();

                ClazzContainer.getPacketScoreboardTeamName().set(newTeamPacket, teamName);
                ClazzContainer.getPacketScoreboardTeamMode().set(newTeamPacket, 0);
                ClazzContainer.getScoreboardTeamNames().set(newTeamPacket, Collections.singletonList(player.getName()));

                Field displayName = ClazzContainer.getScoreboardTeamDisplayName();

                if (displayName.getType() == String.class) {
                    displayName.set(newTeamPacket, teamName);
                } else {
                    displayName.set(newTeamPacket, ComponentParser.asComponent(teamName));
                }
            }

            if (followNameTagVisibility) {
                String optionName = null;

                for (Team team : player.getScoreboard().getTeams()) {
                    if (ClazzContainer.isTeamOptionStatusEnumExist()) {
                        Team.OptionStatus optionStatus = team.getOption(Team.Option.NAME_TAG_VISIBILITY);

                        switch (optionStatus) {
                            case FOR_OTHER_TEAMS:
                                optionName = "hideForOtherTeams";
                                break;
                            case FOR_OWN_TEAM:
                                optionName = "hideForOwnTeam";
                                break;
                            default:
                                if (optionStatus != Team.OptionStatus.ALWAYS) {
                                    optionName = optionStatus.name().toLowerCase(Locale.ENGLISH);
                                }

                                break;
                        }
                    } else {
                        org.bukkit.scoreboard.NameTagVisibility visibility = team.getNameTagVisibility();

                        switch (visibility) {
                            case HIDE_FOR_OTHER_TEAMS:
                                optionName = "hideForOtherTeams";
                                break;
                            case HIDE_FOR_OWN_TEAM:
                                optionName = "hideForOwnTeam";
                                break;
                            default:
                                if (visibility != org.bukkit.scoreboard.NameTagVisibility.ALWAYS) {
                                    optionName = visibility.name().toLowerCase(Locale.ENGLISH);
                                }

                                break;
                        }
                    }
                }

                if (optionName != null) {
                    if (scoreTeam != null) {
                        Object visibility = ClazzContainer.getNameTagVisibilityByNameMethod().invoke(null, optionName);

                        if (visibility != null) {
                            ClazzContainer.getScoreboardTeamSetNameTagVisibility().invoke(scoreTeam, visibility);
                        }
                    } else {
                        ClazzContainer.getNameTagVisibility().set(newTeamPacket, optionName);
                    }
                }
            }

            if (newTeamPacket == null) {
                newTeamPacket = ClazzContainer.scoreboardTeamPacketByAction(scoreTeam, 0);
            }

            playerTeams.add(scoreTeam == null ? newTeamPacket : scoreTeam);

            if (tagTeams.isEmpty()) {
                for (Player one : Bukkit.getOnlinePlayers()) {
                    sendPacket(getPlayerHandle(one), newTeamPacket);
                }
            } else {
                for (TagTeam tagTeam : tagTeams) {
                    if (!tagTeam.playerName.equals(player.getName())) {
                        continue;
                    }

                    ClazzContainer.getScoreboardTeamSetDisplayName().invoke(tagTeam.scoreboardTeam,
                            tagTeam.scoreboardTeamDisplayNameMethod.invoke(tagTeam.scoreboardTeam));
                    ClazzContainer.getScoreboardTeamSetNameTagVisibility().invoke(tagTeam.scoreboardTeam,
                            tagTeam.scoreboardTeamNameTagVisibilityMethod.invoke(tagTeam.scoreboardTeam));

                    for (Player one : Bukkit.getOnlinePlayers()) {
                        Object handle = getPlayerHandle(one);

                        sendPacket(handle, newTeamPacket);
                        sendPacket(handle, ClazzContainer.scoreboardTeamPacketByAction(tagTeam.scoreboardTeam, 0));
                    }

                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Object unregisterBoardTeamPacket(String teamName) {
        try {

            // We use indexed loop to prevent concurrent modification exception
            for (int i = 0; i < playerTeams.size(); i++) {
                Object team = playerTeams.get(i);
                Object playerTeamName = playerTeamName(team);

                if (teamName.equals(playerTeamName)) {
                    try {
                        return ClazzContainer.scoreboardTeamPacketByAction(team, 1);
                    } catch (Exception ex) {
                        Object oldTeamPacket = ClazzContainer.getPacketPlayOutScoreboardTeamConstructor().newInstance();

                        ClazzContainer.getPacketScoreboardTeamName().set(oldTeamPacket, playerTeamName);
                        ClazzContainer.getPacketScoreboardTeamMode().set(oldTeamPacket, 1);

                        return oldTeamPacket;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private Object playerTeamName(Object team) throws IllegalAccessException {
        if (playerTeamNameField == null) {
            playerTeamNameField = ClazzContainer.fieldByTypeOrName(team.getClass(), String.class, "d", "a", "e", "i");
        }

        return playerTeamNameField == null ? null : playerTeamNameField.get(team);
    }

    @Override
    public Object createObjectivePacket(String objectiveName, Object nameComponent,
                                        ObjectiveFormat objectiveFormat, Object formatComponent) {
        try {
            if (ClazzContainer.getFirstScoreboardObjectiveConstructor().getParameterCount() == 3) {
                return ClazzContainer.getFirstScoreboardObjectiveConstructor().newInstance(null, objectiveName, ClazzContainer.getiScoreboardCriteriaDummy());
            }

            return ClazzContainer.getFirstScoreboardObjectiveConstructor().newInstance(null, objectiveName, ClazzContainer.getiScoreboardCriteriaDummy(), nameComponent,
                    ClazzContainer.getEnumScoreboardHealthDisplayInteger());
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object scoreboardObjectivePacket(Object objective, int mode) {
        try {
            return ClazzContainer.getPacketPlayOutScoreboardObjectiveConstructor().newInstance(objective, mode);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object scoreboardDisplayObjectivePacket(Object objective, int slot) {
        try {
            return ClazzContainer.getPacketPlayOutScoreboardDisplayObjectiveConstructor().newInstance(slot, objective);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object changeScoreboardScorePacket(String objectiveName, String scoreName, int score) {
        return ClazzContainer.newInstanceOfPacketPlayOutScoreboardScore(ClazzContainer.getEnumScoreboardActionChange(), objectiveName, scoreName, score);
    }

    @Override
    public Object removeScoreboardScorePacket(String objectiveName, String scoreName, int score) {
        return ClazzContainer.newInstanceOfPacketPlayOutScoreboardScore(ClazzContainer.getEnumScoreboardActionRemove(), objectiveName, scoreName, score);
    }

    @Override
    public Object createScoreboardHealthObjectivePacket(String objectiveName, Object nameComponent) {
        try {
            Constructor<?> constructor = ClazzContainer.getFirstScoreboardObjectiveConstructor();

            if (constructor.getParameterCount() == 3) {
                return constructor.newInstance(null, objectiveName, ClazzContainer.getiScoreboardCriteriaDummy());
            }

            return constructor.newInstance(null, objectiveName, ClazzContainer.getiScoreboardCriteriaDummy(), nameComponent,
                    ClazzContainer.getEnumScoreboardHealthDisplayInteger());
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }

        return null;
    }

    private final class PacketReceivingListener extends io.netty.channel.ChannelDuplexHandler {

        private final UUID listenerPlayerId;
        private final List<Class<?>> classesToListen;
        private Method scoreboardHandle;

        public PacketReceivingListener(UUID listenerPlayerId, List<Class<?>> classesToListen) {
            this.listenerPlayerId = listenerPlayerId;
            this.classesToListen = classesToListen;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
            Class<?> receivingClass = msg.getClass();

            if (!classesToListen.contains(receivingClass)) {
                super.write(ctx, msg, promise);
                return;
            }

            if (receivingClass == ClazzContainer.packetPlayOutScoreboardTeam()) {
                scoreboardTeamPacket(msg);
            } else {
                playerInfoUpdatePacket(msg);
            }

            super.write(ctx, msg, promise);
        }

        @SuppressWarnings("unchecked")
        private void playerInfoUpdatePacket(Object msg) throws Exception {
            if (ClazzContainer.getActionField().get(msg) != ClazzContainer.getEnumUpdateGameMode()) {
                return;
            }

            Player player = Bukkit.getPlayer(listenerPlayerId);

            if (player == null) {
                return;
            }

            for (Object entry : (List<Object>) ClazzContainer.getInfoList().get(msg)) {
                if (ClazzContainer.getPlayerInfoDataGameMode().get(entry) != ClazzContainer.getGameModeSpectator()) {
                    continue;
                }

                GameProfile profile = ClazzContainer.getPlayerInfoDataProfile(entry);

                if (profile == null || profile.getId().equals(listenerPlayerId)) {
                    continue;
                }

                Object updatePacket = ClazzContainer.getPlayOutPlayerInfoConstructor().newInstance(
                        ClazzContainer.getUpdateLatency(), new Object[0]);
                List<Object> players = new ArrayList<>();
                int ping = ClazzContainer.getPlayerInfoDataPing().getInt(entry);
                Object component = ClazzContainer.getPlayerInfoDisplayName().get(entry);

                if (ClazzContainer.getPlayerInfoDataConstructor().getParameterCount() == 5) {
                    players.add(ClazzContainer.getPlayerInfoDataConstructor().newInstance(msg, profile, ping,
                            ClazzContainer.getGameModeSurvival(), component));
                } else {
                    players.add(ClazzContainer.getPlayerInfoDataConstructor().newInstance(profile, ping,
                            ClazzContainer.getGameModeSurvival(), component));
                }

                ClazzContainer.getInfoList().set(updatePacket, players);
                sendPacket(player, updatePacket);
            }
        }

        // Temporal and disgusting solution to fix players name tag overwriting
        @SuppressWarnings("unchecked")
        private void scoreboardTeamPacket(Object msg) throws Exception {
            Collection<Object> players = (Collection<Object>) ClazzContainer.getScoreboardTeamNames().get(msg);

            if (players == null || players.isEmpty()) {
                return;
            }

            if (ClazzContainer.getPacketScoreboardTeamParametersMethod() == null) {
                Object nameTagVisibility = ClazzContainer.getNameTagVisibility().get(msg);

                if (nameTagVisibility == null) {
                    nameTagVisibility = ClazzContainer.getNameTagVisibilityAlways();
                } else if (nameTagVisibility == ClazzContainer.getNameTagVisibilityNever()) {
                    return;
                }

                String prefix, suffix;
                try {
                    prefix = (String) ClazzContainer.getPacketScoreboardTeamPrefix().get(msg);
                    suffix = (String) ClazzContainer.getPacketScoreboardTeamSuffix().get(msg);
                } catch (ClassCastException ex) {
                    prefix = (String) ClazzContainer.getiChatBaseComponentGetStringMethod().invoke(ClazzContainer.getPacketScoreboardTeamPrefix().get(msg));
                    suffix = (String) ClazzContainer.getiChatBaseComponentGetStringMethod().invoke(ClazzContainer.getPacketScoreboardTeamSuffix().get(msg));
                }

                if (prefix.isEmpty() && suffix.isEmpty()) {
                    return;
                }

                String playerName = (String) players.iterator().next();

                for (TagTeam team : tagTeams) {
                    if (team.playerName.equals(playerName)) {
                        return;
                    }
                }

                Player player = Bukkit.getPlayer(playerName);

                if (player == null) {
                    return;
                }

                Object chatFormat = ClazzContainer.getPacketScoreboardTeamChatFormatColorField().get(msg);
                try {
                    chatFormat = ClazzContainer.getEnumChatFormatByIntMethod().invoke(null, chatFormat);
                } catch (Throwable ignored) {
                }

                org.bukkit.scoreboard.Scoreboard scoreboard = player.getScoreboard();

                if (scoreboardHandle == null) {
                    scoreboardHandle = scoreboard.getClass().getDeclaredMethod("getHandle");
                }

                Object scoreboardTeam = ClazzContainer.getScoreboardTeamConstructor().newInstance(scoreboardHandle.invoke(scoreboard),
                        ClazzContainer.getPacketScoreboardTeamName().get(msg));

                ClazzContainer.getScoreboardTeamSetPrefix().invoke(scoreboardTeam, prefix);
                ClazzContainer.getScoreboardTeamSetSuffix().invoke(scoreboardTeam, suffix);
                ClazzContainer.getScoreboardTeamSetNameTagVisibility().invoke(scoreboardTeam, nameTagVisibility);
                ClazzContainer.getScoreboardTeamSetChatFormat().invoke(scoreboardTeam, chatFormat);
                ((Collection<String>) ClazzContainer.getPlayerNameSetMethod().invoke(scoreboardTeam)).add(playerName);

                tagTeams.add(new TagTeam(playerName, scoreboardTeam));
                return;
            }

            ((Optional<?>) ClazzContainer.getPacketScoreboardTeamParametersMethod().invoke(msg)).ifPresent(packetTeam -> {
                try {
                    Object nameTagVisibility = ClazzContainer.getNameTagVisibilityByNameMethod().invoke(packetTeam,
                            ClazzContainer.getParametersNameTagVisibility().invoke(packetTeam));

                    if (nameTagVisibility == null) {
                        nameTagVisibility = ClazzContainer.getNameTagVisibilityAlways();
                    } else if (nameTagVisibility == ClazzContainer.getNameTagVisibilityNever()) {
                        return;
                    }

                    String prefix = (String) ClazzContainer.getiChatBaseComponentGetStringMethod().invoke(ClazzContainer.getParametersTeamPrefix()
                            .invoke(packetTeam));
                    String suffix = (String) ClazzContainer.getiChatBaseComponentGetStringMethod().invoke(ClazzContainer.getParametersTeamSuffix()
                            .invoke(packetTeam));

                    if (!prefix.isEmpty() || !suffix.isEmpty()) {
                        String playerName = (String) players.iterator().next();

                        for (TagTeam team : tagTeams) {
                            if (team.playerName.equals(playerName)) {
                                return;
                            }
                        }

                        Player player = Bukkit.getPlayer(playerName);

                        if (player == null) {
                            return;
                        }

                        org.bukkit.scoreboard.Scoreboard scoreboard = player.getScoreboard();

                        if (scoreboardHandle == null) {
                            scoreboardHandle = scoreboard.getClass().getDeclaredMethod("getHandle");
                        }

                        Object scoreboardTeam = ClazzContainer.getScoreboardTeamConstructor().newInstance(scoreboardHandle.invoke(scoreboard),
                                ClazzContainer.getScoreboardTeamName().invoke(packetTeam));

                        ClazzContainer.getScoreboardTeamSetPrefix().invoke(scoreboardTeam, prefix);
                        ClazzContainer.getScoreboardTeamSetSuffix().invoke(scoreboardTeam, suffix);
                        ClazzContainer.getScoreboardTeamSetNameTagVisibility().invoke(scoreboardTeam, nameTagVisibility);
                        ClazzContainer.getScoreboardTeamSetChatFormat().invoke(scoreboardTeam, ClazzContainer.getScoreboardTeamColor().invoke(packetTeam));
                        ((Collection<String>) ClazzContainer.getPlayerNameSetMethod().invoke(scoreboardTeam)).add(playerName);
                        tagTeams.add(new TagTeam(playerName, scoreboardTeam));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
    }

    private static class TagTeam {

        public final String playerName;
        public final Object scoreboardTeam;
        protected Method scoreboardTeamDisplayNameMethod, scoreboardTeamNameTagVisibilityMethod;

        public TagTeam(String playerName, Object scoreboardTeam) {
            this.playerName = playerName;
            this.scoreboardTeam = scoreboardTeam;

            Class<?> clazz = scoreboardTeam.getClass();

            try {
                scoreboardTeamDisplayNameMethod = clazz.getDeclaredMethod("c");
                scoreboardTeamNameTagVisibilityMethod = clazz.getDeclaredMethod("j");
            } catch (NoSuchMethodException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public boolean equals(Object other) {
            return other != null && getClass() == other.getClass() && playerName.equals(((TagTeam) other).playerName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(playerName);
        }
    }
}
