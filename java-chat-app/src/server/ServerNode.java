package server;

import models.Message;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Represents a peer server node in the distributed architecture.
 *
 * <p>Each {@code ServerNode} maintains a persistent socket connection to a
 * sibling server.  Messages are forwarded over this connection so that clients
 * connected to different nodes can communicate transparently.</p>
 *
 * <p>A background reader thread listens for forwarded messages coming IN from
 * the peer and injects them into this server's routing logic.</p>
 */
public class ServerNode {

    private static final Logger LOG = Logger.getLogger(ServerNode.class.getName());

    private final String ownServerId;
    private final String peerHost;
    private final int    peerPort;

    private Socket           socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private volatile boolean   connected = false;

    public ServerNode(String ownServerId, String peerHost, int peerPort) {
        this.ownServerId = ownServerId;
        this.peerHost    = peerHost;
        this.peerPort    = peerPort;
    }

    // ── Connection ────────────────────────────────────────────────────────────

    public void connect() throws IOException {
        socket = new Socket(peerHost, peerPort);
        out    = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.flush();
        in     = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        connected = true;
        LOG.info("[ServerNode] Connected to peer " + peerHost + ":" + peerPort);

        // Background reader for inbound forwarded messages
        Thread reader = new Thread(this::readLoop, "ServerNode-Reader-" + peerHost);
        reader.setDaemon(true);
        reader.start();
    }

    // ── Outbound forwarding ───────────────────────────────────────────────────

    /**
     * Forwards a {@link Message} to the peer node.
     * Returns {@code false} if the connection is unavailable.
     */
    public synchronized boolean forward(Message msg) {
        if (!connected) return false;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
            return true;
        } catch (IOException e) {
            LOG.warning("[ServerNode] Forward failed to " + peerHost + ": " + e.getMessage());
            connected = false;
            return false;
        }
    }

    // ── Inbound reading ───────────────────────────────────────────────────────

    private void readLoop() {
        while (connected) {
            try {
                Message msg = (Message) in.readObject();
                if (msg == null) break;
                // In a full implementation, inject into this server's router here.
                // For now we log receipt — extending this is straightforward.
                LOG.info("[ServerNode] Received forwarded message from peer: " + msg.getType()
                        + " by " + msg.getSenderUsername());
            } catch (Exception e) {
                if (connected) LOG.warning("[ServerNode] Read error from peer: " + e.getMessage());
                break;
            }
        }
        connected = false;
        LOG.info("[ServerNode] Disconnected from peer " + peerHost + ":" + peerPort);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public boolean isConnected() { return connected; }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public String toString() {
        return "ServerNode{peer=" + peerHost + ":" + peerPort + ", connected=" + connected + "}";
    }
}
