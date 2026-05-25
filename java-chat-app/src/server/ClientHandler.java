package server;

import common.Protocol;
import encryption.EncryptionUtil;
import filetransfer.FileTransferManager;
import models.Group;
import models.Message;
import models.User;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Handles a single connected client on its own thread.
 * Reads incoming {@link Message} objects from the socket's ObjectInputStream
 * and dispatches them to the server for routing.
 */
public class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final Socket          socket;
    private final ChatServer      server;
    private ObjectInputStream     in;
    private ObjectOutputStream    out;
    private String                username;    // set after successful login
    private volatile boolean      running = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Output stream must be created BEFORE the input stream to avoid deadlock
            out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.flush();
            in  = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            LOG.info("[Handler] New connection from " + socket.getRemoteSocketAddress());

            while (running) {
                Message msg = (Message) in.readObject();
                if (msg == null) break;
                dispatch(msg);
            }
        } catch (EOFException | java.net.SocketException e) {
            // Client disconnected cleanly
        } catch (Exception e) {
            LOG.warning("[Handler] Error for " + username + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ── Message dispatch ─────────────────────────────────────────────────────

    private void dispatch(Message msg) {
        switch (msg.getType()) {
            case AUTH_LOGIN    -> handleLogin(msg);
            case AUTH_REGISTER -> handleRegister(msg);
            case AUTH_LOGOUT   -> handleLogout();
            case PRIVATE       -> handlePrivate(msg);
            case GROUP         -> handleGroup(msg);
            case BROADCAST     -> handleBroadcast(msg);
            case CREATE_GROUP  -> handleCreateGroup(msg);
            case JOIN_GROUP    -> handleJoinGroup(msg);
            case LEAVE_GROUP   -> handleLeaveGroup(msg);
            case FILE_OFFER    -> handleFileOffer(msg);
            case HEARTBEAT     -> { /* no-op */ }
            default            -> LOG.warning("[Handler] Unhandled message type: " + msg.getType());
        }
    }

    // ── Auth handlers ────────────────────────────────────────────────────────

    private void handleLogin(Message msg) {
        // payload: "username||plainPassword" (encrypted with AES)
        try {
            String plain = EncryptionUtil.decrypt(msg.getEncryptedContent());
            String[] parts = plain.split("\\|\\|", 2);
            if (parts.length < 2) { sendAuthResponse(Protocol.AUTH_FAIL); return; }

            String uname = parts[0];
            String pass  = parts[1];

            if (server.getAuthManager().isOnline(uname)) {
                sendAuthResponse(Protocol.ALREADY_ONLINE);
                return;
            }

            User user = server.getAuthManager().login(uname, pass, server.getConfig().getServerId());
            if (user == null) {
                sendAuthResponse(Protocol.AUTH_FAIL);
                return;
            }

            this.username = uname;
            server.registerClient(uname, this);
            sendAuthResponse(Protocol.AUTH_OK);
            server.broadcastUserList();
            LOG.info("[Handler] Authenticated: " + uname);

        } catch (Exception e) {
            LOG.severe("[Handler] Login error: " + e.getMessage());
            sendAuthResponse(Protocol.AUTH_FAIL);
        }
    }

    private void handleRegister(Message msg) {
        // payload: "username||password||displayName||email"
        try {
            String plain = EncryptionUtil.decrypt(msg.getEncryptedContent());
            String[] p = plain.split("\\|\\|", 4);
            if (p.length < 4) { sendAuthResponse(Protocol.REGISTER_FAIL); return; }

            boolean ok = server.getAuthManager().register(p[0], p[1], p[2], p[3]);
            sendAuthResponse(ok ? Protocol.REGISTER_OK : Protocol.REGISTER_FAIL);
        } catch (Exception e) {
            LOG.severe("[Handler] Register error: " + e.getMessage());
            sendAuthResponse(Protocol.REGISTER_FAIL);
        }
    }

    private void handleLogout() {
        if (username != null) {
            server.getAuthManager().logout(username);
            server.unregisterClient(username);
            server.broadcastUserList();
        }
        running = false;
    }

    // ── Messaging handlers ───────────────────────────────────────────────────

    private void handlePrivate(Message msg) {
        requireAuth();
        server.getHistoryManager().record(msg);
        ClientHandler recipient = server.getClient(msg.getRecipientUsername());
        if (recipient != null) {
            recipient.send(msg);
        } else {
            // User offline — store for later (simple: just log it)
            LOG.info("[Handler] Recipient offline, message dropped for now: " + msg.getRecipientUsername());
            sendSystem("User " + msg.getRecipientUsername() + " is currently offline.");
        }
    }

    private void handleGroup(Message msg) {
        requireAuth();
        String gname = msg.getGroupName();
        Group group = server.getGroupManager().getGroup(gname);
        if (group == null) { sendSystem("Group '" + gname + "' does not exist."); return; }
        if (!group.isMember(username)) { sendSystem("You are not a member of '" + gname + "'."); return; }

        server.getHistoryManager().record(msg);

        // Fan out to all online group members except the sender
        Set<String> members = group.getMembers();
        for (String member : members) {
            if (!member.equals(username)) {
                ClientHandler h = server.getClient(member);
                if (h != null) h.send(msg);
            }
        }
    }

    private void handleBroadcast(Message msg) {
        requireAuth();
        server.getHistoryManager().record(msg);
        server.broadcast(msg, username);
    }

    // ── Group management handlers ────────────────────────────────────────────

    private void handleCreateGroup(Message msg) {
        requireAuth();
        // payload: "groupName||description"
        try {
            String plain = EncryptionUtil.decrypt(msg.getEncryptedContent());
            String[] p = plain.split("\\|\\|", 2);
            String gname = p[0];
            String desc  = p.length > 1 ? p[1] : "";
            boolean ok = server.getGroupManager().createGroup(gname, desc, username);
            sendSystem(ok ? Protocol.GROUP_CREATED + "||" + gname : Protocol.GROUP_ERROR + "||Name taken");
            if (ok) server.broadcastGroupList();
        } catch (Exception e) {
            sendSystem(Protocol.GROUP_ERROR + "||" + e.getMessage());
        }
    }

    private void handleJoinGroup(Message msg) {
        requireAuth();
        try {
            String gname = EncryptionUtil.decrypt(msg.getEncryptedContent());
            boolean ok = server.getGroupManager().joinGroup(gname, username);
            sendSystem(ok ? Protocol.GROUP_JOINED + "||" + gname : Protocol.GROUP_ERROR + "||Not found");
            if (ok) server.broadcastGroupList();
        } catch (Exception e) {
            sendSystem(Protocol.GROUP_ERROR + "||" + e.getMessage());
        }
    }

    private void handleLeaveGroup(Message msg) {
        requireAuth();
        try {
            String gname = EncryptionUtil.decrypt(msg.getEncryptedContent());
            boolean ok = server.getGroupManager().leaveGroup(gname, username);
            sendSystem(ok ? Protocol.GROUP_LEFT + "||" + gname : Protocol.GROUP_ERROR + "||Not found");
            if (ok) server.broadcastGroupList();
        } catch (Exception e) {
            sendSystem(Protocol.GROUP_ERROR + "||" + e.getMessage());
        }
    }

    // ── File transfer handler ────────────────────────────────────────────────

    private void handleFileOffer(Message msg) {
        requireAuth();
        // Forward the file offer to the recipient so they can accept/reject
        ClientHandler recipient = server.getClient(msg.getRecipientUsername());
        if (recipient == null) {
            sendSystem("User " + msg.getRecipientUsername() + " is offline.");
        } else {
            recipient.send(msg);
        }
    }

    // ── Send helpers ─────────────────────────────────────────────────────────

    /** Send a Message object to this client's socket. Thread-safe. */
    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();  // avoid stale object cache
        } catch (IOException e) {
            LOG.warning("[Handler] Send failed to " + username + ": " + e.getMessage());
            running = false;
        }
    }

    public void sendSystem(String content) {
        send(Message.systemMessage(content));
    }

    private void sendAuthResponse(String token) {
        Message resp = new Message(Message.Type.AUTH_RESPONSE, "SERVER", token);
        resp.setPlainContent(token);
        send(resp);
    }

    private void requireAuth() {
        if (username == null) throw new IllegalStateException("Not authenticated");
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private void cleanup() {
        running = false;
        if (username != null) {
            server.getAuthManager().logout(username);
            server.unregisterClient(username);
            server.broadcastUserList();
            LOG.info("[Handler] Cleaned up session for " + username);
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String getUsername() { return username; }

    @Override
    public String toString() {
        return "ClientHandler{username='" + username + "', remote=" + socket.getRemoteSocketAddress() + "}";
    }
}
