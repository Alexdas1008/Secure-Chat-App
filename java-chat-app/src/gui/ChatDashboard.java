package gui;

import client.ChatClient;
import common.Protocol;
import filetransfer.FileTransferManager;
import models.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main chat dashboard — shown after a successful login.
 *
 * <p>Layout (BorderLayout):</p>
 * <pre>
 *  ┌─────────────────────────────────────────────┐
 *  │  TOP BAR  (username, status, logout)        │
 *  ├──────────┬──────────────────────┬───────────┤
 *  │  LEFT    │     CHAT AREA        │  RIGHT    │
 *  │  panel   │  (tabbed: global /   │  Users    │
 *  │  Groups  │   private / group)   │  list     │
 *  ├──────────┴──────────────────────┴───────────┤
 *  │  INPUT ROW  (text field + send + file btn)  │
 *  └─────────────────────────────────────────────┘
 * </pre>
 */
public class ChatDashboard extends JFrame {

    // ── Colours ──────────────────────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(28, 31, 38);
    private static final Color BG_MID    = new Color(38, 42, 52);
    private static final Color BG_LIGHT  = new Color(50, 55, 68);
    private static final Color ACCENT    = new Color(88, 166, 255);
    private static final Color TEXT_MAIN = new Color(220, 225, 235);
    private static final Color TEXT_DIM  = new Color(130, 140, 160);
    private static final Color MSG_SELF  = new Color(50, 120, 80);
    private static final Color MSG_OTHER = new Color(50, 55, 75);

    // ── State ─────────────────────────────────────────────────────────────────
    private final ChatClient client;
    private final String     username;

    /** The current recipient for private chat (null = global/broadcast) */
    private String currentPrivateTarget = null;
    /** The current group chat name (null = not in a group chat) */
    private String currentGroupTarget   = null;

    // ── UI components ─────────────────────────────────────────────────────────
    private JTextPane  chatPane;
    private StyledDocument chatDoc;
    private JTextField inputField;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JList<String> groupList;
    private DefaultListModel<String> groupListModel;
    private JLabel statusLabel;
    private JLabel chatTitleLabel;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Heartbeat");
                t.setDaemon(true);
                return t;
            });

    public ChatDashboard(ChatClient client, String username) {
        super("SecureChat — " + username);
        this.client   = client;
        this.username = username;
        buildUI();
        wireMessages();
        startHeartbeat();
        appendSystemLine("Connected as " + username + ". All messages are AES-256 encrypted.");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doLogout(); }
        });
        setSize(980, 680);
        setLocationRelativeTo(null);
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        setContentPane(root);

        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildLeftPanel(), BorderLayout.WEST);
        root.add(buildChatArea(),  BorderLayout.CENTER);
        root.add(buildRightPanel(),BorderLayout.EAST);
        root.add(buildInputRow(),  BorderLayout.SOUTH);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_MID);
        bar.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel appLabel = new JLabel("🔐 SecureChat");
        appLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        appLabel.setForeground(ACCENT);
        bar.add(appLabel, BorderLayout.WEST);

        chatTitleLabel = new JLabel("Global Broadcast", SwingConstants.CENTER);
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        chatTitleLabel.setForeground(TEXT_MAIN);
        bar.add(chatTitleLabel, BorderLayout.CENTER);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightTop.setBackground(BG_MID);

        statusLabel = new JLabel("● Online");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(80, 200, 120));

        JLabel userLbl = new JLabel(username);
        userLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userLbl.setForeground(TEXT_MAIN);

        JButton logoutBtn = styledBtn("Logout", new Color(180, 60, 60));
        logoutBtn.addActionListener(e -> doLogout());

        rightTop.add(statusLabel);
        rightTop.add(userLbl);
        rightTop.add(logoutBtn);
        bar.add(rightTop, BorderLayout.EAST);
        return bar;
    }

    // ── Left panel — Groups ───────────────────────────────────────────────────

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_MID);
        panel.setPreferredSize(new Dimension(180, 0));
        panel.setBorder(new EmptyBorder(8, 6, 8, 6));

        JLabel lbl = new JLabel("Groups", SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(ACCENT);
        lbl.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(lbl, BorderLayout.NORTH);

        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);
        groupList.setBackground(BG_MID);
        groupList.setForeground(TEXT_MAIN);
        groupList.setSelectionBackground(BG_LIGHT);
        groupList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        groupList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = groupList.getSelectedValue();
                    if (sel != null) switchToGroup(sel.split(":")[0].trim());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(groupList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG_MID);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(2, 1, 4, 4));
        btns.setBackground(BG_MID);
        btns.setBorder(new EmptyBorder(8, 0, 0, 0));

        JButton createBtn = styledBtn("+ Create Group", new Color(40, 110, 60));
        createBtn.addActionListener(e -> showCreateGroupDialog());
        JButton joinBtn = styledBtn("Join Group", new Color(50, 80, 140));
        joinBtn.addActionListener(e -> showJoinGroupDialog());

        btns.add(createBtn);
        btns.add(joinBtn);
        panel.add(btns, BorderLayout.SOUTH);
        return panel;
    }

    // ── Chat area ─────────────────────────────────────────────────────────────

    private JPanel buildChatArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(BG_DARK);
        chatDoc = chatPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ── Right panel — Online Users ────────────────────────────────────────────

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_MID);
        panel.setPreferredSize(new Dimension(170, 0));
        panel.setBorder(new EmptyBorder(8, 6, 8, 6));

        JLabel lbl = new JLabel("Online Users", SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(ACCENT);
        lbl.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(lbl, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(BG_MID);
        userList.setForeground(TEXT_MAIN);
        userList.setSelectionBackground(BG_LIGHT);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = userList.getSelectedValue();
                    if (sel != null && !sel.equals(username)) switchToPrivate(sel);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG_MID);
        panel.add(scroll, BorderLayout.CENTER);

        JButton broadcastBtn = styledBtn("Broadcast", new Color(120, 70, 160));
        broadcastBtn.addActionListener(e -> switchToGlobal());
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG_MID);
        south.setBorder(new EmptyBorder(8, 0, 0, 0));
        south.add(broadcastBtn, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    // ── Input row ─────────────────────────────────────────────────────────────

    private JPanel buildInputRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BG_MID);
        row.setBorder(new EmptyBorder(10, 12, 12, 12));

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBackground(BG_LIGHT);
        inputField.setForeground(TEXT_MAIN);
        inputField.setCaretColor(TEXT_MAIN);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 75, 95)),
                new EmptyBorder(8, 12, 8, 12)));
        inputField.addActionListener(e -> sendMessage());

        JButton sendBtn = styledBtn("Send", ACCENT);
        sendBtn.setForeground(BG_DARK);
        sendBtn.addActionListener(e -> sendMessage());

        JButton fileBtn = styledBtn("📎 File", new Color(70, 80, 100));
        fileBtn.addActionListener(e -> sendFile());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setBackground(BG_MID);
        btnPanel.add(fileBtn);
        btnPanel.add(sendBtn);

        row.add(inputField, BorderLayout.CENTER);
        row.add(btnPanel, BorderLayout.EAST);
        return row;
    }

    // ── Message wiring ────────────────────────────────────────────────────────

    private void wireMessages() {
        client.setMessageHandler(msg -> SwingUtilities.invokeLater(() -> handleIncoming(msg)));
    }

    private void handleIncoming(Message msg) {
        switch (msg.getType()) {
            case PRIVATE -> appendChatLine(
                    msg.getSenderUsername(),
                    msg.getPlainContent() != null ? msg.getPlainContent() : "<encrypted>",
                    false);
            case GROUP -> appendChatLine(
                    "[#" + msg.getGroupName() + "] " + msg.getSenderUsername(),
                    msg.getPlainContent() != null ? msg.getPlainContent() : "<encrypted>",
                    false);
            case BROADCAST -> appendChatLine(
                    "[All] " + msg.getSenderUsername(),
                    msg.getPlainContent() != null ? msg.getPlainContent() : "<encrypted>",
                    false);
            case SYSTEM -> appendSystemLine(msg.getPlainContent());
            case AUTH_RESPONSE -> { /* handled in LoginWindow */ }
            case USER_LIST -> updateUserList(msg.getPlainContent());
            case GROUP_LIST -> updateGroupList(msg.getPlainContent());
            case FILE_OFFER -> handleFileOffer(msg);
            default -> { /* ignore */ }
        }
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || !client.isConnected()) return;
        inputField.setText("");

        try {
            if (currentGroupTarget != null) {
                client.sendGroupMessage(username, currentGroupTarget, text);
                appendChatLine("You", text, true);
            } else if (currentPrivateTarget != null) {
                client.sendPrivateMessage(username, currentPrivateTarget, text);
                appendChatLine("You → " + currentPrivateTarget, text, true);
            } else {
                client.sendBroadcast(username, text);
                appendChatLine("You [All]", text, true);
            }
        } catch (Exception e) {
            appendSystemLine("Send error: " + e.getMessage());
        }
    }

    private void sendFile() {
        if (currentPrivateTarget == null) {
            JOptionPane.showMessageDialog(this,
                    "Select a user from the Online Users list first for file transfer.",
                    "File Transfer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select file to send to " + currentPrivateTarget);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        appendSystemLine("Sending file '" + file.getName() + "' to " + currentPrivateTarget + "…");

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                int port = FileTransferManager.startSend(file, this::publish);
                client.sendFileOffer(username, currentPrivateTarget,
                        file.getName(), file.length(), port);
                return null;
            }
            @Override protected void process(List<Integer> chunks) {
                int pct = chunks.get(chunks.size() - 1);
                statusLabel.setText("Sending: " + pct + "%");
            }
            @Override protected void done() {
                statusLabel.setText("● Online");
                appendSystemLine("File sent: " + file.getName());
            }
        };
        worker.execute();
    }

    private void handleFileOffer(Message msg) {
        int option = JOptionPane.showConfirmDialog(this,
                msg.getSenderUsername() + " wants to send you:\n" +
                        msg.getFileName() + " (" + humanSize(msg.getFileSize()) + ")\n\nAccept?",
                "Incoming File", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            appendSystemLine("Receiving '" + msg.getFileName() + "'…");
            SwingWorker<Void, Integer> worker = new SwingWorker<>() {
                @Override protected Void doInBackground() {
                    FileTransferManager.startReceive(
                            client.getHost(), msg.getFileTransferPort(),
                            msg.getFileName(), msg.getFileSize(), this::publish);
                    return null;
                }
                @Override protected void process(List<Integer> chunks) {
                    int pct = chunks.get(chunks.size() - 1);
                    statusLabel.setText("Receiving: " + pct + "%");
                }
                @Override protected void done() {
                    statusLabel.setText("● Online");
                    appendSystemLine("File saved to data/files/" + msg.getFileName());
                }
            };
            worker.execute();
        }
    }

    // ── Chat navigation ───────────────────────────────────────────────────────

    private void switchToGlobal() {
        currentPrivateTarget = null;
        currentGroupTarget   = null;
        chatTitleLabel.setText("Global Broadcast");
        appendSystemLine("Switched to global broadcast.");
    }

    private void switchToPrivate(String target) {
        currentPrivateTarget = target;
        currentGroupTarget   = null;
        chatTitleLabel.setText("Private chat with " + target);
        appendSystemLine("Private chat with " + target + ". Only you two can read this.");
    }

    private void switchToGroup(String groupName) {
        currentGroupTarget   = groupName;
        currentPrivateTarget = null;
        chatTitleLabel.setText("Group: #" + groupName);
        appendSystemLine("Now chatting in group #" + groupName);
    }

    // ── Group dialogs ─────────────────────────────────────────────────────────

    private void showCreateGroupDialog() {
        JTextField nameFld = new JTextField();
        JTextField descFld = new JTextField();
        JPanel p = new JPanel(new GridLayout(4, 1, 4, 4));
        p.add(new JLabel("Group Name:")); p.add(nameFld);
        p.add(new JLabel("Description (optional):")); p.add(descFld);

        int res = JOptionPane.showConfirmDialog(this, p, "Create Group",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String name = nameFld.getText().trim();
        if (name.isEmpty()) return;
        try {
            client.sendCreateGroup(username, name, descFld.getText().trim());
        } catch (Exception e) {
            appendSystemLine("Error: " + e.getMessage());
        }
    }

    private void showJoinGroupDialog() {
        String name = JOptionPane.showInputDialog(this, "Enter group name to join:");
        if (name == null || name.isBlank()) return;
        try {
            client.sendJoinGroup(username, name.trim());
        } catch (Exception e) {
            appendSystemLine("Error: " + e.getMessage());
        }
    }

    // ── List updates ──────────────────────────────────────────────────────────

    private void updateUserList(String csv) {
        userListModel.clear();
        if (csv == null || csv.isBlank()) return;
        Arrays.stream(csv.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .forEach(userListModel::addElement);
    }

    private void updateGroupList(String csv) {
        groupListModel.clear();
        if (csv == null || csv.isBlank()) return;
        Arrays.stream(csv.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .forEach(groupListModel::addElement);
    }

    // ── Styled text helpers ───────────────────────────────────────────────────

    private void appendChatLine(String sender, String text, boolean self) {
        try {
            SimpleAttributeSet timeStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(timeStyle, TEXT_DIM);
            StyleConstants.setFontSize(timeStyle, 11);

            SimpleAttributeSet nameStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(nameStyle, self ? new Color(100, 220, 140) : ACCENT);
            StyleConstants.setBold(nameStyle, true);
            StyleConstants.setFontSize(nameStyle, 13);

            SimpleAttributeSet msgStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(msgStyle, TEXT_MAIN);
            StyleConstants.setFontSize(msgStyle, 13);

            String time = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            chatDoc.insertString(chatDoc.getLength(), "\n" + time + "  ", timeStyle);
            chatDoc.insertString(chatDoc.getLength(), sender + ": ", nameStyle);
            chatDoc.insertString(chatDoc.getLength(), text, msgStyle);

            scrollToBottom();
        } catch (BadLocationException e) { /* ignore */ }
    }

    private void appendSystemLine(String text) {
        try {
            SimpleAttributeSet s = new SimpleAttributeSet();
            StyleConstants.setForeground(s, new Color(180, 140, 60));
            StyleConstants.setItalic(s, true);
            StyleConstants.setFontSize(s, 12);
            chatDoc.insertString(chatDoc.getLength(), "\n[system] " + text, s);
            scrollToBottom();
        } catch (BadLocationException e) { /* ignore */ }
    }

    private void scrollToBottom() {
        chatPane.setCaretPosition(chatDoc.getLength());
    }

    // ── Logout / heartbeat ────────────────────────────────────────────────────

    private void doLogout() {
        heartbeatExecutor.shutdownNow();
        client.sendLogout(username);
        client.disconnect();
        dispose();
        new LoginWindow().setVisible(true);
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(
                () -> { if (client.isConnected()) client.sendHeartbeat(username); },
                Protocol.HEARTBEAT_INTERVAL_MS,
                Protocol.HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    // ── Button factory ────────────────────────────────────────────────────────

    private JButton styledBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 12, 6, 12));
        return btn;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
