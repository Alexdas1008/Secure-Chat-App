package gui;

import client.ChatClient;
import common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Registration window — creates a new user account on the server.
 */
public class RegistrationWindow extends JFrame {

    private final String serverHost;
    private final int    serverPort;

    private JTextField     usernameField;
    private JTextField     displayNameField;
    private JTextField     emailField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JButton        registerButton;
    private JLabel         statusLabel;

    public RegistrationWindow(String serverHost, int serverPort) {
        super("SecureChat — Register");
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        buildUI();
        setSize(440, 460);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(30, 33, 40));
        root.setBorder(new EmptyBorder(28, 40, 28, 40));
        setContentPane(root);

        JLabel title = new JLabel("Create Account", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(100, 200, 255));
        title.setBorder(new EmptyBorder(0, 0, 18, 0));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(30, 33, 40));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 0, 5, 0);
        g.weightx = 1.0;

        String[][] fields = {
            {"Username *",     "username"},
            {"Display Name *", "displayName"},
            {"Email",          "email"},
            {"Password *",     "password"},
            {"Confirm Password *", "confirm"},
        };

        for (int i = 0; i < fields.length; i++) {
            g.gridx = 0; g.gridy = i * 2;
            form.add(lbl(fields[i][0]), g);
            g.gridy = i * 2 + 1;
            switch (fields[i][1]) {
                case "username"    -> { usernameField    = txt(); form.add(usernameField, g); }
                case "displayName" -> { displayNameField = txt(); form.add(displayNameField, g); }
                case "email"       -> { emailField       = txt(); form.add(emailField, g); }
                case "password"    -> { passwordField    = pwd(); form.add(passwordField, g); }
                case "confirm"     -> { confirmField     = pwd(); form.add(confirmField, g); }
            }
        }
        root.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(30, 33, 40));
        south.setBorder(new EmptyBorder(16, 0, 0, 0));

        registerButton = new JButton("Register");
        registerButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registerButton.setBackground(new Color(50, 160, 90));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);
        registerButton.setBorderPainted(false);
        registerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerButton.setBorder(new EmptyBorder(10, 0, 10, 0));
        registerButton.addActionListener(e -> doRegister());

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        statusLabel.setForeground(new Color(255, 100, 100));
        statusLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

        south.add(registerButton, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
    }

    private void doRegister() {
        String username    = usernameField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String email       = emailField.getText().trim();
        String password    = new String(passwordField.getPassword());
        String confirm     = new String(confirmField.getPassword());

        if (username.isEmpty() || displayName.isEmpty() || password.isEmpty()) {
            setStatus("Required fields missing.", Color.RED); return;
        }
        if (!password.equals(confirm)) {
            setStatus("Passwords do not match.", Color.RED); return;
        }
        if (password.length() < 6) {
            setStatus("Password must be at least 6 characters.", Color.RED); return;
        }

        registerButton.setEnabled(false);
        setStatus("Connecting…", new Color(100, 200, 255));

        final String uname = username, dname = displayName, em = email, pw = password;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                ChatClient client = new ChatClient(serverHost, serverPort);
                client.connect();

                final String[] result = { null };
                final Object   lock   = new Object();

                client.setMessageHandler(msg -> {
                    if (msg.getType() == models.Message.Type.AUTH_RESPONSE) {
                        synchronized (lock) { result[0] = msg.getPlainContent(); lock.notifyAll(); }
                    }
                });

                client.sendRegister(uname, pw, dname, em);
                synchronized (lock) { lock.wait(5_000); }
                client.disconnect();
                return result[0];
            }

            @Override protected void done() {
                registerButton.setEnabled(true);
                try {
                    String resp = get();
                    if (Protocol.REGISTER_OK.equals(resp)) {
                        setStatus("Account created! You can now log in.", new Color(80, 200, 80));
                        Timer t = new Timer(2000, ev -> dispose());
                        t.setRepeats(false);
                        t.start();
                    } else {
                        setStatus("Registration failed — username may be taken.", Color.RED);
                    }
                } catch (Exception e) {
                    setStatus("Error: " + e.getMessage(), Color.RED);
                }
            }
        }.execute();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(new Color(180, 185, 200));
        return l;
    }

    private JTextField txt() {
        JTextField f = new JTextField();
        styleField(f); return f;
    }

    private JPasswordField pwd() {
        JPasswordField f = new JPasswordField();
        styleField(f); return f;
    }

    private void styleField(JTextField f) {
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setBackground(new Color(45, 48, 58));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 75, 90)),
                new EmptyBorder(6, 10, 6, 10)));
    }

    private void setStatus(String msg, Color c) {
        statusLabel.setText(msg);
        statusLabel.setForeground(c);
    }
}
