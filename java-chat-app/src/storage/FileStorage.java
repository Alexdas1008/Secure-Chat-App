package storage;

import models.Group;
import models.Message;
import models.User;
import common.Protocol;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persistence layer using Java object serialization and plain text files.
 * All public methods are thread-safe.
 */
public class FileStorage {

    private static final Logger LOG = Logger.getLogger(FileStorage.class.getName());

    // ── Directory bootstrapping ──────────────────────────────────────────────

    public static void ensureDirectories() {
        String[] dirs = {
            "data/users", "data/groups", "data/history", "data/files", "data/config", "logs"
        };
        for (String d : dirs) new File(d).mkdirs();
    }

    // ────────────────────────────────────────────────────────────────────────
    // USER persistence
    // ────────────────────────────────────────────────────────────────────────

    /** Load all users from disk into a map keyed by username. */
    @SuppressWarnings("unchecked")
    public static synchronized Map<String, User> loadUsers() {
        File file = new File(Protocol.USERS_FILE);
        if (!file.exists()) return new ConcurrentHashMap<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, User> map = (Map<String, User>) ois.readObject();
            LOG.info("Loaded " + map.size() + " users from disk.");
            return new ConcurrentHashMap<>(map);
        } catch (Exception e) {
            LOG.warning("Could not load users: " + e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    /** Persist the full user map to disk (atomic write via temp file). */
    public static synchronized void saveUsers(Map<String, User> users) {
        File file    = new File(Protocol.USERS_FILE);
        File tmpFile = new File(Protocol.USERS_FILE + ".tmp");
        file.getParentFile().mkdirs();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmpFile))) {
            oos.writeObject(new HashMap<>(users));
            oos.flush();
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOG.severe("Could not save users: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // GROUP persistence
    // ────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static synchronized Map<String, Group> loadGroups() {
        File file = new File(Protocol.GROUPS_FILE);
        if (!file.exists()) return new ConcurrentHashMap<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, Group> map = (Map<String, Group>) ois.readObject();
            LOG.info("Loaded " + map.size() + " groups from disk.");
            return new ConcurrentHashMap<>(map);
        } catch (Exception e) {
            LOG.warning("Could not load groups: " + e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    public static synchronized void saveGroups(Map<String, Group> groups) {
        File file    = new File(Protocol.GROUPS_FILE);
        File tmpFile = new File(Protocol.GROUPS_FILE + ".tmp");
        file.getParentFile().mkdirs();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmpFile))) {
            oos.writeObject(new HashMap<>(groups));
            oos.flush();
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOG.severe("Could not save groups: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // CHAT HISTORY persistence  (one file per conversation / group)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Appends a single message to the relevant history file.
     * For private chats the file is named "alice__bob.txt" (sorted alphabetically).
     * For group chats it is "group__general.txt".
     */
    public static synchronized void appendHistory(Message msg) {
        String filename;
        if (msg.getType() == Message.Type.GROUP) {
            filename = Protocol.HISTORY_DIR + "group__" + sanitize(msg.getGroupName()) + ".txt";
        } else if (msg.getType() == Message.Type.PRIVATE) {
            String[] pair = { msg.getSenderUsername(), msg.getRecipientUsername() };
            Arrays.sort(pair);
            filename = Protocol.HISTORY_DIR + sanitize(pair[0]) + "__" + sanitize(pair[1]) + ".txt";
        } else {
            filename = Protocol.HISTORY_DIR + "broadcast.txt";
        }

        File file = new File(filename);
        file.getParentFile().mkdirs();

        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
            pw.println(msg.getFormattedTimestamp()
                    + " | " + msg.getSenderUsername()
                    + " | " + msg.getType()
                    + " | " + (msg.getEncryptedContent() != null ? msg.getEncryptedContent() : msg.getPlainContent()));
        } catch (IOException e) {
            LOG.warning("Could not append history: " + e.getMessage());
        }
    }

    /**
     * Reads the last {@code limit} lines from a history file.
     */
    public static synchronized List<String> readHistory(String filename, int limit) {
        File file = new File(Protocol.HISTORY_DIR + filename);
        if (!file.exists()) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            LOG.warning("Could not read history: " + e.getMessage());
        }
        // Return last `limit` lines
        int from = Math.max(0, lines.size() - limit);
        return lines.subList(from, lines.size());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Strip characters unsafe for file names. */
    private static String sanitize(String s) {
        return s == null ? "null" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
