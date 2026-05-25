package models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a chat message in the system.
 * Supports private, group, and broadcast message types.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 2L;

    public enum Type {
        PRIVATE,        // one-to-one message
        GROUP,          // message to a named group
        BROADCAST,      // message to all connected users
        SYSTEM,         // server system notification
        FILE_OFFER,     // notification that a file transfer is being offered
        FILE_ACCEPT,    // recipient accepted the file
        FILE_REJECT,    // recipient rejected the file
        JOIN_GROUP,     // request to join a group
        LEAVE_GROUP,    // leave a group
        CREATE_GROUP,   // create a new group
        AUTH_LOGIN,     // login request
        AUTH_REGISTER,  // registration request
        AUTH_LOGOUT,    // logout notification
        AUTH_RESPONSE,  // server response to auth request
        USER_LIST,      // server sending online user list
        GROUP_LIST,     // server sending group list
        HEARTBEAT       // keep-alive ping
    }

    private String id;                   // unique message id (UUID-like)
    private Type   type;
    private String senderUsername;
    private String recipientUsername;    // null for group / broadcast
    private String groupName;            // null for private messages
    private String encryptedContent;     // AES-256 encrypted payload
    private String plainContent;         // only populated after decryption client-side
    private LocalDateTime timestamp;
    private boolean delivered;
    // File-transfer metadata (populated for FILE_OFFER type)
    private String fileName;
    private long   fileSize;
    private int    fileTransferPort;     // ephemeral port for the file stream

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Message(Type type, String senderUsername, String encryptedContent) {
        this.id               = generateId();
        this.type             = type;
        this.senderUsername   = senderUsername;
        this.encryptedContent = encryptedContent;
        this.timestamp        = LocalDateTime.now();
        this.delivered        = false;
    }

    // ── Static factory helpers ───────────────────────────────────────────────

    public static Message privateMessage(String from, String to, String encryptedContent) {
        Message m = new Message(Type.PRIVATE, from, encryptedContent);
        m.recipientUsername = to;
        return m;
    }

    public static Message groupMessage(String from, String group, String encryptedContent) {
        Message m = new Message(Type.GROUP, from, encryptedContent);
        m.groupName = group;
        return m;
    }

    public static Message systemMessage(String content) {
        Message m = new Message(Type.SYSTEM, "SERVER", content);
        m.plainContent = content;
        return m;
    }

    public static Message broadcastMessage(String from, String encryptedContent) {
        return new Message(Type.BROADCAST, from, encryptedContent);
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public String getId()                 { return id; }
    public Type   getType()               { return type; }
    public String getSenderUsername()     { return senderUsername; }
    public String getRecipientUsername()  { return recipientUsername; }
    public String getGroupName()          { return groupName; }
    public String getEncryptedContent()   { return encryptedContent; }
    public String getPlainContent()       { return plainContent; }
    public LocalDateTime getTimestamp()   { return timestamp; }
    public boolean isDelivered()          { return delivered; }
    public String getFileName()           { return fileName; }
    public long   getFileSize()           { return fileSize; }
    public int    getFileTransferPort()   { return fileTransferPort; }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setRecipientUsername(String r)  { this.recipientUsername = r; }
    public void setGroupName(String g)          { this.groupName = g; }
    public void setEncryptedContent(String c)   { this.encryptedContent = c; }
    public void setPlainContent(String c)       { this.plainContent = c; }
    public void setDelivered(boolean d)         { this.delivered = d; }
    public void setFileName(String n)           { this.fileName = n; }
    public void setFileSize(long s)             { this.fileSize = s; }
    public void setFileTransferPort(int p)      { this.fileTransferPort = p; }

    public String getFormattedTimestamp() {
        return timestamp.format(FMT);
    }

    /** Simple but unique enough ID for our use-case */
    private static String generateId() {
        return Long.toHexString(System.nanoTime()) + Long.toHexString((long)(Math.random() * Long.MAX_VALUE));
    }

    @Override
    public String toString() {
        return "[" + getFormattedTimestamp() + "] " + senderUsername + " → "
                + (groupName != null ? "#" + groupName
                        : recipientUsername != null ? recipientUsername : "ALL")
                + ": " + (plainContent != null ? plainContent : "<encrypted>");
    }
}
