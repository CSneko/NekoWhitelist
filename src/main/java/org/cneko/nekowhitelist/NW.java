package org.cneko.nekowhitelist;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

@Mod("neko_whitelist")
public class NW {
    public static final Logger LOGGER = LoggerFactory.getLogger("NekoWhitelist");

    public NW(IEventBus modEventBus) {
        LOGGER.info("NekoWhitelist 正在加载喵...");

        ModConfig.getInstance().load();
        WhitelistDataManager.getInstance().load();
        PasswordDataManager.getInstance().load();
        TemplateManager.getInstance().init();

        // 注册游戏事件到 NeoForge 事件总线
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLeave);

        var config = ModConfig.getInstance();
        LOGGER.info("NekoWhitelist 加载完成喵~ enabled={}, whitelist={}, blacklist={}",
            config.enabled, config.whitelistEnabled, config.blacklistEnabled);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        NWCommand.register(dispatcher);
        if (ModConfig.getInstance().login.enabled) {
            LoginCommand.register(dispatcher);
        }
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
    }

    private void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoginManager.getInstance().onPlayerDisconnect(player.getUUID());
        }
    }
}
