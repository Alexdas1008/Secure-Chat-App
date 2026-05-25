package models;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents a registered user in the chat application.
 * Implements Serializable for file-based persistence.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String passwordHash;   // SHA-256 hash stored, never plain text
    private String displayName;
    private String email;
    private LocalDateTime registeredAt;
    private LocalDateTime lastLoginAt;
    private boolean online;
    private String currentServerId;  // which server node the user is connected to

    public User(String username, String passwordHash, String displayName, String email) {
        this.username      = username;
        this.passwordHash  = passwordHash;
        this.displayName   = displayName;
        this.email         = email;
        this.registeredAt  = LocalDateTime.now();
        this.online        = false;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public String getUsername()        { return username; }
    public String getPasswordHash()    { return passwordHash; }
    public String getDisplayName()     { return displayName; }
    public String getEmail()           { return email; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public LocalDateTime getLastLoginAt()  { return lastLoginAt; }
    public boolean isOnline()          { return online; }
    public String getCurrentServerId() { return currentServerId; }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setDisplayName(String displayName)  { this.displayName   = displayName; }
    public void setEmail(String email)              { this.email         = email; }
    public void setLastLoginAt(LocalDateTime t)     { this.lastLoginAt   = t; }
    public void setOnline(boolean online)           { this.online        = online; }
    public void setCurrentServerId(String id)       { this.currentServerId = id; }
    public void setPasswordHash(String hash)        { this.passwordHash  = hash; }

    @Override
    public String toString() {
        return "User{username='" + username + "', displayName='" + displayName
                + "', online=" + online + "}";
    }
}
