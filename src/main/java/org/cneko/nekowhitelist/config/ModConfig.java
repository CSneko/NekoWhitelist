package org.cneko.nekowhitelist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("neko_whitelist/config.json");
    private static final int CURRENT_VERSION = 1;

    private static ModConfig instance;

    public int configVersion = CURRENT_VERSION;
    public boolean enabled = true;
    public boolean whitelistEnabled = false;
    public boolean blacklistEnabled = true;
    public EmailConfig email = new EmailConfig();

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }

    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded != null) {
                this.enabled = loaded.enabled;
                this.whitelistEnabled = loaded.whitelistEnabled;
                this.blacklistEnabled = loaded.blacklistEnabled;
                if (loaded.email != null) {
                    this.email = loaded.email;
                }
                if (loaded.configVersion < CURRENT_VERSION) {
                    migrate(loaded.configVersion);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 保存以更新配置文件中的新字段
        save();
    }

    private void migrate(int fromVersion) {
        if (fromVersion < 1) {
            // 示例: 从版本0迁移到版本1
        }
        this.configVersion = CURRENT_VERSION;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class EmailConfig {
        public boolean enabled = false;
        public String smtpHost = "smtp.example.com";
        public int smtpPort = 587;
        public String username = "";
        public String password = "";
        public String senderEmail = "";
        public boolean useTls = true;
        public String templateDir = "templates";  // 相对于 config/neko_whitelist/ 的自定义模板目录
    }
}
