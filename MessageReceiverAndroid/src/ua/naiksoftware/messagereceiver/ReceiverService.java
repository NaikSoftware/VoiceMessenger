package ua.naiksoftware.messagereceiver;

import java.net.*;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import ua.naiksoftware.utils.FileUtils;
import ua.naiksoftware.utils.InetUtils;

public class ReceiverService extends Service {

    public static final String ACTION_UPDATE_RECEIVER = "act_update_receiver";

    private static final String TAG = ReceiverService.class.getSimpleName();
    public static final String HOST_NAME = android.os.Build.DEVICE;
    public static final String SERVICE_TYPE = "_messagesender._tcp.local.";
    public static final int PORT = 6432;
    public static final byte FLAG_REQUEST = 0x03;
    public static final byte FLAG_STOP_ALL = 0x05;
    private static ServerThread[] serverThreads;
    private static JmDNS[] jmdns;
    private static final Object lock = new Object();
    private static final Semaphore waiter = new Semaphore(0);
    private Handler uiHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        uiHandler = new Handler();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ReceiverService.running()) {
            startReceiving();
        }
        return Service.START_STICKY;
    }

    private void startReceiving() {
        // Start multicast
        new Thread(new Runnable() {

            @Override
            public void run() {
                synchronized (lock) {
                    try {
                        InetAddress[] addresses = InetUtils.getLocalAddresses();
                        jmdns = new JmDNS[addresses.length];
                        serverThreads = new ServerThread[addresses.length];
                        for (int i = 0; i < addresses.length; i++) {
                            serverThreads[i] = new ServerThread(addresses[i], ReceiverService.this);
                            serverThreads[i].start();
                            Log.i(TAG, "Register service in " + addresses[i].getHostAddress());
                            jmdns[i] = JmDNS.create(addresses[i], HOST_NAME);
                            ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE, HOST_NAME,
                                    PORT, "MessageReceiver on " + HOST_NAME);
                            jmdns[i].registerService(serviceInfo);
                            Log.i(TAG, "Service " + serviceInfo.getName() + " registered");
                            toast("Service " + serviceInfo.getName() + " registered on " + addresses[i]);
                        }
                    } catch (SocketException ex) {
                        Log.e(TAG, "", ex);
                    } catch (IOException ex) {
                        Log.e(TAG, "", ex);
                    }
                    waiter.release();
                }
            }
        }).start();
        try {
            waiter.acquire();
        } catch (InterruptedException ex) {
            Log.e(TAG, "", ex);
        }
        Log.i(TAG, "Receiving started");
        Intent intent = new Intent(ACTION_UPDATE_RECEIVER);
        sendBroadcast(intent);
        // Multicast working
    }

    private void stopReceiving() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                synchronized (lock) {
                    InetAddress[] addresses;
                    try {
                        addresses = InetUtils.getLocalAddresses();
                    } catch (SocketException ex) {
                        Log.e(TAG, "", ex);
                        return;
                    }
                    for (int i = 0; i < addresses.length; i++) {
                        try {
                            Socket serv = new Socket(addresses[i], PORT);
                            serv.getOutputStream().write(FLAG_STOP_ALL);
                            serv.close();
                        } catch (IOException ex) {
                            //Log.e(TAG, "", ex);
                            Log.i(TAG, "Address " + addresses[i] + " already stopped");
                        }
                    }
                }
            }
        }).start();

        try {
            waiter.acquire();
        } catch (InterruptedException ex) {
            Log.e(TAG, "", ex);
        }
        Log.i(TAG, "Receiving stopped");
        toast("Receiving stopped");
        Intent intent = new Intent(ACTION_UPDATE_RECEIVER);
        sendBroadcast(intent);
    }

    private static class ServerThread implements Runnable {

        private static int count = 0;

        private final InetAddress addr;
        private final ServerSocket serverSocket;
        private final Thread thread;
        private boolean destroyed;
        private ReceiverService service;

        public ServerThread(InetAddress addr, ReceiverService service) throws IOException {
            this.addr = addr;
            this.service = service;
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
            Log.i(TAG, "Server " + addr.getHostName() + " started");
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    int flag = socket.getInputStream().read();
                    if (flag == FLAG_REQUEST) {
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        String text = in.readUTF();
                        Log.i(TAG, text);
                        playText(text, service);
                        in.close();
                    } else if (flag == FLAG_STOP_ALL) {
                        Log.i(TAG, "Detected \"stop all\" signal from " + socket.getInetAddress().getHostName());
                        for (ServerThread st : serverThreads) {
                            st.stop();
                        }
                        break;
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "", ex);
                }
            }
            Log.i(TAG, "Server " + addr.getHostName() + " finished");
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
                service.stopSelf();
            }
            thread.interrupt();
            try {
                serverSocket.close();
            } catch (IOException ex) {
                Log.e(TAG, "", ex);
            }
            Log.i(TAG, "Server " + addr.getHostName() + " stopped");
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
                            Log.e(TAG, "", ex);
                        }
                    }
                }
            }
            waiter.release();
            Log.i(TAG, "mDNS released");
        }
    }

    private static void playText(String text, ReceiverService service) {
        try {
            URL url = new URL("http://translate.google.com/translate_tts?ie=UTF-8&tl=ru&q=" + URLEncoder.encode(text, "UTF-8"));
            File file = FileUtils.saveTempFile(url);
            MediaPlayer player = MediaPlayer.create(service, Uri.fromFile(file));
            player.start();

            Log.i(TAG, "Player playing");
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "", ex);
        } catch (IOException ex) {
            Log.e(TAG, "", ex);
        }
    }

    public static boolean running() {
        synchronized (lock) {
            InetAddress[] addresses;
            try {
                addresses = InetUtils.getLocalAddresses();
            } catch (SocketException ex) {
                Log.e(TAG, "", ex);
                return false;
            }
            for (int i = 0; i < addresses.length; i++) {
                ServerSocket serv;
                try {
                    serv = new ServerSocket();
                    serv.bind(new InetSocketAddress(addresses[i], PORT));
                    serv.close();
                } catch (BindException ex) {
                    //Log.e(TAG, "", ex);
                    Log.i(TAG, "Detected running receiver on " + addresses[i]);
                    return true;
                } catch (IOException ex) {
                    Log.e(TAG, "", ex);
                }
            }
            return false;
        }
    }

    private void toast(final String msg) {
        uiHandler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(ReceiverService.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        if (ReceiverService.running()) {
            stopReceiving();
        }
        toast("Message receiver stopped");
        super.onDestroy();
    }

}
