package ua.naiksoftware.messagesender;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import ua.naiksoftware.utils.InetUtils;

/**
 *
 * @author Naik
 */
public class MainActivity extends ListActivity implements ServiceListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String SERVICE_TYPE = "_messagesender._tcp.local.";
    public static final byte FLAG_REQUEST = 0x03;
    public static final byte FLAG_STOP_ALL = 0x05;

    private ArrayList<Receiver> receivers = new ArrayList<Receiver>();
    private ReceiversAdapter receiversAdapter;
    private EditText editText;
    private JmDNS[] jmdns;
    private WifiManager.MulticastLock multicastLock;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        editText = (EditText) findViewById(R.id.message);
        setListAdapter(receiversAdapter = new ReceiversAdapter(this, 0, receivers));
        new Thread(new Runnable() {

            public void run() {
                try {
                    WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (wifi == null) {
                        toast(getString(R.string.wifi_not_detected));
                        return;
                    }
                    if (connectivityManager == null || !InetUtils.isConnected(connectivityManager)) {
                        toast(getString(R.string.connection_not_detected));
                        return;
                    }
                    InetAddress[] addresses = InetUtils.getLocalAddresses();
                    if (addresses.length == 0) {
                        toast(getString(R.string.cant_get_ip));
                        return;
                    }
                    multicastLock = wifi.createMulticastLock(TAG);
                    multicastLock.setReferenceCounted(false);
                    multicastLock.acquire();
                    jmdns = new JmDNS[addresses.length];
                    for (int i = 0; i < addresses.length; i++) {
                        jmdns[i] = JmDNS.create(addresses[i], android.os.Build.DEVICE);
                        jmdns[i].addServiceListener(SERVICE_TYPE, MainActivity.this);
                        toast(getString(R.string.listen) + ' ' + addresses[i].getHostAddress());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Create JmDNS Listener error", e);
                    toast(e.getLocalizedMessage());
                }
            }
        }).start();

        getListView().setLongClickable(true);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showStopReceiverDialog(receivers.get(position));
                return true;
            }
        });
    }

    @Override
    protected void onListItemClick(ListView l, View v, final int position, long id) {
        new Thread(new Runnable() {

            public void run() {
                Receiver receiver = receivers.get(position);
                String text = editText.getText().toString();
                Socket socket = null;
                try {
                    socket = new Socket(receiver.ip, receiver.port);
                    socket.getOutputStream().write(FLAG_REQUEST);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF(text);
                    out.flush();
                    out.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Connecting to " + receiver.ip + ":" + receiver.port + " error", ex);
                    toast(ex.getLocalizedMessage());
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            Log.e(TAG, "Close socket error", ex);
                        }
                    }
                }
            }
        }).start();
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.i(TAG, "Service added: " + event.getType() + "\n Info: " + event.getInfo());
        // Для вызова serviceResolved(...)
        event.getDNS().requestServiceInfo(event.getType(), event.getName(), 1 /* timeout 1ms*/);
    }

    @Override
    public void serviceRemoved(final ServiceEvent event) {
        Log.i(TAG, "Service removed: " + event.getType() + "\n Info: " + event.getInfo().getHostAddresses()[0]
                + ", event.getName(), " + event.getInfo().getPort());
        toast("Service removed: " + event.getType() + "\n Info: " + event.getInfo().getHostAddresses()[0]
                + ", event.getName(), " + event.getInfo().getPort());
        final Receiver r = new Receiver(event.getInfo().getHostAddresses()[0], event.getName(), event.getInfo().getPort());
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                receivers.remove(r);
                receiversAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void serviceResolved(final ServiceEvent event) {
        Log.i(TAG, "Service resolved: " + event.getType() + "\n Info: " + event.getInfo());
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                String name = event.getName();
                String ip = event.getInfo().getInetAddresses()[0].getHostAddress();
                Receiver receiver = new Receiver(ip, name, event.getInfo().getPort());
                receivers.remove(receiver); // remove if already exists
                receivers.add(receiver);
                receiversAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (jmdns != null) {
            for (JmDNS mdns : jmdns) {
                if (mdns != null) {
                    try {
                        mdns.removeServiceListener(SERVICE_TYPE, MainActivity.this);
                        mdns.close();
                        mdns = null;
                    } catch (IOException ex) {
                        Log.e(TAG, "Close jmdns error", ex);
                    }
                }
            }
        }
        if (multicastLock != null) {
            Log.i(TAG, "Releasing Mutlicast Lock...");
            multicastLock.release();
            multicastLock = null;
            toast("Multicast lock released");
        }
        super.onDestroy();
    }

    public static class Receiver {

        public final String ip;
        public final String name;
        public final int port;

        public Receiver(String ip, String name, int port) {
            this.ip = ip;
            this.name = name;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Receiver) {
                Receiver other = (Receiver) o;
                return ip.equals(other.ip) && name.equals(other.name) && port == other.port;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 61 * hash + (this.ip != null ? this.ip.hashCode() : 0);
            hash = 61 * hash + (this.name != null ? this.name.hashCode() : 0);
            hash = 61 * hash + this.port;
            return hash;
        }
    }

    private void toast(final String msg) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else {
            runOnUiThread(new Runnable() {

                public void run() {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void showStopReceiverDialog(final Receiver receiver) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.stop_receiver) + " " + receiver.ip + "?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {

                            public void run() {
                                Socket socket = null;
                                try {
                                    socket = new Socket(receiver.ip, receiver.port);
                                    OutputStream out = socket.getOutputStream();
                                    out.write(FLAG_STOP_ALL);
                                    out.flush();
                                    out.close();
                                } catch (IOException ex) {
                                    Log.e(TAG, "Stopping " + receiver.ip + ":" + receiver.port + " error", ex);
                                    toast(ex.getLocalizedMessage());
                                } finally {
                                    if (socket != null) {
                                        try {
                                            socket.close();
                                        } catch (IOException ex) {
                                            Log.e(TAG, "Close socket error", ex);
                                        }
                                    }
                                }
                            }
                        }).start();
                        receivers.remove(receiver);
                        receiversAdapter.notifyDataSetChanged();
                    }
                })
                .show();
    }
}
