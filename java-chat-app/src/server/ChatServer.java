package server;

import common.Protocol;
import config.ServerConfig;
import encryption.EncryptionUtil;
import models.Group;
import models.Message;
import storage.FileStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Multi-threaded chat server.
 *
 * <p>Accepts unlimited client connections (up to maxClients), each serviced
 * on its own thread via a cached thread pool.  Maintains an in-memory
 * registry of connected {@link ClientHandler} instances for routing.</p>
 */
public class ChatServer {

    private static final Logger LOG = Logger.getLogger(ChatServer.class.getName());

    private final ServerConfig         config;
    private final AuthenticationManager authManager;
    private final GroupManager          groupManager;
    private final ChatHistoryManager    historyManager;

    /** username → handler for every currently connected & authenticated client */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    // ── Peer node references (distributed architecture) ──────────────────────
    private final List<ServerNode> peerNodes = new CopyOnWriteArrayList<>();

    public ChatServer(ServerConfig config) {
        this.config         = config;
        this.authManager    = new AuthenticationManager();
        this.groupManager   = new GroupManager();
        this.historyManager = new ChatHistoryManager();
        FileStorage.ensureDirectories();
        setupLogging();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort());
        running      = true;

        LOG.info("╔══════════════════════════════════════════════════╗");
        LOG.info("║  Distributed Encrypted Chat Server               ║");
        LOG.info("║  Server ID : " + padRight(config.getServerId(), 37) + "║");
        LOG.info("║  Listening : " + padRight(config.getHost() + ":" + config.getPort(), 37) + "║");
        LOG.info("╚══════════════════════════════════════════════════╝");

        // Connect to peer nodes in background
        for (String peer : config.getPeerAddresses()) {
            threadPool.submit(() -> connectToPeer(peer));
        }

        // Accept loop
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(Protocol.SOCKET_TIMEOUT_MS);

                if (clients.size() >= config.getMaxClients()) {
                    LOG.warning("[Server] Max clients reached — rejecting connection.");
                    clientSocket.close();
                    continue;
                }

                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.submit(handler);
                LOG.info("[Server] Accepted connection from " + clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running) LOG.warning("[Server] Accept error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        threadPool.shutdown();
        LOG.info("[Server] Server stopped.");
    }

    // ── Client registry ──────────────────────────────────────────────────────

    public void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        LOG.info("[Server] Registered client: " + username + " (total online: " + clients.size() + ")");
    }

    public void unregisterClient(String username) {
        clients.remove(username);
        LOG.info("[Server] Unregistered client: " + username + " (total online: " + clients.size() + ")");
    }

    public ClientHandler getClient(String username) {
        return clients.get(username);
    }

    public Set<String> getOnlineUsernames() {
        return new HashSet<>(clients.keySet());
    }

    // ── Broadcast helpers ────────────────────────────────────────────────────

    /**
     * Send a message to every connected authenticated client except the sender.
     */
    public void broadcast(Message msg, String excludeUsername) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeUsername)) {
                entry.getValue().send(msg);
            }
        }
        // Forward to peer nodes for distributed delivery
        for (ServerNode peer : peerNodes) {
            peer.forward(msg);
        }
    }

    /**
     * Push the current online-user list to every connected client.
     * The list is sent as a SYSTEM message with pipe-separated usernames.
     */
    public void broadcastUserList() {
        String userList = String.join(",", clients.keySet());
        Message listMsg = new Message(Message.Type.USER_LIST, "SERVER", userList);
        listMsg.setPlainContent(userList);
        for (ClientHandler h : clients.values()) {
            h.send(listMsg);
        }
    }

    /**
     * Push the current group list to every connected client.
     */
    public void broadcastGroupList() {
        Map<String, Group> groups = groupManager.getAllGroups();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue().getMemberCount());
        }
        String payload = sb.toString();
        Message listMsg = new Message(Message.Type.GROUP_LIST, "SERVER", payload);
        listMsg.setPlainContent(payload);
        for (ClientHandler h : clients.values()) {
            h.send(listMsg);
        }
    }

    // ── Distributed peer nodes ───────────────────────────────────────────────

    private void connectToPeer(String address) {
        try {
            String[] parts = address.split(":");
            String host = parts[0];
            int    port = Integer.parseInt(parts[1]);
            ServerNode node = new ServerNode(config.getServerId(), host, port);
            node.connect();
            peerNodes.add(node);
            LOG.info("[Server] Connected to peer node: " + address);
        } catch (Exception e) {
            LOG.warning("[Server] Could not connect to peer " + address + ": " + e.getMessage());
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public ServerConfig          getConfig()         { return config; }
    public AuthenticationManager getAuthManager()    { return authManager; }
    public GroupManager          getGroupManager()   { return groupManager; }
    public ChatHistoryManager    getHistoryManager() { return historyManager; }

    // ── Logging setup ────────────────────────────────────────────────────────

    private void setupLogging() {
        try {
            new java.io.File("logs").mkdirs();
            FileHandler fh = new FileHandler(Protocol.LOG_FILE, true);
            fh.setFormatter(new SimpleFormatter());
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fh);
            rootLogger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("[Server] Could not set up file logging: " + e.getMessage());
        }
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        ServerConfig cfg = new ServerConfig();
        cfg.load(Protocol.CONFIG_FILE);

        // Override from command-line: java -cp ... server.ChatServer 9001 SERVER-2
        if (args.length >= 1) cfg.setPort(Integer.parseInt(args[0]));
        if (args.length >= 2) cfg.setServerId(args[1]);

        ChatServer server = new ChatServer(cfg);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        try {
            server.start();
        } catch (IOException e) {
            LOG.severe("[Server] Fatal: " + e.getMessage());
            System.exit(1);
        }
    }
}
