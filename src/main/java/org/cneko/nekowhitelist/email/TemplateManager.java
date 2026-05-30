package org.cneko.nekowhitelist.email;

import net.fabricmc.loader.api.FabricLoader;
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
    private static final Path CONFIG_BASE = FabricLoader.getInstance().getConfigDir().resolve("neko_whitelist");
    private static final String BUILTIN_PREFIX = "templates/email/";

    /** 内置模板文件列表 — 新增模板时在此添加即可 */
    private static final String[] BUILTIN_TEMPLATES = {
        "verification.html",
        "debug.html"
    };

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
