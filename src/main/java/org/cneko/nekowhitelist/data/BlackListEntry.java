package org.cneko.nekowhitelist.data;

public record BlackListEntry(
    String name,
    String banTime,
    String reason,
    String message,
    String unbanTime,
    String operator
) {
    public BlackListEntry {
        if (banTime == null) banTime = "null";
        if (reason == null) reason = "";
        if (message == null) message = "You are banned from this server.";
        if (unbanTime == null) unbanTime = "null";
        if (operator == null) operator = "null";
    }

    public BlackListEntry(String name, String reason, String message, String unbanTime, String operator) {
        this(name, java.time.Instant.now().toString(), reason, message, unbanTime, operator);
    }
}
