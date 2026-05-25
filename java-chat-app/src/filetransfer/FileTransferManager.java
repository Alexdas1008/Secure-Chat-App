package filetransfer;

import common.Protocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Handles peer-to-peer file transfers using a separate TCP connection.
 *
 * <p><b>Send side:</b> opens an ephemeral server socket, notifies the
 * recipient of the port via a {@code FILE_OFFER} message (handled by
 * {@link client.ChatClient}), then waits for the recipient to connect and
 * streams the file.</p>
 *
 * <p><b>Receive side:</b> connects to the sender's ephemeral port and
 * reads the incoming bytes, saving them to the files directory.</p>
 *
 * <p>Both operations run on daemon threads so the GUI stays responsive.</p>
 */
public class FileTransferManager {

    private static final Logger LOG = Logger.getLogger(FileTransferManager.class.getName());
    private static final int BUFFER_SIZE = 64 * 1024;  // 64 KiB

    /**
     * Start sending a file.  Opens an ephemeral server socket and returns
     * its port number immediately.  The actual transfer happens asynchronously
     * once a receiver connects.
     *
     * @param file             the file to send
     * @param progressCallback receives values 0–100 during transfer (may be null)
     * @return the ephemeral local port the receiver should connect to
     */
    public static int startSend(File file, Consumer<Integer> progressCallback) throws IOException {
        ServerSocket ss = new ServerSocket(0);  // OS assigns a free port
        int port = ss.getLocalPort();

        Thread sender = new Thread(() -> {
            try (ServerSocket srvSock = ss) {
                srvSock.setSoTimeout(30_000);   // 30 s for recipient to connect
                LOG.info("[FTM] Waiting for receiver on port " + port + " for file: " + file.getName());
                Socket conn = srvSock.accept();
                doSend(file, conn, progressCallback);
            } catch (Exception e) {
                LOG.warning("[FTM] Send error: " + e.getMessage());
            }
        }, "FileTransfer-Send-" + file.getName());
        sender.setDaemon(true);
        sender.start();

        return port;
    }

    /**
     * Connect to the sender and receive a file, saving it to the files directory.
     *
     * @param senderHost       host of the sender (usually the server acts as relay,
     *                         but in direct mode it's the sender's host)
     * @param senderPort       ephemeral port from the FILE_OFFER message
     * @param fileName         name to save the file as
     * @param fileSize         expected number of bytes (used for progress)
     * @param progressCallback receives values 0–100 during transfer (may be null)
     */
    public static void startReceive(String senderHost, int senderPort,
                                    String fileName, long fileSize,
                                    Consumer<Integer> progressCallback) {
        Thread receiver = new Thread(() -> {
            try (Socket conn = new Socket(senderHost, senderPort)) {
                doReceive(conn, fileName, fileSize, progressCallback);
            } catch (Exception e) {
                LOG.warning("[FTM] Receive error: " + e.getMessage());
            }
        }, "FileTransfer-Recv-" + fileName);
        receiver.setDaemon(true);
        receiver.start();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static void doSend(File file, Socket conn, Consumer<Integer> progress) {
        long total = file.length();
        long sent  = 0;
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()));
             FileInputStream fis  = new FileInputStream(file)) {

            // Send file size header
            dos.writeLong(total);
            dos.flush();

            byte[] buf = new byte[BUFFER_SIZE];
            int    read;
            while ((read = fis.read(buf)) != -1) {
                dos.write(buf, 0, read);
                sent += read;
                if (progress != null && total > 0) {
                    progress.accept((int) (sent * 100 / total));
                }
            }
            dos.flush();
            LOG.info("[FTM] Sent " + sent + " bytes for file: " + file.getName());
        } catch (IOException e) {
            LOG.warning("[FTM] doSend error: " + e.getMessage());
        }
    }

    private static void doReceive(Socket conn, String fileName, long expectedSize,
                                  Consumer<Integer> progress) {
        File dir = new File(Protocol.FILES_DIR);
        dir.mkdirs();
        File outFile = resolveUnique(dir, fileName);

        try (DataInputStream dis   = new DataInputStream(new BufferedInputStream(conn.getInputStream()));
             FileOutputStream fos  = new FileOutputStream(outFile)) {

            long total    = dis.readLong();   // server sends real size in header
            long received = 0;
            byte[] buf    = new byte[BUFFER_SIZE];
            int    read;

            while (received < total && (read = dis.read(buf, 0, (int) Math.min(BUFFER_SIZE, total - received))) != -1) {
                fos.write(buf, 0, read);
                received += read;
                if (progress != null && total > 0) {
                    progress.accept((int) (received * 100 / total));
                }
            }
            fos.flush();
            LOG.info("[FTM] Received " + received + " bytes → " + outFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.warning("[FTM] doReceive error: " + e.getMessage());
        }
    }

    /** If a file with the same name already exists, append (1), (2), … */
    private static File resolveUnique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String ext  = dot >= 0 ? name.substring(dot)    : "";
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(dir, base + "(" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, name + "." + System.currentTimeMillis());
    }
}
