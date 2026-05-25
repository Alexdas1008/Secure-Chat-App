package server;

import models.Message;
import storage.FileStorage;

import java.util.List;
import java.util.logging.Logger;

/**
 * Manages chat history persistence and retrieval.
 * Thin wrapper around FileStorage that can be extended to support
 * asynchronous / buffered writes in future iterations.
 */
public class ChatHistoryManager {

    private static final Logger LOG = Logger.getLogger(ChatHistoryManager.class.getName());
    private static final int DEFAULT_HISTORY_LINES = 50;

    public ChatHistoryManager() {}

    /**
     * Persist a message to the appropriate history file.
     * Only PRIVATE, GROUP, and BROADCAST types are stored.
     */
    public void record(Message msg) {
        switch (msg.getType()) {
            case PRIVATE:
            case GROUP:
            case BROADCAST:
                FileStorage.appendHistory(msg);
                break;
            default:
                // system / control messages are not stored in chat history
                break;
        }
    }

    /**
     * Retrieve the last N lines of a private chat history between two users.
     * The filename is constructed to match what FileStorage writes.
     */
    public List<String> getPrivateHistory(String userA, String userB) {
        String[] pair = { userA, userB };
        java.util.Arrays.sort(pair);
        String filename = pair[0] + "__" + pair[1] + ".txt";
        return FileStorage.readHistory(filename, DEFAULT_HISTORY_LINES);
    }

    /**
     * Retrieve the last N lines of a group chat history.
     */
    public List<String> getGroupHistory(String groupName) {
        String filename = "group__" + groupName + ".txt";
        return FileStorage.readHistory(filename, DEFAULT_HISTORY_LINES);
    }

    /**
     * Retrieve broadcast history.
     */
    public List<String> getBroadcastHistory() {
        return FileStorage.readHistory("broadcast.txt", DEFAULT_HISTORY_LINES);
    }
}
