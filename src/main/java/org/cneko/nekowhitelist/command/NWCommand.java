package org.cneko.nekowhitelist.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.cneko.nekowhitelist.config.ModConfig;
import org.cneko.nekowhitelist.data.BlackListEntry;
import org.cneko.nekowhitelist.data.WhiteListEntry;
import org.cneko.nekowhitelist.data.WhitelistDataManager;
import org.cneko.nekowhitelist.email.EmailService;
import org.cneko.nekowhitelist.email.VerificationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NWCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("nw");

        // Admin commands
        root.then(Commands.literal("reload")
            .requires(source -> source.hasPermission(4))
            .executes(NWCommand::reload));

        root.then(Commands.literal("status")
            .requires(source -> source.hasPermission(4))
            .executes(NWCommand::status));

        // /nw whitelist ... (admin)
        var whitelist = Commands.literal("whitelist")
            .requires(source -> source.hasPermission(4));

        whitelist.then(Commands.literal("add")
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("email", StringArgumentType.string())
                    .executes(NWCommand::whitelistAddWithEmail))
                .executes(NWCommand::whitelistAdd)));
        whitelist.then(Commands.literal("remove")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(NWCommand::whitelistRemove)));
        whitelist.then(Commands.literal("list")
            .executes(NWCommand::whitelistList));
        whitelist.then(Commands.literal("check")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(NWCommand::whitelistCheck)));
        whitelist.then(Commands.literal("setemail")
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("email", StringArgumentType.string())
                    .executes(NWCommand::whitelistSetEmail))));

        root.then(whitelist);

        // /nw blacklist ... (admin)
        var blacklist = Commands.literal("blacklist")
            .requires(source -> source.hasPermission(4));

        blacklist.then(Commands.literal("add")
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .then(Commands.argument("duration", StringArgumentType.word())
                        .executes(NWCommand::blacklistAddWithDuration))
                    .executes(NWCommand::blacklistAdd))));
        blacklist.then(Commands.literal("remove")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(NWCommand::blacklistRemove)));
        blacklist.then(Commands.literal("list")
            .executes(NWCommand::blacklistList));
        blacklist.then(Commands.literal("check")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(NWCommand::blacklistCheck)));

        root.then(blacklist);

        // /nw debug ... (admin only)
        var debug = Commands.literal("debug")
            .requires(source -> source.hasPermission(4));

        debug.then(Commands.literal("send")
            .then(Commands.argument("email", StringArgumentType.string())
                .executes(NWCommand::debugSend)));

        root.then(debug);

        // /nw email ... (player commands, no permission required)
        var email = Commands.literal("email");

        email.then(Commands.literal("set")
            .then(Commands.argument("email", StringArgumentType.string())
                .executes(NWCommand::emailSet)));
        email.then(Commands.literal("verify")
            .then(Commands.argument("code", StringArgumentType.string())
                .executes(NWCommand::emailVerify)));
        email.then(Commands.literal("status")
            .executes(NWCommand::emailStatus));

        root.then(email);

        dispatcher.register(root);
    }

    // ========== Admin Commands ==========

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ModConfig.getInstance().load();
        WhitelistDataManager.getInstance().load();
        ctx.getSource().sendSuccess(() -> Component.literal("§a配置文件和数据已重新加载喵~"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        var config = ModConfig.getInstance();
        var data = WhitelistDataManager.getInstance();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§6===== §dNekoWhitelist 状态喵 §6=====\n" +
            "§e模组启用: " + (config.enabled ? "§a✔ 是" : "§c✘ 否") + "\n" +
            "§e白名单: " + (config.whitelistEnabled ? "§a✔ 启用" : "§c✘ 禁用") +
            " §7（共 " + data.getWhiteList().size() + " 只玩家）\n" +
            "§e黑名单: " + (config.blacklistEnabled ? "§a✔ 启用" : "§c✘ 禁用") +
            " §7（共 " + data.getBlackList().size() + " 只坏蛋）\n" +
            "§e邮件服务: " + (config.email.enabled ? "§a✔ 启用" : "§c✘ 禁用")
        ), false);
        return 1;
    }

    private static int whitelistAdd(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String adder = ctx.getSource().getTextName();
        var data = WhitelistDataManager.getInstance();

        if (data.isWhitelisted(player)) {
            ctx.getSource().sendFailure(Component.literal("§c" + player + " 早就在白名单里了喵，不要再重复添加啦！"));
            return 0;
        }
        data.addWhite(new WhiteListEntry(player, adder));
        ctx.getSource().sendSuccess(() -> Component.literal("§a✨ " + player + " 已加入白名单喵~ 欢迎欢迎！"), true);

        // 公屏广播
        if (ModConfig.getInstance().broadcastWhitelistAdd) {
            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6🎉 " + player + " §e已被 §b" + adder + " §e添加到了白名单喵~ 大家欢迎新朋友！"),
                false
            );
        }
        return 1;
    }

    private static int whitelistAddWithEmail(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String adder = ctx.getSource().getTextName();
        String email = StringArgumentType.getString(ctx, "email");
        var data = WhitelistDataManager.getInstance();

        if (data.isWhitelisted(player)) {
            ctx.getSource().sendFailure(Component.literal("§c" + player + " 早就在白名单里了喵，不要再重复添加啦！"));
            return 0;
        }
        data.addWhite(new WhiteListEntry(player, adder, email));
        ctx.getSource().sendSuccess(() -> Component.literal("§a✨ " + player + " 已加入白名单喵~ 邮箱: " + email + " 绑定成功！"), true);

        // 公屏广播
        if (ModConfig.getInstance().broadcastWhitelistAdd) {
            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6🎉 " + player + " §e已被 §b" + adder + " §e添加到了白名单喵~ 大家欢迎新朋友！"),
                false
            );
        }
        return 1;
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        if (WhitelistDataManager.getInstance().removeWhite(player)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a" + player + " 已被移出白名单喵……有缘再会！"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c" + player + " 不在白名单里喵，没法移除呢~"));
        return 0;
    }

    private static int whitelistList(CommandContext<CommandSourceStack> ctx) {
        List<WhiteListEntry> list = WhitelistDataManager.getInstance().getWhiteList();
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7白名单空空如也喵……还没有任何玩家被加入呢~"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("§6===== §d白名单喵 §6=====\n");
        for (WhiteListEntry e : list) {
            String email = "null".equals(e.email()) ? "§7未绑定" : "§b" + e.email();
            sb.append("§e").append(e.name())
                .append(" §7[方式: ").append(e.method()).append("]")
                .append(" §7添加者: ").append(e.adder())
                .append(" §7邮箱: ").append(email).append("\n");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int whitelistCheck(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        Optional<WhiteListEntry> entry = WhitelistDataManager.getInstance().getWhiteEntry(player);
        if (entry.isPresent()) {
            WhiteListEntry e = entry.get();
            String email = "null".equals(e.email()) ? "§7未绑定" : "§b" + e.email();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§a" + player + " 在白名单中喵~ ✓\n" +
                "§7  添加方式: " + e.method() + "\n" +
                "§7  添加者: " + e.adder() + "\n" +
                "§7  添加时间: " + e.addTime() + "\n" +
                "§7  邮箱: " + email
            ), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§c" + player + " 不在白名单中喵……❌"), false);
        }
        return 1;
    }

    private static int whitelistSetEmail(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String email = StringArgumentType.getString(ctx, "email");
        if (WhitelistDataManager.getInstance().setEmail(player, email)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a✨ " + player + " 的邮箱已更新为: " + email + " 喵~"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c" + player + " 不在白名单中喵，无法设置邮箱！请先添加到白名单再设置喵~"));
        return 0;
    }

    private static int blacklistAdd(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String reason = StringArgumentType.getString(ctx, "reason");
        String operator = ctx.getSource().getTextName();
        addBlacklistEntry(ctx, player, reason, "null", operator);
        return 1;
    }

    private static int blacklistAddWithDuration(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        String reason = StringArgumentType.getString(ctx, "reason");
        String durationStr = StringArgumentType.getString(ctx, "duration");
        String operator = ctx.getSource().getTextName();

        long seconds = parseDuration(durationStr);
        if (seconds <= 0) {
            ctx.getSource().sendFailure(Component.literal("§c无效的时间格式喵: " + durationStr + "。请用 1d, 2h, 30m, 90s 这样的格式喵~"));
            return 0;
        }
        String unbanTime = Instant.now().plus(Duration.ofSeconds(seconds)).toString();
        addBlacklistEntry(ctx, player, reason, unbanTime, operator);
        return 1;
    }

    private static void addBlacklistEntry(CommandContext<CommandSourceStack> ctx, String player, String reason, String unbanTime, String operator) {
        var data = WhitelistDataManager.getInstance();
        data.addBlack(new BlackListEntry(player, reason,
            "你已被此服务器封禁喵！\n原因: " + reason + "\n操作者: " + operator,
            unbanTime, operator));
        ctx.getSource().sendSuccess(() -> Component.literal("§c🚫 " + player + " 已被加入黑名单喵！原因: " + reason), true);
    }

    private static int blacklistRemove(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        if (WhitelistDataManager.getInstance().removeBlack(player)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a" + player + " 已从黑名单中移除喵~ 改过自新就是好孩子！"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c" + player + " 不在黑名单里喵，你是不是记错了？"));
        return 0;
    }

    private static int blacklistList(CommandContext<CommandSourceStack> ctx) {
        List<BlackListEntry> list = WhitelistDataManager.getInstance().getBlackList();
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7黑名单是空的喵~ 今天也是和平的一天呢！"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("§6===== §d黑名单喵 §6=====\n");
        for (BlackListEntry e : list) {
            String expiry = "null".equals(e.unbanTime()) ? "永久封禁" : "解封: " + e.unbanTime();
            sb.append("§c").append(e.name())
                .append(" §7- ").append(e.reason())
                .append(" §7（").append(expiry).append("）\n");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int blacklistCheck(CommandContext<CommandSourceStack> ctx) {
        String player = StringArgumentType.getString(ctx, "player");
        Optional<BlackListEntry> entry = WhitelistDataManager.getInstance().getBlackEntry(player);
        if (entry.isPresent()) {
            BlackListEntry e = entry.get();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§c" + player + " 目前在黑名单中喵！🚫\n" +
                "§7  封禁原因: " + e.reason() + "\n" +
                "§7  操作者: " + e.operator() + "\n" +
                "§7  封禁时间: " + e.banTime() + "\n" +
                "§7  解封时间: " + e.unbanTime()
            ), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§a" + player + " 不在黑名单中喵~ 是个好孩子呢！✔"), false);
        }
        return 1;
    }

    // ========== Player Email Commands ==========

    private static int emailSet(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c这个命令只能由玩家执行喵~"));
            return 0;
        }

        String newEmail = StringArgumentType.getString(ctx, "email");
        String playerName = player.getGameProfile().getName();
        var vMgr = VerificationManager.getInstance();
        var config = ModConfig.getInstance();

        if (!vMgr.canSend(playerName)) {
            long remaining = vMgr.getCooldownRemaining(playerName);
            ctx.getSource().sendFailure(Component.literal("§c请勿频繁发送验证码喵~ 请等待 " + remaining + " 秒后再试喵！"));
            return 0;
        }

        // check if email already belongs to another player
        WhitelistDataManager dataMgr = WhitelistDataManager.getInstance();
        for (WhiteListEntry e : dataMgr.getWhiteList()) {
            if (newEmail.equalsIgnoreCase(e.email()) && !e.name().equalsIgnoreCase(playerName)) {
                ctx.getSource().sendFailure(Component.literal("§c这个邮箱已经被 " + e.name() + " 绑定了喵~"));
                return 0;
            }
        }

        // Check if player already has a bound email
        String oldEmail = null;
        var existingEntry = dataMgr.getWhiteEntry(playerName);
        if (existingEntry.isPresent() && !"null".equals(existingEntry.get().email())) {
            oldEmail = existingEntry.get().email();
        }

        // If player already has this exact email, no need to change
        if (oldEmail != null && oldEmail.equalsIgnoreCase(newEmail)) {
            ctx.getSource().sendFailure(Component.literal("§c这个邮箱已经绑定给你了喵~ 不需要重新设置！"));
            return 0;
        }

        String code = vMgr.generateCode();

        if (oldEmail != null) {
            // ===== Email CHANGE: verify old email FIRST =====
            // Store pending with newEmail as the target, send code to old email
            vMgr.putCode(playerName, oldEmail, code, newEmail);
            vMgr.markSent(playerName);

            String maskedOld = maskEmail(oldEmail);
            if (config.email.enabled) {
                EmailService.getInstance().sendVerificationCode(oldEmail, code)
                    .thenAccept(success -> {
                        if (success) {
                            player.sendSystemMessage(Component.literal("§a📧 验证码已发送到旧邮箱 " + maskedOld + " 喵~\n§7💡 没收到邮件？记得检查一下垃圾邮件箱喵~"));
                        } else {
                            player.sendSystemMessage(Component.literal("§e⚠ 邮件发送失败喵…… 去问问服务器管理员呢"));
                        }
                    });
                ctx.getSource().sendSuccess(() -> Component.literal("§6🔄 检测到邮箱变更请求，正在发送验证码到旧邮箱 " + maskedOld + " 喵~\n§7验证旧邮箱后，我们会再向新邮箱发送验证码~"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e⚠ 邮件服务未启用喵"
                ), false);
            }
        } else {
            // ===== New email BIND: current behavior =====
            vMgr.putCode(playerName, newEmail, code);
            vMgr.markSent(playerName);

            if (config.email.enabled) {
                EmailService.getInstance().sendVerificationCode(newEmail, code)
                    .thenAccept(success -> {
                        if (success) {
                            player.sendSystemMessage(Component.literal("§a📧 验证码已发送到 " + newEmail + " 喵~\n§7💡 没收到邮件？记得检查一下垃圾邮件箱喵~"));
                        } else {
                            player.sendSystemMessage(Component.literal("§e⚠ 邮件发送失败喵…… 去问问服务器管理员呢"));
                        }
                    });
                ctx.getSource().sendSuccess(() -> Component.literal("§a📧 正在发送验证码到 " + newEmail + " 喵~"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e⚠ 邮件服务未启用喵"
                ), false);
            }
        }
        return 1;
    }

    private static int emailVerify(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c这个命令只能由玩家执行喵~"));
            return 0;
        }

        String code = StringArgumentType.getString(ctx, "code");
        String playerName = player.getGameProfile().getName();
        VerificationManager.PendingVerification pv = VerificationManager.getInstance().verify(code);

        if (pv == null) {
            ctx.getSource().sendFailure(Component.literal("§c验证码无效或已过期喵~ 请重新使用 /nw email set <邮箱> 获取新的验证码！"));
            return 0;
        }

        if (!pv.playerName().equalsIgnoreCase(playerName)) {
            ctx.getSource().sendFailure(Component.literal("§c这个验证码不是发给你的喵！请自己获取验证码~~"));
            return 0;
        }

        // If this verification was for an email CHANGE (newEmail is set),
        // the player just proved ownership of the old email.
        // Now send a new code to the NEW email for phase 2.
        if (pv.newEmail() != null) {
            String newEmail = pv.newEmail();
            var vMgr = VerificationManager.getInstance();
            var config = ModConfig.getInstance();

            String newCode = vMgr.generateCode();
            vMgr.putCode(playerName, newEmail, newCode); // phase 2: no newEmail field

            if (config.email.enabled) {
                EmailService.getInstance().sendVerificationCode(newEmail, newCode)
                    .thenAccept(success -> {
                        if (success) {
                            player.sendSystemMessage(Component.literal("§a📧 旧邮箱验证成功！验证码已发送到新邮箱 " + newEmail + " 喵~\n§7💡 没收到邮件？记得检查一下垃圾邮件箱喵~"));
                        } else {
                            player.sendSystemMessage(Component.literal("§e⚠ 邮件发送失败喵…… 去问问服务器管理员呢"));
                        }
                    });
                ctx.getSource().sendSuccess(() -> Component.literal("§a✅ 旧邮箱验证成功！验证码已发送到新邮箱 " + newEmail + " 喵~\n§7请再次使用 /nw email verify <验证码> 来验证新邮箱~"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a✅ 旧邮箱验证成功！但邮件服务未启用，新邮箱无法验证喵~"
                ), false);
            }
            return 1;
        }

        // Phase 2 (email change) or initial bind: commit the email
        WhitelistDataManager.getInstance().setEmailForPlayer(playerName, pv.email());
        ctx.getSource().sendSuccess(() -> Component.literal("§a✨ 邮箱验证成功喵！§b" + pv.email() + " §a已绑定到你的账号喵~"), true);
        return 1;
    }

    private static int emailStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c这个命令只能由玩家执行喵~"));
            return 0;
        }

        String playerName = player.getGameProfile().getName();
        Optional<WhiteListEntry> entry = WhitelistDataManager.getInstance().getWhiteEntry(playerName);

        if (entry.isPresent() && !"null".equals(entry.get().email())) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a你当前绑定的邮箱是: §b" + entry.get().email() + " §a喵~"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§7你还没有绑定邮箱喵~ 使用 §a/nw email set <邮箱> §7来绑定吧！"), false);
        }
        return 1;
    }

    // ========== Debug Commands ==========

    private static int debugSend(CommandContext<CommandSourceStack> ctx) {
        String toEmail = StringArgumentType.getString(ctx, "email");

        if (!ModConfig.getInstance().email.enabled) {
            ctx.getSource().sendFailure(Component.literal("§c邮件服务未启用喵！请先在 config.json 中启用 email.enabled 并配置 SMTP 参数喵~"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6🔍 正在发送 SMTP 调试邮件到 §b" + toEmail + " §6喵... 详细日志即将输出~"), false);

        EmailService.getInstance().sendDebugEmail(toEmail)
            .thenAccept(log -> {
                // 把多行日志拆分发送，避免聊天框截断
                String[] lines = log.split("\n");
                // 分批发送，每批最多 10 行
                StringBuilder batch = new StringBuilder();
                int count = 0;
                for (String line : lines) {
                    batch.append(line).append("\n");
                    count++;
                    if (count >= 10) {
                        final String batchStr = batch.toString();
                        ctx.getSource().sendSuccess(() -> Component.literal("§7" + batchStr.trim()), false);
                        batch = new StringBuilder();
                        count = 0;
                    }
                }
                if (count > 0) {
                    final String batchStr = batch.toString();
                    ctx.getSource().sendSuccess(() -> Component.literal("§7" + batchStr.trim()), false);
                }
            });

        return 1;
    }

    // ========== Utilities ==========

    /**
     * Partially mask an email address for display, e.g. {@code a***@example.com}.
     */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 1) {
            return email.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    private static long parseDuration(String input) {
        input = input.toLowerCase().trim();
        try {
            if (input.endsWith("d")) return Long.parseLong(input.replace("d", "")) * 86400;
            if (input.endsWith("h")) return Long.parseLong(input.replace("h", "")) * 3600;
            if (input.endsWith("m")) return Long.parseLong(input.replace("m", "")) * 60;
            if (input.endsWith("s")) return Long.parseLong(input.replace("s", ""));
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
