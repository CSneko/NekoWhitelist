package org.cneko.nekowhitelist;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import org.cneko.nekowhitelist.command.LoginCommand;
import org.cneko.nekowhitelist.command.NWCommand;
import org.cneko.nekowhitelist.config.ModConfig;
import org.cneko.nekowhitelist.data.BlackListEntry;
import org.cneko.nekowhitelist.data.PasswordDataManager;
import org.cneko.nekowhitelist.data.WhitelistDataManager;
import org.cneko.nekowhitelist.email.TemplateManager;
import org.cneko.nekowhitelist.login.LoginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

public class NW implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("NekoWhitelist");

    @Override
    public void onInitialize() {
        LOGGER.info("NekoWhitelist 正在加载喵...");

        ModConfig.getInstance().load();
        WhitelistDataManager.getInstance().load();
        PasswordDataManager.getInstance().load();
        TemplateManager.getInstance().init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            NWCommand.register(dispatcher);
            LoginCommand.register(dispatcher);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            var config = ModConfig.getInstance();

            if (!config.enabled) return;

            String playerName = player.getGameProfile().getName();
            var data = WhitelistDataManager.getInstance();

            if (config.blacklistEnabled) {
                Optional<BlackListEntry> banEntry = data.getBlackEntry(playerName);
                if (banEntry.isPresent()) {
                    BlackListEntry ban = banEntry.get();

                    if (!"null".equals(ban.unbanTime())) {
                        try {
                            Instant unban = Instant.parse(ban.unbanTime());
                            if (Instant.now().isAfter(unban)) {
                                data.removeBlack(playerName);
                                return;
                            }
                        } catch (Exception ignored) {}
                    }

                    player.connection.disconnect(Component.literal(ban.message()));
                    LOGGER.info("已拦截黑名单玩家: {}", playerName);
                    return;
                }
            }

            if (config.whitelistEnabled) {
                if (!data.isWhitelisted(playerName)) {
                    player.connection.disconnect(Component.literal("你不在本服务器的白名单中喵~ 请先联系管理员添加你哦！"));
                    LOGGER.info("已拦截非白名单玩家: {}", playerName);
                } else {
                    // Remind players who haven't bound an email yet
                    var entry = data.getWhiteEntry(playerName);
                    if (entry.isPresent() && "null".equals(entry.get().email())) {
                        player.sendSystemMessage(Component.literal(
                            "§e⚠ 你还没有绑定邮箱喵！使用 §a/nw email set <邮箱> §e来绑定吧~\n" +
                            "§7绑定邮箱后可以验证你的身份喵~"
                        ));
                    }
                }
            }

            // 登录检查
            if (config.login.enabled) {
                LoginManager.getInstance().onPlayerJoin(player);
            }
        });

        // 玩家离开时清理登录状态
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                LoginManager.getInstance().onPlayerDisconnect(player.getUUID());
            }
        });

        var config = ModConfig.getInstance();
        LOGGER.info("NekoWhitelist 加载完成喵~ enabled={}, whitelist={}, blacklist={}",
            config.enabled, config.whitelistEnabled, config.blacklistEnabled);
    }
}
