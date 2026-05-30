package org.cneko.nekowhitelist;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import org.cneko.nekowhitelist.command.NWCommand;
import org.cneko.nekowhitelist.config.ModConfig;
import org.cneko.nekowhitelist.data.BlackListEntry;
import org.cneko.nekowhitelist.data.WhitelistDataManager;
import org.cneko.nekowhitelist.email.TemplateManager;
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
        TemplateManager.getInstance().init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            NWCommand.register(dispatcher);
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
                }
            }
        });

        var config = ModConfig.getInstance();
        LOGGER.info("NekoWhitelist 加载完成喵~ enabled={}, whitelist={}, blacklist={}",
            config.enabled, config.whitelistEnabled, config.blacklistEnabled);
    }
}
