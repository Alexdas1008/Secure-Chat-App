package client;

import common.Protocol;
import encryption.EncryptionUtil;
import gui.ChatDashboard;
import models.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Network layer for the chat client.
 *
 * <p>Manages the TCP socket connection to the server, serialises outgoing
 * {@link Message} objects, and deserialises incoming ones, dispatching each
 * to a registered {@link Consumer} callback so the GUI stays decoupled from
 * the network layer.</p>
 *
 * <p>A single daemon thread handles all inbound reads to keep the EDT free.</p>
 */
public class ChatClient {

    private static final Logger LOG = Logger.getLogger(ChatClient.class.getName());

    private final String host;
    private final int    port;

    private Socket            socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private volatile boolean   connected = false;

    /** Callback invoked on every inbound message (runs on reader thread — dispatch to EDT). */
    private Consumer<Message> messageHandler;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Connection lifecycle ─────────────────────────────────────────────────

    /**
     * Opens the socket connection and starts the background reader thread.
     *
     * @throws IOException if the server is unreachable.
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(0);  // no read timeout on client — server sends heartbeats
        out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.flush();
        in  = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        connected = true;

        Thread reader = new Thread(this::readLoop, "Client-Reader");
        reader.setDaemon(true);
        reader.start();

        LOG.info("[Client] Connected to " + host + ":" + port);
    }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        LOG.info("[Client] Disconnected.");
    }

    public boolean isConnected() { return connected; }

    // ── Inbound reader ────────────────────────────────────────────────────────

    private void readLoop() {
        while (connected) {
            try {
                Message msg = (Message) in.readObject();
                if (msg == null) break;
                decryptIfNeeded(msg);
                if (messageHandler != null) messageHandler.accept(msg);
            } catch (EOFException | java.net.SocketException e) {
                break;
            } catch (Exception e) {
                if (connected) LOG.warning("[Client] Read error: " + e.getMessage());
                break;
            }
        }
        connected = false;
        // Notify UI of disconnection
        if (messageHandler != null) {
            messageHandler.accept(Message.systemMessage("Disconnected from server."));
        }
    }

    /**
     * Decrypts the encrypted content of messages that carry ciphertext,
     * placing the plain text into {@code msg.plainContent}.
     */
    private void decryptIfNeeded(Message msg) {
        if (msg.getEncryptedContent() == null) return;
        switch (msg.getType()) {
            case PRIVATE:
            case GROUP:
            case BROADCAST:
                try {
                    String plain = EncryptionUtil.decrypt(msg.getEncryptedContent());
                    msg.setPlainContent(plain);
                } catch (Exception e) {
                    msg.setPlainContent("<decryption error>");
                }
                break;
            case SYSTEM:
            case AUTH_RESPONSE:
            case USER_LIST:
            case GROUP_LIST:
                // These carry plain text in encryptedContent (server shortcuts)
                if (msg.getPlainContent() == null) {
                    msg.setPlainContent(msg.getEncryptedContent());
                }
                break;
            default:
                break;
        }
    }

    // ── Outbound send helpers ─────────────────────────────────────────────────

    /** Low-level send — thread-safe. */
    public synchronized void send(Message msg) {
        if (!connected) { LOG.warning("[Client] Not connected — cannot send."); return; }
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOG.warning("[Client] Send error: " + e.getMessage());
            connected = false;
        }
    }

    // ── High-level protocol helpers ───────────────────────────────────────────

    public void sendLogin(String username, String password) throws Exception {
        String payload = EncryptionUtil.encrypt(username + Protocol.SEP + password);
        Message msg = new Message(Message.Type.AUTH_LOGIN, username, payload);
        send(msg);
    }

    public void sendRegister(String username, String password,
                             String displayName, String email) throws Exception {
        String payload = EncryptionUtil.encrypt(
                username + Protocol.SEP + password + Protocol.SEP + displayName + Protocol.SEP + email);
        Message msg = new Message(Message.Type.AUTH_REGISTER, username, payload);
        send(msg);
    }

    public void sendLogout(String username) {
        send(new Message(Message.Type.AUTH_LOGOUT, username, null));
    }

    public void sendPrivateMessage(String from, String to, String plainText) throws Exception {
        String encrypted = EncryptionUtil.encrypt(plainText);
        send(Message.privateMessage(from, to, encrypted));
    }

    public void sendGroupMessage(String from, String groupName, String plainText) throws Exception {
        String encrypted = EncryptionUtil.encrypt(plainText);
        send(Message.groupMessage(from, groupName, encrypted));
    }

    public void sendBroadcast(String from, String plainText) throws Exception {
        String encrypted = EncryptionUtil.encrypt(plainText);
        send(Message.broadcastMessage(from, encrypted));
    }

    public void sendCreateGroup(String from, String groupName, String description) throws Exception {
        String payload = EncryptionUtil.encrypt(groupName + Protocol.SEP + description);
        Message msg = new Message(Message.Type.CREATE_GROUP, from, payload);
        send(msg);
    }

    public void sendJoinGroup(String from, String groupName) throws Exception {
        String payload = EncryptionUtil.encrypt(groupName);
        Message msg = new Message(Message.Type.JOIN_GROUP, from, payload);
        send(msg);
    }

    public void sendLeaveGroup(String from, String groupName) throws Exception {
        String payload = EncryptionUtil.encrypt(groupName);
        Message msg = new Message(Message.Type.LEAVE_GROUP, from, payload);
        send(msg);
    }

    public void sendFileOffer(String from, String to, String fileName,
                              long fileSize, int transferPort) {
        Message msg = new Message(Message.Type.FILE_OFFER, from, null);
        msg.setRecipientUsername(to);
        msg.setFileName(fileName);
        msg.setFileSize(fileSize);
        msg.setFileTransferPort(transferPort);
        send(msg);
    }

    public void sendHeartbeat(String username) {
        send(new Message(Message.Type.HEARTBEAT, username, null));
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    public void setMessageHandler(Consumer<Message> handler) {
        this.messageHandler = handler;
    }

    public String getHost() { return host; }
    public int    getPort() { return port; }
}
