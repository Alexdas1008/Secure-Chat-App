package server;

import models.Group;
import storage.FileStorage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe manager for chat groups.
 */
public class GroupManager {

    private static final Logger LOG = Logger.getLogger(GroupManager.class.getName());

    private final Map<String, Group> groups;

    public GroupManager() {
        this.groups = FileStorage.loadGroups();
        LOG.info("[Groups] Loaded " + groups.size() + " groups from disk.");
    }

    // ── Group lifecycle ──────────────────────────────────────────────────────

    /** Create a new group; returns {@code false} if name already taken. */
    public synchronized boolean createGroup(String name, String description, String creatorUsername) {
        if (groups.containsKey(name)) {
            LOG.warning("[Groups] Cannot create group '" + name + "' — already exists.");
            return false;
        }
        groups.put(name, new Group(name, description, creatorUsername));
        persist();
        LOG.info("[Groups] Created group '" + name + "' by " + creatorUsername);
        return true;
    }

    /** Add a member to an existing group; returns {@code false} if group not found. */
    public synchronized boolean joinGroup(String groupName, String username) {
        Group g = groups.get(groupName);
        if (g == null) return false;
        g.addMember(username);
        persist();
        LOG.info("[Groups] " + username + " joined group '" + groupName + "'");
        return true;
    }

    /** Remove a member from a group; returns {@code false} if group not found. */
    public synchronized boolean leaveGroup(String groupName, String username) {
        Group g = groups.get(groupName);
        if (g == null) return false;
        g.removeMember(username);
        persist();
        LOG.info("[Groups] " + username + " left group '" + groupName + "'");
        return true;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public synchronized Group getGroup(String name) {
        return groups.get(name);
    }

    public synchronized Map<String, Group> getAllGroups() {
        return new ConcurrentHashMap<>(groups);
    }

    /** Returns the usernames of all members in the group, or empty set. */
    public synchronized Set<String> getMembers(String groupName) {
        Group g = groups.get(groupName);
        return g == null ? Set.of() : g.getMembers();
    }

    public synchronized boolean groupExists(String name) {
        return groups.containsKey(name);
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void persist() {
        FileStorage.saveGroups(groups);
    }
}
