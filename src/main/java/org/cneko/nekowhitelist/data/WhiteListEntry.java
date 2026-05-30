package org.cneko.nekowhitelist.data;

public record WhiteListEntry(
    String name,
    String addTime,
    String adder,
    String method,
    String email
) {
    public WhiteListEntry {
        if (addTime == null) addTime = "null";
        if (adder == null) adder = "null";
        if (method == null) method = "in-game";
        if (email == null) email = "null";
    }

    public WhiteListEntry(String name, String adder) {
        this(name, java.time.Instant.now().toString(), adder, "in-game", "null");
    }

    public WhiteListEntry(String name, String adder, String email) {
        this(name, java.time.Instant.now().toString(), adder, "in-game", email != null ? email : "null");
    }
}
