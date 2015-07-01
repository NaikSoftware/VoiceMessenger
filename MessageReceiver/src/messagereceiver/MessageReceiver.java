package messagereceiver;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import static java.lang.System.out;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import ua.naiksoftware.utils.InetUtils;

/**
 *
 * @author Naik
 */
public class MessageReceiver {

    public static final String HOST_NAME = InetUtils.hostName();
    public static final String SERVICE_TYPE = "_messagesender._tcp.local.";
    public static final int PORT = 6432;
    public static final byte FLAG_REQUEST = 0x03;
    public static final byte FLAG_STOP_ALL = 0x05;
    private static ServerThread[] serverThreads;
    private static JmDNS[] jmdns;
    private static final Object lock = new Object();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            /* DIRECT START SERVER */
            startReceiving();
        } else {
            /* START GUI */
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                    new MainFrame().setVisible(true);
                }
            });
        }
    }

    public static void startReceiving() {
        // Start multicast
        synchronized (lock) {
            try {
                InetAddress[] addresses = InetUtils.getLocalAddresses();
                jmdns = new JmDNS[addresses.length];
                serverThreads = new ServerThread[addresses.length];
                for (int i = 0; i < addresses.length; i++) {
                    serverThreads[i] = new ServerThread(addresses[i]);
                    serverThreads[i].start();
                    out.println("Register service in " + addresses[i].getHostAddress());
                    jmdns[i] = JmDNS.create(addresses[i], HOST_NAME);
                    ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE, HOST_NAME,
                            PORT, "MessageReceiver on " + HOST_NAME);
                    jmdns[i].registerService(serviceInfo);
                    out.println("Service " + serviceInfo.getName() + " registered");
                }
            } catch (SocketException ex) {
                Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        out.println("Receiving started");
        // Multicast working
    }
    
    public static void stopReceiving() {
        synchronized (lock) {
            InetAddress[] addresses;
            try {
                addresses = InetUtils.getLocalAddresses();
            } catch (SocketException ex) {
                Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            for (int i = 0; i < addresses.length; i++) {
                Socket serv;
                try {
                    serv = new Socket(addresses[i], PORT);
                    serv.getOutputStream().write(FLAG_STOP_ALL);
                    serv.close();
                } catch (IOException ex) {
                    //Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                    out.println("Address " + addresses[i] + " already stopped");
                }
            }
        }
        while (running()) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        out.println("Receiving stopped");
    }

    public static class ServerThread implements Runnable {

        private static int count = 0;

        private final InetAddress addr;
        private final ServerSocket serverSocket;
        private final Thread thread;
        private boolean destroyed;

        public ServerThread(InetAddress addr) throws IOException {
            this.addr = addr;
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(addr, PORT));
            thread = new Thread(this);
            count++;
        }

        public void start() {
            thread.start();
        }

        @Override
        public void run() {
            out.println("Server " + addr.getHostName() + " started");
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    int flag = socket.getInputStream().read();
                    if (flag == FLAG_REQUEST) {
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        String text = in.readUTF();
                        out.println(text);
                        playText(text);
                        in.close();
                    } else if (flag == FLAG_STOP_ALL) {
                        out.println("Detected \"stop all\" signal from " + socket.getInetAddress().getHostName());
                        for (ServerThread st : serverThreads) {
                            st.stop();
                        }
                        break;
                    }
                } catch (IOException ex) {
                    Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            out.println("Server " + addr.getHostName() + " finished");
            if (!destroyed) {
                stop();
            }
        }

        public void stop() {
            if (destroyed) {
                return;
            }
            destroyed = true;
            count--;
            if (count == 0) {
                stopServicesMDNS();
            }
            thread.interrupt();
            try {
                serverSocket.close();
            } catch (IOException ex) {
                Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
            }
            out.println("Server " + addr.getHostName() + " stopped");
        }
    }

    private static void stopServicesMDNS() {
        synchronized (lock) {
            if (jmdns != null) {
                for (JmDNS mdns : jmdns) {
                    if (mdns != null) {
                        try {
                            mdns.unregisterAllServices();
                            mdns.close();
                            mdns = null;
                        } catch (IOException ex) {
                            Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            out.println("mDNS released");
        }
    }

    private static void playText(final String text) {
        try {
            URL url = new URL("http://translate.google.com/translate_tts?ie=UTF-8&tl=ru&q=" + URLEncoder.encode(text, "UTF-8"));
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
            Player player = new Player(conn.getInputStream());
            out.println("Player created");
            player.play();
            out.println("Player playing");
        } catch (JavaLayerException ex) {
            Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static boolean running() {
        synchronized (lock) {
            InetAddress[] addresses;
            try {
                addresses = InetUtils.getLocalAddresses();
            } catch (SocketException ex) {
                Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
            for (int i = 0; i < addresses.length; i++) {
                ServerSocket serv;
                try {
                    serv = new ServerSocket();
                    serv.bind(new InetSocketAddress(addresses[i], PORT));
                    serv.close();
                } catch (BindException ex) {
                    //Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                    out.println("Detected running receiver on " + addresses[i]);
                    return true;
                } catch (IOException ex) {
                    Logger.getLogger(MessageReceiver.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return false;
        }
    }

}
