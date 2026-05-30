package org.cneko.nekowhitelist.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WhitelistDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir().resolve("neko_whitelist/data.json");

    private static WhitelistDataManager instance;

    private List<WhiteListEntry> whiteList = new ArrayList<>();
    private List<BlackListEntry> blackList = new ArrayList<>();

    public static WhitelistDataManager getInstance() {
        if (instance == null) {
            instance = new WhitelistDataManager();
        }
        return instance;
    }

    public void load() {
        if (!Files.exists(DATA_FILE)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<WhitelistDataFile>() {}.getType();
            WhitelistDataFile data = GSON.fromJson(reader, type);
            if (data != null) {
                whiteList = data.white != null ? data.white : new ArrayList<>();
                blackList = data.black != null ? data.black : new ArrayList<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            WhitelistDataFile data = new WhitelistDataFile(whiteList, blackList);
            try (Writer writer = Files.newBufferedWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addWhite(WhiteListEntry entry) {
        removeWhite(entry.name());
        whiteList.add(entry);
        save();
    }

    public boolean removeWhite(String name) {
        boolean removed = whiteList.removeIf(e -> e.name().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public boolean isWhitelisted(String name) {
        return whiteList.stream().anyMatch(e -> e.name().equalsIgnoreCase(name));
    }

    public Optional<WhiteListEntry> getWhiteEntry(String name) {
        return whiteList.stream().filter(e -> e.name().equalsIgnoreCase(name)).findFirst();
    }

    public boolean setEmail(String name, String email) {
        Optional<WhiteListEntry> entry = getWhiteEntry(name);
        if (entry.isPresent()) {
            WhiteListEntry old = entry.get();
            whiteList.removeIf(e -> e.name().equalsIgnoreCase(name));
            whiteList.add(new WhiteListEntry(old.name(), old.addTime(), old.adder(), old.method(), email));
            save();
            return true;
        }
        return false;
    }

    public boolean setEmailForPlayer(String name, String email) {
        if (isWhitelisted(name)) {
            return setEmail(name, email);
        } else {
            addWhite(new WhiteListEntry(name, "Player", email));
            return true;
        }
    }

    public List<WhiteListEntry> getWhiteList() {
        return new ArrayList<>(whiteList);
    }

    public void addBlack(BlackListEntry entry) {
        removeBlack(entry.name());
        blackList.add(entry);
        save();
    }

    public boolean removeBlack(String name) {
        boolean removed = blackList.removeIf(e -> e.name().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public boolean isBlacklisted(String name) {
        return blackList.stream().anyMatch(e -> e.name().equalsIgnoreCase(name));
    }

    public Optional<BlackListEntry> getBlackEntry(String name) {
        return blackList.stream().filter(e -> e.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<BlackListEntry> getBlackList() {
        return new ArrayList<>(blackList);
    }

    private record WhitelistDataFile(List<WhiteListEntry> white, List<BlackListEntry> black) {}
}
