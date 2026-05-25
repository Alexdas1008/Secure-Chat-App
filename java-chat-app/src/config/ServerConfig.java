package config;

import common.Protocol;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads and exposes server configuration from a properties file.
 * Supports distributed multi-node configuration.
 */
public class ServerConfig {

    private final Properties props = new Properties();

    private String serverId;
    private String host;
    private int    port;
    private int    maxClients;
    private List<String> peerAddresses;   // host:port of other server nodes

    public ServerConfig() {
        this("SERVER-1", Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public ServerConfig(String serverId, String host, int port) {
        this.serverId     = serverId;
        this.host         = host;
        this.port         = port;
        this.maxClients   = 100;
        this.peerAddresses = new ArrayList<>();
    }

    /** Load configuration from file, falling back to defaults on any error. */
    public void load(String configFilePath) {
        File file = new File(configFilePath);
        if (!file.exists()) {
            System.out.println("[Config] Config file not found at " + configFilePath + " — using defaults.");
            save(configFilePath);   // write defaults so user can edit
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            serverId   = props.getProperty("server.id",        serverId);
            host       = props.getProperty("server.host",      host);
            port       = Integer.parseInt(props.getProperty("server.port", String.valueOf(port)));
            maxClients = Integer.parseInt(props.getProperty("server.maxClients", "100"));

            String peers = props.getProperty("server.peers", "");
            peerAddresses.clear();
            if (!peers.isBlank()) {
                for (String p : peers.split(",")) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) peerAddresses.add(trimmed);
                }
            }
            System.out.println("[Config] Loaded configuration for server " + serverId);
        } catch (Exception e) {
            System.err.println("[Config] Error reading config file: " + e.getMessage() + " — using defaults.");
        }
    }

    /** Write current settings to a properties file. */
    public void save(String configFilePath) {
        try {
            File file = new File(configFilePath);
            file.getParentFile().mkdirs();
            props.setProperty("server.id",         serverId);
            props.setProperty("server.host",        host);
            props.setProperty("server.port",        String.valueOf(port));
            props.setProperty("server.maxClients",  String.valueOf(maxClients));
            props.setProperty("server.peers",       String.join(",", peerAddresses));
            try (OutputStream out = new FileOutputStream(file)) {
                props.store(out, "Distributed Chat Server Configuration");
            }
        } catch (IOException e) {
            System.err.println("[Config] Could not save config: " + e.getMessage());
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getServerId()            { return serverId; }
    public String getHost()                { return host; }
    public int    getPort()                { return port; }
    public int    getMaxClients()          { return maxClients; }
    public List<String> getPeerAddresses() { return new ArrayList<>(peerAddresses); }

    public void setServerId(String id)     { this.serverId = id; }
    public void setHost(String h)          { this.host = h; }
    public void setPort(int p)             { this.port = p; }
    public void setMaxClients(int m)       { this.maxClients = m; }
    public void addPeer(String address)    { peerAddresses.add(address); }
}
