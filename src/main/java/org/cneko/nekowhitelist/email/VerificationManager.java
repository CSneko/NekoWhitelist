package org.cneko.nekowhitelist.email;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VerificationManager {
    private static VerificationManager instance;

    private final Map<String, PendingVerification> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>(); // player -> last send millis

    private static final long CODE_EXPIRY_SECONDS = 300; // 5 minutes
    private static final long COOLDOWN_SECONDS = 60;    // 60 seconds

    public static VerificationManager getInstance() {
        if (instance == null) {
            instance = new VerificationManager();
        }
        return instance;
    }

    public record PendingVerification(String playerName, String email, String code, Instant expiresAt) {}

    public String generateCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
    }

    public boolean canSend(String playerName) {
        cleanup();
        Long last = cooldowns.get(playerName.toLowerCase());
        if (last == null) return true;
        return (System.currentTimeMillis() - last) > COOLDOWN_SECONDS * 1000;
    }

    public long getCooldownRemaining(String playerName) {
        Long last = cooldowns.get(playerName.toLowerCase());
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        return Math.max(0, COOLDOWN_SECONDS - elapsed);
    }

    public void markSent(String playerName) {
        cooldowns.put(playerName.toLowerCase(), System.currentTimeMillis());
    }

    public void putCode(String playerName, String email, String code) {
        cleanup();
        // remove any existing pending for this player
        pending.values().removeIf(p -> p.playerName.equalsIgnoreCase(playerName));
        pending.put(code, new PendingVerification(playerName, email, code, Instant.now().plusSeconds(CODE_EXPIRY_SECONDS)));
    }

    public PendingVerification verify(String code) {
        cleanup();
        PendingVerification pv = pending.remove(code);
        if (pv == null) return null;
        if (Instant.now().isAfter(pv.expiresAt)) return null;
        return pv;
    }

    private void cleanup() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, PendingVerification>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingVerification> entry = it.next();
            if (now.isAfter(entry.getValue().expiresAt)) {
                it.remove();
            }
        }
    }
}
