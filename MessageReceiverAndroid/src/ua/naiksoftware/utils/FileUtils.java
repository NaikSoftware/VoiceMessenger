package ua.naiksoftware.utils;
import java.io.*;

import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import android.os.Environment;

public class FileUtils {
	
	public static void write(String data, File file) throws IOException {
		FileWriter writer = null;
		try {
			writer = new FileWriter(file);
			writer.write(data);
			writer.flush();
		} catch (IOException e) {
			throw new IOException(e);
		} finally {
			if (writer != null) {
		    	writer.close();
			}
		}
	}
	
	public static File saveTempFile(URL url) throws FileNotFoundException, IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
        File file = File.createTempFile("play_text", ".mp3");
		//File file = new File(Environment.getExternalStorageDirectory(), "msg_temp.mp3");
		file.delete();
        FileOutputStream fos = new FileOutputStream(file);
		write(conn.getInputStream(), fos);
        return file;
    }
	
	public static void write(InputStream is, OutputStream os) throws IOException {
		byte[] buff = new byte[4096];
		int n;
		while ((n = is.read(buff)) != -1) {
			os.write(buff, 0, n);
		}
		os.flush();
		os.close();
		is.close();
	}
}
