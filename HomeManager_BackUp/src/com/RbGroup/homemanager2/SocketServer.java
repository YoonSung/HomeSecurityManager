package com.RbGroup.homemanager2;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import android.util.Log;

public class SocketServer extends WebSocketServer{
	public SocketServer(InetSocketAddress address) {
		super(address);
	}

	@Override
	public void onClose(WebSocket arg0, int arg1, String arg2, boolean arg3) {
		Log.d("arduino", "close");
	}

	@Override
	public void onError(WebSocket arg0, Exception e) {
		Log.d("aurduino", "Error :" + e.toString());
	}

	@Override
	public void onMessage(WebSocket arg0, String msg) {
		Log.d("aurduino", "RCV :" + msg);

	}

	@Override
	public void onOpen(WebSocket arg0, ClientHandshake arg1) {
		Log.d("arduino", "websocket open..");

	}
}
