package ua.naiksoftware.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Naik
 */
public class InetUtils {
    
    private static final Random RND = new Random();
    
    public static Inet4Address[] getLocalAddresses() throws SocketException {
        ArrayList<Inet4Address> results = new ArrayList<>(1);
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast()) {
                continue;
            }
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address) {
                    results.add((Inet4Address) addr);
                }
            }
        }
        return results.toArray(new Inet4Address[results.size()]);
    }
    
    public static String hostName() {
        String name = "Host" + RND.nextInt(100);
        String prop = null;
        try {
            prop = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            Logger.getLogger(InetUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (prop != null && !prop.equals("127.0.1.1")) {
            name = prop;
        }
        return name;
    }
    
}
