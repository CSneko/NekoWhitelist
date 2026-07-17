package org.cneko.nekowhitelist.email;

import net.neoforged.fml.loading.FMLPaths;
import org.cneko.nekowhitelist.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTML 邮件模板管理器
 * 加载优先级：config/neko_whitelist/{templateDir}/ → jar 内置 resources/templates/email/
 */
public class TemplateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("NekoWhitelist:Template");
    private static final Path CONFIG_BASE = FMLPaths.CONFIGDIR.get().resolve("neko_whitelist");
    private static final String BUILTIN_PREFIX = "templates/email/";

    /** 内置模板文件列表 — 新增模板时在此添加即可 */
    private static final String[] BUILTIN_TEMPLATES = {
        "verification.html",
        "debug.html",
        "password_reset.html"
    };

    /** 默认验证码模板 — 当没有任何模板时自动生成 */
    private static final String DEFAULT_VERIFICATION_TEMPLATE =
        "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n" +
        "<title>NekoWhitelist 邮箱验证</title>\n</head>\n" +
        "<body style=\"margin:0;padding:0;background-color:#fdf6f9;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;\">\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#fdf6f9;padding:40px 0;\">\n" +
        "<tr><td align=\"center\">\n" +
        "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(255,150,180,0.15);\">\n" +
        "<tr><td style=\"background:linear-gradient(135deg,#ff9ec4,#ffc8dd);padding:32px 40px;text-align:center;\">\n" +
        "<h1 style=\"margin:0;font-size:26px;color:#fff;\">🐾 NekoWhitelist 喵~</h1>\n" +
        "<p style=\"margin:8px 0 0 0;font-size:14px;color:#fff5f8;\">邮箱验证码</p>\n" +
        "</td></tr>\n" +
        "<tr><td style=\"padding:40px;\">\n" +
        "<p style=\"margin:0 0 16px 0;font-size:15px;color:#5a4a4f;\">喵喵~ 你好呀！</p>\n" +
        "<p style=\"margin:0 0 24px 0;font-size:15px;color:#5a4a4f;\">你正在绑定 <strong style=\"color:#e8789a;\">NekoWhitelist</strong> 的邮箱喵~<br>请使用下面的验证码完成验证：</p>\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px 0;\">\n" +
        "<tr><td style=\"background-color:#fff5f8;border:2px dashed #ffb8d0;border-radius:12px;padding:24px;text-align:center;\">\n" +
        "<span style=\"font-size:36px;font-weight:bold;color:#e8789a;letter-spacing:8px;font-family:'Courier New',monospace;\">{{code}}</span>\n" +
        "</td></tr></table>\n" +
        "<p style=\"margin:0 0 8px 0;font-size:13px;color:#9a8a8f;\">⏰ 验证码有效期为 <strong>{{expiry_minutes}}</strong> 分钟，请尽快使用喵~</p>\n" +
        "<p style=\"margin:0 0 8px 0;font-size:13px;color:#9a8a8f;\">📋 在游戏中使用命令：<code style=\"background:#f5f0f2;padding:2px 8px;border-radius:4px;color:#d0678a;\">/nw email verify {{code}}</code></p>\n" +
        "<hr style=\"border:none;border-top:1px solid #f0e0e8;margin:24px 0;\">\n" +
        "<p style=\"margin:0;font-size:12px;color:#bfa8b0;\">如果这不是你操作的，请忽略这封邮件喵~<br>你的邮箱地址不会被用于其他用途喵~</p>\n" +
        "</td></tr>\n" +
        "<tr><td style=\"background-color:#fdf6f9;padding:20px 40px;text-align:center;\">\n" +
        "<p style=\"margin:0;font-size:12px;color:#c8a8b4;\">🐱 NekoWhitelist 猫娘管理团队<br><span style=\"color:#d8b8c4;\">—— 守护每一只可爱玩家喵~</span></p>\n" +
        "</td></tr>\n" +
        "</table>\n</td></tr>\n</table>\n</body>\n</html>";

    /** 默认调试邮件模板 — 当没有任何模板时自动生成 */
    private static final String DEFAULT_DEBUG_TEMPLATE =
        "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n" +
        "<title>NekoWhitelist 调试邮件</title>\n</head>\n" +
        "<body style=\"margin:0;padding:0;background-color:#f5f7fa;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;\">\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f5f7fa;padding:40px 0;\">\n" +
        "<tr><td align=\"center\">\n" +
        "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(100,120,180,0.12);\">\n" +
        "<tr><td style=\"background:linear-gradient(135deg,#7eb8da,#a0d2f0);padding:32px 40px;text-align:center;\">\n" +
        "<h1 style=\"margin:0;font-size:26px;color:#fff;\">🔧 NekoWhitelist 调试邮件</h1>\n" +
        "<p style=\"margin:8px 0 0 0;font-size:14px;color:#f0f6fb;\">SMTP 配置测试</p>\n" +
        "</td></tr>\n" +
        "<tr><td style=\"padding:40px;\">\n" +
        "<p style=\"margin:0 0 16px 0;font-size:15px;color:#4a505a;\">喵喵~ 你好呀！</p>\n" +
        "<p style=\"margin:0 0 24px 0;font-size:15px;color:#4a505a;\">这是一封来自 <strong style=\"color:#5a8fba;\">NekoWhitelist</strong> 的调试测试邮件喵~<br>如果你收到了这封邮件，说明 SMTP 配置完全正确喵！</p>\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px 0;\">\n" +
        "<tr><td style=\"background-color:#f5f8fb;border:2px solid #d0dff0;border-radius:12px;padding:20px;\">\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n" +
        "<tr><td style=\"padding:4px 0;font-size:13px;color:#6a7a8a;width:90px;\">🕐 发送时间</td><td style=\"padding:4px 0;font-size:13px;color:#3a4a5a;\">{{timestamp}}</td></tr>\n" +
        "<tr><td style=\"padding:4px 0;font-size:13px;color:#6a7a8a;\">📡 SMTP 服务器</td><td style=\"padding:4px 0;font-size:13px;color:#3a4a5a;\">{{smtp_host}}</td></tr>\n" +
        "</table></td></tr></table>\n" +
        "<p style=\"margin:0 0 4px 0;font-size:14px;color:#5a8fba;font-weight:bold;\">✅ 一切正常喵！</p>\n" +
        "<p style=\"margin:0;font-size:13px;color:#8a9aaa;\">邮件服务已正确配置并可以正常发送邮件喵~</p>\n" +
        "<hr style=\"border:none;border-top:1px solid #e8edf3;margin:24px 0;\">\n" +
        "<p style=\"margin:0;font-size:12px;color:#a0aeb8;\">这是一封自动生成的测试邮件，无需回复喵~</p>\n" +
        "</td></tr>\n" +
        "<tr><td style=\"background-color:#f5f7fa;padding:20px 40px;text-align:center;\">\n" +
        "<p style=\"margin:0;font-size:12px;color:#b0bcc8;\">🐱 NekoWhitelist 猫娘管理团队<br><span style=\"color:#c0ccd8;\">—— 守护每一只可爱玩家喵~</span></p>\n" +
        "</td></tr>\n" +
        "</table>\n</td></tr>\n</table>\n</body>\n</html>";

    /** 默认密码重置模板 — 当没有任何模板时自动生成 */
    private static final String DEFAULT_PASSWORD_RESET_TEMPLATE =
        "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n" +
        "<title>NekoWhitelist 密码重置</title>\n</head>\n" +
        "<body style=\"margin:0;padding:0;background-color:#fdf6f9;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;\">\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#fdf6f9;padding:40px 0;\">\n" +
        "<tr><td align=\"center\">\n" +
        "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(255,150,180,0.15);\">\n" +
        "<tr><td style=\"background:linear-gradient(135deg,#ffb347,#ffcc80);padding:32px 40px;text-align:center;\">\n" +
        "<h1 style=\"margin:0;font-size:26px;color:#fff;\">🐾 NekoWhitelist 密码重置喵~</h1>\n" +
        "<p style=\"margin:8px 0 0 0;font-size:14px;color:#fff8f0;\">找回你的账户</p>\n" +
        "</td></tr>\n" +
        "<tr><td style=\"padding:40px;\">\n" +
        "<p style=\"margin:0 0 16px 0;font-size:15px;color:#5a4a4f;\">喵喵~ 你好呀！</p>\n" +
        "<p style=\"margin:0 0 24px 0;font-size:15px;color:#5a4a4f;\">你正在重置 <strong style=\"color:#e8789a;\">NekoWhitelist</strong> 的登录密码喵~<br>请使用下面的验证码完成密码重置：</p>\n" +
        "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px 0;\">\n" +
        "<tr><td style=\"background-color:#fff8f0;border:2px dashed #ffcc80;border-radius:12px;padding:24px;text-align:center;\">\n" +
        "<span style=\"font-size:36px;font-weight:bold;color:#e8789a;letter-spacing:8px;font-family:'Courier New',monospace;\">{{code}}</span>\n" +
        "</td></tr></table>\n" +
        "<p style=\"margin:0 0 8px 0;font-size:13px;color:#9a8a8f;\">⏰ 验证码有效期为 <strong>{{expiry_minutes}}</strong> 分钟，请尽快使用喵~</p>\n" +
        "<p style=\"margin:0 0 8px 0;font-size:13px;color:#9a8a8f;\">📋 在游戏中使用命令：<code style=\"background:#f5f0f2;padding:2px 8px;border-radius:4px;color:#d0678a;\">/resetpassword {{code}} &lt;新密码&gt;</code></p>\n" +
        "<hr style=\"border:none;border-top:1px solid #f0e0e8;margin:24px 0;\">\n" +
        "<p style=\"margin:0;font-size:12px;color:#bfa8b0;\">如果这不是你操作的，请忽略这封邮件喵~<br>你的账号密码不会被修改喵~</p>\n" +
        "</td></tr>\n" +
        "<tr><td style=\"background-color:#fdf6f9;padding:20px 40px;text-align:center;\">\n" +
        "<p style=\"margin:0;font-size:12px;color:#c8a8b4;\">🐱 NekoWhitelist 猫娘管理团队<br><span style=\"color:#d8b8c4;\">—— 守护每一只可爱玩家喵~</span></p>\n" +
        "</td></tr>\n" +
        "</table>\n</td></tr>\n</table>\n</body>\n</html>";

    private static TemplateManager instance;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    public static TemplateManager getInstance() {
        if (instance == null) {
            instance = new TemplateManager();
        }
        return instance;
    }

    /**
     * 初始化模板目录：创建自定义模板目录并将内置模板复制过去（如果还不存在的话）
     * 调用此方法后，用户可以在 config/neko_whitelist/templates/ 下自定义模板
     */
    public void init() {
        if (initialized) return;
        initialized = true;

        Path customDir = resolveCustomTemplateDir();
        try {
            Files.createDirectories(customDir);
            LOGGER.info("模板目录已就绪: {}", customDir);
        } catch (IOException e) {
            LOGGER.error("无法创建模板目录: {}", customDir, e);
            return;
        }

        int copied = 0;
        for (String templateName : BUILTIN_TEMPLATES) {
            Path targetPath = customDir.resolve(templateName);
            if (Files.exists(targetPath)) {
                LOGGER.debug("模板已存在，跳过: {}", targetPath);
                continue;
            }
            // 从 jar 内置资源复制
            String resourcePath = BUILTIN_PREFIX + templateName;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    LOGGER.warn("内置模板不存在于 jar 中: {}", resourcePath);
                    continue;
                }
                Files.copy(is, targetPath);
                copied++;
                LOGGER.info("已从 jar 复制内置模板: {} -> {}", resourcePath, targetPath);
            } catch (IOException e) {
                LOGGER.error("复制内置模板失败: {} -> {}", resourcePath, e);
            }
        }
        if (copied > 0) {
            LOGGER.info("共复制 {} 个内置模板到自定义模板目录喵~", copied);
        }

        // 兜底：如果模板目录下没有任何 .html 文件，就地生成默认模板
        ensureTemplatesExist(customDir);
    }

    /**
     * 加载并渲染模板
     * @param templateName 模板文件名（如 "verification.html"）
     * @param variables    变量键值对，模板中的 {{key}} 会被替换为 value
     * @return 渲染后的 HTML 字符串
     */
    public String render(String templateName, Map<String, String> variables) {
        String template = loadTemplate(templateName);
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * 清除缓存，下次访问时重新从磁盘/jar 加载
     */
    public void reload() {
        cache.clear();
        LOGGER.info("模板缓存已清除喵~");
    }

    /**
     * 获取模板目录路径（供外部查看/管理）
     */
    public Path getCustomTemplateDir() {
        String dir = ModConfig.getInstance().email.templateDir;
        return CONFIG_BASE.resolve(dir);
    }

    // ========== 内部实现 ==========

    /**
     * 兜底保障：如果模板目录下没有任何 .html 文件，则自动生成默认模板
     */
    private void ensureTemplatesExist(Path customDir) {
        try {
            boolean hasHtml = false;
            try (var stream = Files.list(customDir)) {
                hasHtml = stream.anyMatch(p -> p.getFileName().toString().endsWith(".html"));
            }
            if (!hasHtml) {
                LOGGER.warn("模板目录下没有找到任何 HTML 模板，正在生成默认模板喵~");
                writeDefaultTemplate(customDir.resolve("verification.html"), DEFAULT_VERIFICATION_TEMPLATE);
                writeDefaultTemplate(customDir.resolve("debug.html"), DEFAULT_DEBUG_TEMPLATE);
                writeDefaultTemplate(customDir.resolve("password_reset.html"), DEFAULT_PASSWORD_RESET_TEMPLATE);
                LOGGER.info("默认模板已生成在 {} 喵~", customDir);
            }
        } catch (IOException e) {
            LOGGER.error("检查模板目录时出错: {}", customDir, e);
        }
    }

    private void writeDefaultTemplate(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            LOGGER.info("已创建默认模板: {}", path.getFileName());
        } catch (IOException e) {
            LOGGER.error("无法创建默认模板: {}", path, e);
        }
    }

    private Path resolveCustomTemplateDir() {
        String dir = ModConfig.getInstance().email.templateDir;
        // 防止路径穿越：只取配置中的最后一段目录名
        if (dir.contains("/") || dir.contains("\\") || dir.contains("..")) {
            LOGGER.warn("模板目录名包含非法字符: {} — 使用默认值 'templates'", dir);
            dir = "templates";
        }
        return CONFIG_BASE.resolve(dir);
    }

    private String loadTemplate(String templateName) {
        // 1. 优先从自定义目录加载
        Path customDir = resolveCustomTemplateDir();
        Path customPath = customDir.resolve(templateName);
        if (Files.exists(customPath)) {
            String cached = cache.get("custom:" + templateName);
            if (cached != null) return cached;
            try {
                String content = Files.readString(customPath, StandardCharsets.UTF_8);
                cache.put("custom:" + templateName, content);
                LOGGER.debug("从自定义目录加载模板: {}", customPath);
                return content;
            } catch (IOException e) {
                LOGGER.warn("读取自定义模板失败: {} — 回退到内置模板", customPath);
            }
        }

        // 2. 回退到 jar 内置模板
        String cached = cache.get("builtin:" + templateName);
        if (cached != null) return cached;

        String resourcePath = BUILTIN_PREFIX + templateName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("内置模板不存在: " + resourcePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            cache.put("builtin:" + templateName, content);
            LOGGER.debug("加载内置模板: {}", resourcePath);
            return content;
        } catch (IOException e) {
            LOGGER.error("无法加载模板: {}", templateName, e);
            // 最终兜底：返回纯文本提示
            return "<html><body><p>模板加载失败: " + templateName + "</p></body></html>";
        }
    }
}
