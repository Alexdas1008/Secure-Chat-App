package common;

/**
 * Shared constants used by both client and server for the
 * wire protocol and default configuration.
 */
public final class Protocol {

    private Protocol() {}   // utility class – no instances

    // ── Default network settings ─────────────────────────────────────────────
    public static final int    DEFAULT_PORT           = 9000;
    public static final int    FILE_TRANSFER_BASE_PORT = 9100;
    public static final String DEFAULT_HOST           = "localhost";

    // ── Timing ───────────────────────────────────────────────────────────────
    public static final int SOCKET_TIMEOUT_MS    = 30_000;  // 30 s read timeout
    public static final int HEARTBEAT_INTERVAL_MS = 10_000; // 10 s heartbeat

    // ── Auth tokens returned inside Message.plainContent ────────────────────
    public static final String AUTH_OK          = "AUTH_OK";
    public static final String AUTH_FAIL        = "AUTH_FAIL";
    public static final String REGISTER_OK      = "REGISTER_OK";
    public static final String REGISTER_FAIL    = "REGISTER_FAIL";
    public static final String ALREADY_ONLINE   = "ALREADY_ONLINE";

    // ── Group operation tokens ───────────────────────────────────────────────
    public static final String GROUP_CREATED    = "GROUP_CREATED";
    public static final String GROUP_JOINED     = "GROUP_JOINED";
    public static final String GROUP_LEFT       = "GROUP_LEFT";
    public static final String GROUP_ERROR      = "GROUP_ERROR";

    // ── Data directory paths (relative to jar) ───────────────────────────────
    public static final String DATA_DIR         = "data/";
    public static final String USERS_FILE       = DATA_DIR + "users/users.dat";
    public static final String GROUPS_FILE      = DATA_DIR + "groups/groups.dat";
    public static final String HISTORY_DIR      = DATA_DIR + "history/";
    public static final String FILES_DIR        = DATA_DIR + "files/";
    public static final String CONFIG_FILE      = "data/config/server.properties";
    public static final String LOG_FILE         = "logs/server.log";

    // ── Separator used inside delimited plain-text payloads ─────────────────
    public static final String SEP              = "||";
}
