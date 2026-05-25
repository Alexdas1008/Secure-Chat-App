package server;

import encryption.EncryptionUtil;
import models.User;
import storage.FileStorage;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe authentication manager.
 * Handles registration, login, logout, and session tracking.
 */
public class AuthenticationManager {

    private static final Logger LOG = Logger.getLogger(AuthenticationManager.class.getName());

    /** In-memory user store; backed by serialized file on disk. */
    private final Map<String, User> users;

    /** Tracks which usernames are currently logged in (username → serverId). */
    private final Map<String, String> onlineSessions = new ConcurrentHashMap<>();

    public AuthenticationManager() {
        this.users = FileStorage.loadUsers();
        LOG.info("[Auth] Loaded " + users.size() + " user accounts.");
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     *
     * @return {@code true} on success, {@code false} if username already taken.
     */
    public synchronized boolean register(String username, String plainPassword,
                                         String displayName, String email) {
        if (users.containsKey(username)) {
            LOG.warning("[Auth] Registration failed — username '" + username + "' already exists.");
            return false;
        }
        try {
            String hash = EncryptionUtil.hashPassword(plainPassword);
            User user   = new User(username, hash, displayName, email);
            users.put(username, user);
            FileStorage.saveUsers(users);
            LOG.info("[Auth] Registered new user: " + username);
            return true;
        } catch (Exception e) {
            LOG.severe("[Auth] Error registering user: " + e.getMessage());
            return false;
        }
    }

    // ── Login / Logout ───────────────────────────────────────────────────────

    /**
     * Authenticates a user.
     *
     * @return the {@link User} object on success, or {@code null} on failure.
     */
    public synchronized User login(String username, String plainPassword, String serverId) {
        User user = users.get(username);
        if (user == null) {
            LOG.warning("[Auth] Login failed — unknown user: " + username);
            return null;
        }
        if (onlineSessions.containsKey(username)) {
            LOG.warning("[Auth] Login failed — user already online: " + username);
            return null;   // prevent concurrent sessions
        }
        try {
            if (!EncryptionUtil.verifyPassword(plainPassword, user.getPasswordHash())) {
                LOG.warning("[Auth] Login failed — bad password for: " + username);
                return null;
            }
            user.setOnline(true);
            user.setLastLoginAt(LocalDateTime.now());
            user.setCurrentServerId(serverId);
            onlineSessions.put(username, serverId);
            FileStorage.saveUsers(users);
            LOG.info("[Auth] User logged in: " + username + " on server " + serverId);
            return user;
        } catch (Exception e) {
            LOG.severe("[Auth] Error during login: " + e.getMessage());
            return null;
        }
    }

    public synchronized void logout(String username) {
        User user = users.get(username);
        if (user != null) {
            user.setOnline(false);
            user.setCurrentServerId(null);
        }
        onlineSessions.remove(username);
        FileStorage.saveUsers(users);
        LOG.info("[Auth] User logged out: " + username);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public synchronized boolean isOnline(String username) {
        return onlineSessions.containsKey(username);
    }

    public synchronized User getUser(String username) {
        return users.get(username);
    }

    public synchronized Map<String, User> getAllUsers() {
        return new ConcurrentHashMap<>(users);
    }

    public synchronized Map<String, String> getOnlineSessions() {
        return new ConcurrentHashMap<>(onlineSessions);
    }
}
