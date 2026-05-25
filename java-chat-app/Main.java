import gui.LoginWindow;
import server.ChatServer;
import config.ServerConfig;
import common.Protocol;

import javax.swing.*;

/**
 * Application entry point.
 *
 * Usage:
 *   java Main              → launches the GUI client (default)
 *   java Main server       → starts the chat server with defaults
 *   java Main server 9001 SERVER-2 → start server on port 9001 with id SERVER-2
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equalsIgnoreCase("server")) {
            // ── Server mode ───────────────────────────────────────────────────
            ServerConfig cfg = new ServerConfig();
            cfg.load(Protocol.CONFIG_FILE);
            if (args.length >= 2) cfg.setPort(Integer.parseInt(args[1]));
            if (args.length >= 3) cfg.setServerId(args[2]);

            ChatServer server = new ChatServer(cfg);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "Shutdown-Hook"));
            server.start();
        } else {
            // ── Client GUI mode ───────────────────────────────────────────────
            // Use system Look & Feel for a native feel on each OS
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to default Swing L&F
            }

            // All Swing operations must happen on the EDT
            SwingUtilities.invokeLater(() -> {
                LoginWindow login = new LoginWindow();
                login.setVisible(true);
            });
        }
    }
}
