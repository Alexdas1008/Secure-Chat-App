package gui;

import client.ChatClient;
import common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Login window — the first screen the user sees.
 * Connects to the server and sends encrypted credentials.
 */
public class LoginWindow extends JFrame {

    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JTextField     hostField;
    private JTextField     portField;
    private JButton        loginButton;
    private JButton        registerButton;
    private JLabel         statusLabel;

    private ChatClient client;

    public LoginWindow() {
        super("Encrypted Chat — Login");
        buildUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(440, 360);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        // Dark background panel
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(30, 33, 40));
        root.setBorder(new EmptyBorder(30, 40, 30, 40));
        setContentPane(root);

        // ── Title ────────────────────────────────────────────────────────────
        JLabel title = new JLabel("🔐 SecureChat", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(100, 200, 255));
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        root.add(title, BorderLayout.NORTH);

        // ── Form panel ───────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(30, 33, 40));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.insets  = new Insets(6, 0, 6, 0);
        gbc.weightx = 1.0;

        // Server host / port
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(makeLabel("Server Host"), gbc);
        gbc.gridy = 1;
        hostField = makeTextField(Protocol.DEFAULT_HOST);
        form.add(hostField, gbc);

        gbc.gridy = 2;
        form.add(makeLabel("Server Port"), gbc);
        gbc.gridy = 3;
        portField = makeTextField(String.valueOf(Protocol.DEFAULT_PORT));
        form.add(portField, gbc);

        gbc.gridy = 4;
        form.add(makeLabel("Username"), gbc);
        gbc.gridy = 5;
        usernameField = makeTextField("");
        usernameField.setToolTipText("Your registered username");
        form.add(usernameField, gbc);

        gbc.gridy = 6;
        form.add(makeLabel("Password"), gbc);
        gbc.gridy = 7;
        passwordField = new JPasswordField();
        styleField(passwordField);
        form.add(passwordField, gbc);

        root.add(form, BorderLayout.CENTER);

        // ── Button panel ─────────────────────────────────────────────────────
        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setBackground(new Color(30, 33, 40));
        buttons.setBorder(new EmptyBorder(20, 0, 0, 0));

        loginButton = makeButton("Login", new Color(0, 120, 215));
        loginButton.addActionListener(e -> doLogin());

        registerButton = makeButton("Register", new Color(50, 160, 90));
        registerButton.addActionListener(e -> openRegister());

        buttons.add(loginButton);
        buttons.add(registerButton);

        // Status label
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        statusLabel.setForeground(new Color(255, 100, 100));

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(30, 33, 40));
        south.add(buttons, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        // Allow Enter key to trigger login
        passwordField.addActionListener(e -> doLogin());
    }

    // ── Action handlers ──────────────────────────────────────────────────────

    private void doLogin() {
        String host     = hostField.getText().trim();
        int    port;
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus("Invalid port number.", Color.RED);
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Username and password required.", Color.RED);
            return;
        }

        setStatus("Connecting…", new Color(100, 200, 255));
        loginButton.setEnabled(false);

        // Run network operations off the EDT
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception {
                client = new ChatClient(host, port);
                client.connect();

                // We wait for AUTH_RESPONSE via a one-shot latch
                final String[] result = { null };
                final Object   lock   = new Object();

                client.setMessageHandler(msg -> {
                    if (msg.getType() == models.Message.Type.AUTH_RESPONSE) {
                        synchronized (lock) {
                            result[0] = msg.getPlainContent();
                            lock.notifyAll();
                        }
                    }
                });

                client.sendLogin(username, password);

                synchronized (lock) {
                    lock.wait(5_000);  // wait up to 5 s for the server response
                }
                return result[0];
            }

            @Override protected void done() {
                loginButton.setEnabled(true);
                try {
                    String resp = get();
                    if (Protocol.AUTH_OK.equals(resp)) {
                        setStatus("Authenticated!", new Color(80, 200, 80));
                        launchDashboard(username);
                    } else if (Protocol.ALREADY_ONLINE.equals(resp)) {
                        setStatus("User already logged in from another session.", Color.RED);
                        client.disconnect();
                    } else {
                        setStatus("Login failed — check credentials.", Color.RED);
                        if (client != null) client.disconnect();
                    }
                } catch (Exception e) {
                    setStatus("Connection error: " + e.getMessage(), Color.RED);
                    if (client != null) client.disconnect();
                }
            }
        };
        worker.execute();
    }

    private void openRegister() {
        String host = hostField.getText().trim();
        int    port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException e) { port = Protocol.DEFAULT_PORT; }

        new RegistrationWindow(host, port).setVisible(true);
    }

    private void launchDashboard(String username) {
        dispose();
        ChatDashboard dashboard = new ChatDashboard(client, username);
        dashboard.setVisible(true);
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(180, 185, 200));
        return lbl;
    }

    private JTextField makeTextField(String defaultText) {
        JTextField field = new JTextField(defaultText);
        styleField(field);
        return field;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBackground(new Color(45, 48, 58));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 75, 90)),
                new EmptyBorder(6, 10, 6, 10)));
    }

    private JButton makeButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        return btn;
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }
}
