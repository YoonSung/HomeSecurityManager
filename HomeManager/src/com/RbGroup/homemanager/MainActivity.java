package com.RbGroup.homemanager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.apache.http.conn.util.InetAddressUtils;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("NewApi")
public class MainActivity extends Activity 
    implements View.OnTouchListener, CameraView.CameraReadyCallback, OverlayView.UpdateDoneCallback{
    private static final String TAG = "HOME_MANGER";

    boolean inProcessing = false;
    final int maxVideoNumber = 3;
    VideoFrame[] videoFrames = new VideoFrame[maxVideoNumber];
    byte[] preFrame = new byte[1024*1024*8];
    
    HttpServer webServer = null;
    private CameraView cameraView_;
    private OverlayView overlayView_;
    private Button btnExit, btnToggle;
    private TextView tvMessage1;
    private TextView tvMessage2;

    private AudioRecord audioCapture = null;
    private StreamingLoop audioLoop = null;
    
    //Arduino Declation Start
    /**************************************************************************************************/
	private static final int VENDOR_ID = 9025;
	private UsbSerialDriver driver = null;
	private static final int BAUD = 9600;
	private boolean connected = false;
	private boolean isRun = true;
	
	//Arduino Declation End
	/**************************************************************************************************/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);    
        //win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); 

        setContentView(R.layout.main);

        btnExit = (Button)findViewById(R.id.btn_exit);
        btnToggle = (Button)findViewById(R.id.btn_toggle);
        btnExit.setOnClickListener(exitAction);
        btnToggle.setOnClickListener(testToggle);
        
        tvMessage1 = (TextView)findViewById(R.id.tv_message1);
        tvMessage2 = (TextView)findViewById(R.id.tv_message2);
        
        for(int i = 0; i < maxVideoNumber; i++) {
            videoFrames[i] = new VideoFrame(1024*1024*2);        
        }    

        System.loadLibrary("mp3encoder");
        System.loadLibrary("natpmp");
        
        initAudio();
        initCamera();
        initArduino();
    }
    
	private void initArduino() {
    	//Arduino Setting Start
        /**************************************************************************************************/
        
        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);

		HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
		Set<String> deviceNames = devices.keySet();

		//System.out.println(devices.size());
		//System.out.println(deviceNames.size());
		
		for (Iterator<String> iterator = deviceNames.iterator(); iterator
				.hasNext();) {
			String deviceName = iterator.next();
			//sb.append("deviceName = " + deviceName + "\n");
			UsbDevice device = devices.get(deviceName);

			//sb.append("getVendorId : " + device.getVendorId() + "\n");

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
		
		//sb.append("driver : " + driver);
		
		//Arduino Setting End
        /**************************************************************************************************/		
	}

	@Override
    public void onCameraReady() {
        if ( initWebServer() ) {
            int wid = cameraView_.Width();
            int hei = cameraView_.Height();
            cameraView_.StopPreview();
            cameraView_.setupCamera(wid, hei, previewCb_);
            cameraView_.StartPreview();
        }
    }
 
    @Override
    public void onUpdateDone() {
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }   

    @Override
    public void onStart(){
        super.onStart();
    }   

    @Override
    public void onResume(){
        super.onResume();
    }   
    
    @Override
    public void onPause(){  
        super.onPause();
        inProcessing = true;
        if ( webServer != null)
            webServer.stop();
        cameraView_.StopPreview(); 
        //cameraView_.Release();
        audioLoop.ReleaseLoop();
        audioCapture.release();
    
        //System.exit(0);
        finish();
    }  
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override 
    public boolean onTouch(View v, MotionEvent evt) { 
        

        return false;
    }
  
    private void initAudio() {
        int minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int minTargetSize = 4410 * 2;      // 0.1 seconds buffer size
        if (minTargetSize < minBufferSize) {
            minTargetSize = minBufferSize;
        }
        if (audioCapture == null) {
            audioCapture = new AudioRecord(MediaRecorder.AudioSource.MIC,
                                        44100,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        minTargetSize);
        }

        if ( audioLoop == null) {  
            Random rnd = new Random();
            String etag = Integer.toHexString( rnd.nextInt() );
            audioLoop = new StreamingLoop("HOME_MANGER" + etag );
        }

    }

    private void initCamera() {
        SurfaceView cameraSurface = (SurfaceView)findViewById(R.id.surface_camera);
        cameraView_ = new CameraView(cameraSurface);        
        cameraView_.setCameraReadyCallback(this);

        overlayView_ = (OverlayView)findViewById(R.id.surface_overlay);
        overlayView_.setOnTouchListener(this);
        overlayView_.setUpdateDoneCallback(this);
    }
    
    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    //if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && inetAddress.isSiteLocalAddress() ) {
                    if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(inetAddress.getHostAddress()) ) {
                        String ipAddr = inetAddress.getHostAddress();
                        return ipAddr;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.d(TAG, ex.toString());
        }
        return null;
    }   

    private boolean initWebServer() {
        String ipAddr = getLocalIpAddress();
        if ( ipAddr != null ) {
            try{
                webServer = new HttpServer(8080, this); 
                webServer.registerCGI("/cgi/query", doQuery);
                webServer.registerCGI("/cgi/setup", doSetup);
                webServer.registerCGI("/stream/live.jpg", doCapture);
                webServer.registerCGI("/stream/live.mp3", doBroadcast);
                webServer.registerCGI("/move", doMove);
            }catch (IOException e){
                webServer = null;
            }
        }
        if ( webServer != null) {
            tvMessage1.setText( getString(R.string.msg_access_local) + " http://" + ipAddr  + ":8080" );
            //tvMessage2.setText( getString(R.string.msg_access_query));
            //tvMessage2.setVisibility(View.VISIBLE);
            NatPMPClient natQuery = new NatPMPClient();
            natQuery.start();  
            return true;
        } else {
            tvMessage1.setText( getString(R.string.msg_error) );
            tvMessage2.setVisibility(View.GONE);
            return false;
        }
          
    }
   
    private OnClickListener exitAction = new OnClickListener() {
        @Override
        public void onClick(View v) {
            onPause();
        }   
    };
    
    private OnClickListener testToggle = new OnClickListener() {
    	
		@Override
		public void onClick(View v) {
			
			if (btnToggle.getText().toString().equalsIgnoreCase("off")) {
				sendCmd("0");
				btnToggle.setText("on");
				//Toast.makeText(MainActivity.this, "OFF !!", Toast.LENGTH_SHORT).show();
			} else {
				sendCmd("1");
				btnToggle.setText("off");
				//Toast.makeText(MainActivity.this, "ON !!", Toast.LENGTH_SHORT).show();
			}
		}
    	
    };
   
    private PreviewCallback previewCb_ = new PreviewCallback() {
        public void onPreviewFrame(byte[] frame, Camera c) {
            if ( !inProcessing ) {
                inProcessing = true;
           
                int picWidth = cameraView_.Width();
                int picHeight = cameraView_.Height(); 
                ByteBuffer bbuffer = ByteBuffer.wrap(frame); 
                bbuffer.get(preFrame, 0, picWidth*picHeight + picWidth*picHeight/2);

                inProcessing = false;
            }
        }
    };
    
    private HttpServer.CommonGatewayInterface doQuery = new HttpServer.CommonGatewayInterface () {
        @Override
        public String run(Properties parms) {
            String ret = "";
            List<Camera.Size> supportSize =  cameraView_.getSupportedPreviewSize();                             
            ret = ret + "" + cameraView_.Width() + "x" + cameraView_.Height() + "|";
            for(int i = 0; i < supportSize.size() - 1; i++) {
                ret = ret + "" + supportSize.get(i).width + "x" + supportSize.get(i).height + "|";
            }
            int i = supportSize.size() - 1;
            ret = ret + "" + supportSize.get(i).width + "x" + supportSize.get(i).height ;
            return ret;
        }
        
        @Override 
        public InputStream streaming(Properties parms) {
            return null;
        }    
    }; 

    private HttpServer.CommonGatewayInterface doSetup = new HttpServer.CommonGatewayInterface () {
        @Override
        public String run(Properties parms) {
            int wid = Integer.parseInt(parms.getProperty("wid")); 
            int hei = Integer.parseInt(parms.getProperty("hei"));
            Log.d("HOME_MANGER", ">>>>>>>run in doSetup wid = " + wid + " hei=" + hei);
            cameraView_.StopPreview();
            cameraView_.setupCamera(wid, hei, previewCb_);
            cameraView_.StartPreview();
            return "OK";
        }   
 
        @Override 
        public InputStream streaming(Properties parms) {
            return null;
        }    
    }; 

    private HttpServer.CommonGatewayInterface doBroadcast = new HttpServer.CommonGatewayInterface() {
        @Override
        public String run(Properties parms) {
            return null;
        }   
        
        
        @Override 
        public InputStream streaming(Properties parms) {
            if ( audioLoop.isConnected() ) {     
                return null;                    // tell client is is busy by 503
            }    
 
            audioLoop.InitLoop(128, 8192);
            InputStream is = null;
            try{
                is = audioLoop.getInputStream();
            } catch(IOException e) {
                audioLoop.ReleaseLoop();
                return null;
            }
            
            audioCapture.startRecording();
            AudioEncoder audioEncoder = new AudioEncoder();
            audioEncoder.start();  
            
            return is;
        }

    };
    String temp;
    //working
    private HttpServer.CommonGatewayInterface doMove = new HttpServer.CommonGatewayInterface() {
        @Override
        public String run(Properties parms) {
//        	if (temp.equalsIgnoreCase("1"))
//        		temp = "0";
//        	else 
//        		temp = "1";
//        	
        	String direction = parms.getProperty("direction");
        	sendCmd(direction);
        	//TestCode
        	/*
        	if (direction.equalsIgnoreCase("w"))
        		sendCmd("1");
        	else
        		sendCmd("0");
        	*/
        	
        	
        	//Toast.makeText(MainActivity.this, "doMove!!!!!! Parameter is "+direction, 50000).show();
            return direction;
        }   
        
        
        @Override 
        public InputStream streaming(Properties parms) {
           
            return null;
        }

    };
    
	private HttpServer.CommonGatewayInterface doCapture = new HttpServer.CommonGatewayInterface () {
        @Override
        public String run(Properties parms) {
           return null;
        }   
        
        @Override 
        public InputStream streaming(Properties parms) {
            VideoFrame targetFrame = null;
            for(int i = 0; i < maxVideoNumber; i++) {
                if ( videoFrames[i].acquire() ) {
                    targetFrame = videoFrames[i];
                    break;
                }
            }
            // return 503 internal error
            if ( targetFrame == null) {
                Log.d("HOME_MANGER", "No free videoFrame found!");
                return null;
            }

            // compress yuv to jpeg
            int picWidth = cameraView_.Width();
            int picHeight = cameraView_.Height(); 
            YuvImage newImage = new YuvImage(preFrame, ImageFormat.NV21, picWidth, picHeight, null);
            targetFrame.reset();
            boolean ret;
            inProcessing = true;
            try{
                ret = newImage.compressToJpeg( new Rect(0,0,picWidth,picHeight), 30, targetFrame);
            } catch (Exception ex) {
                ret = false;    
            } 
            inProcessing = false;

            // compress success, return ok
            if ( ret == true)  {
                parms.setProperty("mime", "image/jpeg");
                InputStream ins = targetFrame.getInputStream();
                return ins;
            }
            // send 503 error
            targetFrame.release();

            return null;
        }
    }; 

    static private native int nativeOpenEncoder();
    static private native void nativeCloseEncoder();
    static private native int nativeEncodingPCM(byte[] pcmdata, int length, byte[] mp3Data);    
    private class AudioEncoder extends Thread {
        byte[] audioPackage = new byte[1024*16];
        byte[] mp3Data = new byte[1024*8];
        int packageSize = 4410 * 2;
        @Override
        public void run() {
            nativeOpenEncoder(); 
            
            OutputStream os = null;
            try {
                os = audioLoop.getOutputStream();
            } catch(IOException e) {
                os = null;
                audioLoop.ReleaseLoop();
                nativeCloseEncoder();
                return;
            }
            
            while(true) {

                int ret = audioCapture.read(audioPackage, 0, packageSize);
                if ( ret == AudioRecord.ERROR_INVALID_OPERATION ||
                        ret == AudioRecord.ERROR_BAD_VALUE) {
                    break; 
                }

                //TODO: call jni encoding PCM to mp3
                ret = nativeEncodingPCM(audioPackage, ret, mp3Data);          
                
                try {
                    os.write(mp3Data, 0, ret);
                } catch(IOException e) {
                    break;    
                }
            }
            
            audioLoop.ReleaseLoop();
            nativeCloseEncoder();
        }
    }

    
    static private native String nativeQueryInternet();    
    private class NatPMPClient extends Thread {
        String queryResult;
        Handler handleQueryResult = new Handler(getMainLooper());  
        @Override
        public void run(){
            queryResult = nativeQueryInternet();
            if ( queryResult.startsWith("error:") ) {
                handleQueryResult.post( new Runnable() {
                    @Override
                    public void run() {
                        tvMessage2.setText( getString(R.string.msg_access_query_error));                        
                    }
                });
            } else {
                handleQueryResult.post( new Runnable() {
                    @Override
                    public void run() {
                        tvMessage2.setText( getString(R.string.msg_access_internet) + " " + queryResult );
                    }
                });
            }
        }    
    }
    
    
    //Arduino Code Start
  	/**************************************************************************************************/
    
    private void sendCmd(String cmd) {
    	
    	int result = 0;
    	
		if (driver != null && connected) {
			try {
				Log.d("Arduino Command : ", cmd);
				driver.write(cmd.getBytes(), 1000);
				//Toast.makeText(this, "데이터 전달 : "+result, Toast.LENGTH_LONG).show();
			} catch (IOException e) {
				e.printStackTrace();
				//Toast.makeText(this, "데이터 전달실패", Toast.LENGTH_LONG).show();
			}
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
	
	//Arduino Code End
	/**************************************************************************************************/
}    

