package com.dittybot.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.WindowManager;

public class GlobalVars extends Application {
	
	public boolean appInstalled = false;
	public boolean isRunning = false; //flag for when app might get launched from gmail etc. to check if app is already running. set in LaunchActivity
	public String appID = null; //random UUID generated on 1st run of app. used in concert with licensing		
	public boolean firstRun = false; //flag set on first run to give user a welcome/to register etc.
	public boolean licenseOK = false;		
	
	//GooglePlay key given for app
	public String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzxqJPlMJ24k2uO7yPFvBtpw3m7tkh4Hpq1lT8LA/jbwti7i8Dl0rdQcBpKQSAI8nYsiO6QPYfH+T+DMeTBgdrl16KJ5bFQ7bMkvrs2HtpUtyQESbtLyHVUMf9CqAXf7NSAFQCNAdY7XszChNA4OyEDoBu4Hm4Pi4c48InIQCI2ZE7zNOPm7NwCTMllBflDZ+fVCmVw4ipR+UbwLMBJ2ty9gI3Jr6ZA4yH7YuN/bWGqZ+pRAirt4yGH3uLfJsOio2WQlqsuDI9geKMphUyM4cJHINx9HwKbF0SRPffFTjDBsL36Mvyta0s7CeBSw8C+XoWWlT45lSCjXrbBqcebFbQwIDAQAB";
	//SALT I made with online generator utility. just random numbers 
	public byte[] SALT = new byte[] {46,67,62,64,24,86,57,56,40,41,21,76,77,22,38,11,37,58,31,63};		
	
	public String appPath;
	public String extStorPath; //external storage. usually SD card (but not always)
	
	public PdService pdService = null;
	public PdUiDispatcher pdRcvr; //the bridge between the Pd patches and the Java code
	
	public int screenWidth; //get hardware-specific sizes in pixels so can size UI controls accordingly
	public int screenHeight;
	public float TRKSIZE_DP = 60; //h/w of tracks ui, pic dimensions in device independent pixels (dp)
	public float LEDSIZE_DP = 8; //dp width of the track on/off LED's
	
	public List<Instruments> instruments; //data structure to instrument details and status
	public List<Drums> drums;
	
	public String[] noteNames; //the 0-127 midi notes paired with their music note letter names. read from file		
		
	public String defaultInstmt = "Acoustic Guitar"; //TODO make user settable. save in appstate.txt 
	
	public int uiLoop_ms = 40; //interval to update UI in ms. TODO maybe make settable
	
	public boolean streaming = false; //	
	
	public int scrollColor = 0xFFAB3807; //background color indicating a scrollable menu
	//public int scoreColor = 0xFF1e8896; //background color for scoring areas
	
	
	@Override
    public void onCreate() {		
		super.onCreate();		
		System.out.println("GlobalVars onCreate()");		
		
		appPath = getFilesDir().toString(); //these are useful when in a non-Activity class that does not have these methods		
		extStorPath = Environment.getExternalStorageDirectory().toString();		
		
		initSystemServices();
		bindService(new Intent(this, PdService.class), pdConnection, BIND_AUTO_CREATE);		
		
		pdRcvr = new PdUiDispatcher();				
	    PdBase.setReceiver(pdRcvr);
	    
	    getScreenSize();
	} 	
	
    private void initSystemServices() {
		System.out.println("GV initSystemServices()");
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				
				if (pdService == null) return;
				if (state == TelephonyManager.CALL_STATE_IDLE) {
					start(); } else {
						pdService.stopAudio(); } 
			}			
		}, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
    private final ServiceConnection pdConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {			
			pdService = ((PdService.PdBinder)service).getService();
			try {
				initPdAudio();				
			} catch (IOException e) {
				System.out.println("pdConnection io error");				
			}			
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// this method will never be called
			System.out.println("onServiceDisconnected " + name);
		}
	};
	
	private void initPdAudio() throws IOException {
		System.out.println("GV initPdAudio()"); 
		// Configure the audio glue
    	int sampleRate = AudioParameters.suggestSampleRate();
    	System.out.println("initPdAudio() sampleRate: " + sampleRate);
    	pdService.initAudio(sampleRate, 1, 2, 10.0f);  
		start();		
		
    	// TODO: my phone defaults to 44.1 kHz sampleRate but need to make sure 
    	// that always happens to maintain pitch. So warn users up front  that their 
    	// device needs to support CD quality, and exit gracefully if it won't		
    }  
	
	private void start() {
    	System.out.println("GV start()");    	
    	if (!pdService.isRunning()) {        		
    		pdService.startAudio();    		
    	}    	
    }
	
    @SuppressWarnings("deprecation")
	@TargetApi(13) 
    private void getScreenSize() {
    	WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		
		if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) < 13 ) {			 
	        screenWidth = display.getWidth();
	        screenHeight = display.getHeight();	        
		}
		else {
			Point size = new Point();				
			display.getSize(size);
			screenWidth = size.x;
            screenHeight = size.y;
		}
		
		if (screenWidth < screenHeight) { //because orientation
			int temp = screenWidth;
			screenWidth = screenHeight;
			screenHeight = temp;
		}
		
		System.out.println("gv screen w " + screenWidth +" h " + screenHeight);
    }
}
