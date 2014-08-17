package com.RbGroup.homemanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.http.conn.util.InetAddressUtils;
import org.java_websocket.server.WebSocketServer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class MainActivity extends Activity {

	/************************************************************************************************************************/
	public String getLocalIpAddress() {
		
		String ipv4 = null;
		
	    try {
	        for (Enumeration<NetworkInterface> en = NetworkInterface
	                .getNetworkInterfaces(); en.hasMoreElements();) {
	            NetworkInterface intf = en.nextElement();
	            for (Enumeration<InetAddress> enumIpAddr = intf
	                    .getInetAddresses(); enumIpAddr.hasMoreElements();) {
	                InetAddress inetAddress = enumIpAddr.nextElement();
	                System.out.println("ip1--:" + inetAddress);
	                System.out.println("ip2--:" + inetAddress.getHostAddress());

	      // for getting IPV4 format
	      if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(ipv4 = inetAddress.getHostAddress())) {

	                    String ip = inetAddress.getHostAddress().toString();
	                    System.out.println("ip---::" + ip);
	                   
	                    return ipv4;
	                }
	            }
	        }
	    } catch (Exception ex) {
	        Log.e("IP Address", ex.toString());
	    }
	    return null;
	}

	// Android
	/************************************************************************************************************************/

	private static final int VENDOR_ID = 9025;
	private WebSocketServer webSocketServer;
	private UsbSerialDriver driver = null;
	private static final int BAUD = 9600;
	private boolean connected = false;
	private HttpServer webserver;
	private boolean isRun = true;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		webSocketServer = new SocketServer(new InetSocketAddress(8080));
		webSocketServer.start();

		//Get IPv4
		new Thread() {
			public void run() {
				try {
					
					System.out.println(InetAddress.getLocalHost().getAddress());
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
			};
		}.start();
		
		UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
		Set<String> deviceNames = devices.keySet();
		for (Iterator<String> iterator = deviceNames.iterator(); iterator
				.hasNext();) {
			String deviceName = iterator.next();
			UsbDevice device = devices.get(deviceName);

			System.out.println("getVendorId : " + device.getVendorId());

			if (device.getVendorId() == VENDOR_ID) {
				driver = UsbSerialProber.acquire(usbManager, device);
				break;
			}
		}

		if (driver != null) {
			try {
				driver.open();
				driver.setBaudRate(BAUD);
				driver.setReadBufferSize(64);
				connected = true;
				receveTask.execute();
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Driver 연결에 실패 했습니다.\n 다시 시도해 주세요.",
						Toast.LENGTH_LONG).show();
			}

		}
		try {
			webserver = new HttpServer();
			webserver.start();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	AsyncTask<Void, String, Void> receveTask = new AsyncTask<Void, String, Void>() {
		byte[] read = new byte[128];
		byte[] buff = new byte[128];
		int buffCnt = 0;

		@Override
		protected Void doInBackground(Void... params) {
			try {
				while (isRun) {
					Arrays.fill(read, (byte) 0);
					int readCnt = driver.read(read, 500);
					// Log.d("arduino", "read("+readCnt + ")" + new String(read,
					// 0, readCnt));
					for (int i = 0; i < readCnt; i++) {

						if (read[i] == 13) {
							String msg = new String(buff, 0, buffCnt);
							Arrays.fill(buff, (byte) 0);
							buffCnt = 0;
							Log.d("arduino", msg);
							publishProgress(msg);
						} else if (read[i] != 10 && read[i] != 13) {
							if (buffCnt >= buff.length) {
								Arrays.fill(buff, (byte) 0);
							}
							buff[buffCnt++] = read[i];
						}
					}
					// Thread.sleep(50);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	};

	// HttpServer
	/************************************************************************************************************************/
	private class HttpServer extends NanoHTTPD {

		private static final int PORT = 8081;

		public HttpServer(int port) {
			super(port);
		}

		public HttpServer() throws IOException {
			super(PORT);
		}

		void log(IHTTPSession session) {
			StringBuilder builder = new StringBuilder();
			builder.append(session.getUri()).append(" ");
			Map<String, String> params = session.getParms();
			Set<String> keys = params.keySet();
			Iterator<String> iterator = keys.iterator();
			while (iterator.hasNext()) {
				String key = iterator.next();
				String value = params.get(key);
				builder.append(key).append(":").append(value).append(" ");
			}
			Log.d("arduino", builder.toString());
		}

		@Override
		public Response serve(IHTTPSession session) {
			log(session);
			String uri = session.getUri();
			if (uri.endsWith(".do")) {
				if (uri.equalsIgnoreCase("/command.do")) {

					// String key = session.getParms().get("key");
					// String value = session.getParms().get("value");

					String result = "{'result':'OK'}";
					return new NanoHTTPD.Response(Status.OK, MIME_HTML, result);
				} else if (uri.equalsIgnoreCase("/monitoring.do")) {
					return new NanoHTTPD.Response(Status.OK, MIME_HTML,
							"{\"L1\":ON}");
				}
			} else {
				return htdocs(uri, "www");

			}
			return new NanoHTTPD.Response(Status.NOT_FOUND, MIME_HTML,
					"<h1>404 Not found</h1>");
		}

		NanoHTTPD.Response htdocs(String url, String root) {
			String fileName = "www" + url;
			if ("/".equals(url)) {
				fileName = "www/index.html";
			}

			BufferedReader reader = null;
			StringBuilder builder = new StringBuilder();
			try {
				reader = new BufferedReader(new InputStreamReader(getAssets()
						.open(fileName)));
				String msg = reader.readLine();
				while (msg != null) {
					builder.append(msg).append("\n");
					msg = reader.readLine();

				}
			} catch (IOException e) {
				e.printStackTrace();
				return new NanoHTTPD.Response(Status.NOT_FOUND, MIME_HTML,
						"<h1>404 Not found</h1>");
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			String mimeType = getMimeType(url);

			return new NanoHTTPD.Response(Status.OK, mimeType,
					builder.toString());

		}

		public String getMimeType(String url) {
			String mimeType = MIME_HTML;
			if (url.endsWith("js")) {
				mimeType = "text/javascript";
			} else if (url.endsWith("css")) {
				mimeType = "text/css";
			} else if (url.endsWith("png")) {
				mimeType = "image/png";
			} else if (url.endsWith("ttf")) {
				mimeType = "application/x-font-ttf";
			} else if (url.endsWith("otf")) {
				mimeType = "application/x-font-opentype";
			} else if (url.endsWith("eot")) {
				mimeType = "application/vnd.ms-fontobject";
			} else if (url.endsWith("woff")) {
				mimeType = "application/font-woff";
			}
			return mimeType;
		}
	}
}