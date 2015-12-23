package com.dittybot.app;

/**
 * This activity is the first one launched after GlobalVars sets Application level flags
 * and routes to other activities based on those flags
 * This Activity has noHistory="true" in Manifest
 */


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//import org.puredata.core.PdBase;
//import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources.NotFoundException;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class LaunchActivity extends Activity {
	
	private GlobalVars gv;	
	private LicenseCheckerCallback mLicenseCheckerCallback;
	private LicenseChecker mChecker;
	private int retry_count = 0; //count of how many times retry license verification
	private int MAX_RETRIES = 4; //maximum # times to retry license verification
	private int dialogsOpen = 0; //check variable to make sure dialogs acknowledged before going to mainscreen
	private boolean lvlCheck = false; //flag for if the license check response has arrived
	
	/**
	 * TODO - need to log & handle the various possible license error states
	 */
	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.splashscreen); 
		
		System.out.println("onCreate() LaunchActivity");		
		
		gv = ((GlobalVars)getApplicationContext());		
		
		// LVL_library calls this when it's done.
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        
        mChecker = new LicenseChecker(
                this, new ServerManagedPolicy(this,
                    new AESObfuscator(gv.SALT, getPackageName(), gv.appID)),
                gv.BASE64_PUBLIC_KEY);
		
		//check SD card status before anything else	
		if (!checkSDCard()) {
			sdCardError(); //send user to SDCardError activity & inform to check card
			return;
		}
		else {
			System.out.println("checkSDCard() is OK: ");		
			
			new installApp().execute(); 
			//follow the start up chain of processes through the AsyncTasks below. Order is:
			//set up instruments master list
			//set up drums master list	
			//getNoteNames() - the A-G sharp/flat % octave # notation for the 0-127 note values	
			//check licensing 
			//**note need to do something with that gv.firstRun = true; flag
			//> then think need to jump to that MainMenu thing back in with options for Live Play or Song Editor etc.
		}		
	}	
	
	private boolean checkSDCard() { //check if SD card is mounted properly		
		boolean sdcardchk = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);		
		return sdcardchk;
	}
		
	private void sdCardError() { //if SD Card not mounted flag set in GlobalVars launch the SD Card Error screen
		Intent intent = new Intent(this, SDCardError.class);
		startActivity(intent);
	}
	
	//TODO all of this needs error checking & notifications big time
	private class installApp extends AsyncTask<Void, Void, Boolean> {
		
		ProgressBar spinner = (ProgressBar) findViewById(R.id.splashPB);
		TextView splashTV = (TextView) findViewById(R.id.splashTV);
		
		@Override
		protected void onPreExecute(){			
			
		    spinner.setVisibility(View.VISIBLE);		    
		    splashTV.setText("Verifying Installation");
		    splashTV.setVisibility(View.VISIBLE);
		}

		@Override
		protected Boolean doInBackground(Void... params) {
						
			boolean success = true;
			
			File appIDfile = new File(getFilesDir(), "appIDfile");
			
	    	if (!appIDfile.exists()) { //should only ever be true on 1st run, so use as flag to install app fully
	    		
	    		gv.firstRun = true;
	    		
	    		gv.appID = UUID.randomUUID().toString(); //generate the appID & write it to binary file
	    		System.out.println("appID: " + gv.appID);
	    		try {					
	    			FileOutputStream out = new FileOutputStream(appIDfile);
	    	    	out.write(gv.appID.getBytes());
	    	        out.close();
					
				} catch (Exception e) {
					System.out.println("error writing appIDfile");
					e.printStackTrace();
					//TODO need to alert here? not sure what could cause that
					//guess could tell user to try running again
				}
	    		
	    		File dittybot = new File(getFilesDir() + "/dittybot");
	    		
	    		if (!dittybot.exists()) { //dittybot directory tree is on the zip file in APK so unzip it & others
	    			System.out.println("creating directories"); //TODO maybe show install progress
	    			 
	    			//unzip the dittybot directory archive packaged in the APK
	    			File dir = getFilesDir();
	    			try {
						IoUtils.extractZipResource(getResources().openRawResource(R.raw.dittybot), dir, true);
					} catch (NotFoundException e) {						
						e.printStackTrace();
					} catch (IOException e) {						
						e.printStackTrace();
					} 
	    			
	    			if (dittybot.exists()) { 
	    				System.out.println("dittybot directory created");    					    	
	    				
	    				// set up the SD card directories- copy DittyBot dir tree & then delete from internal
	    				File sdDir = new File(dittybot + "/DittyBot"); // on internal storage
	    				
	    				File extDir = new File(gv.extStorPath + "/DittyBot");
	    				if (!extDir.exists()) {								
	    					try {
								FileUtilities.copy(sdDir, extDir);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
	    					//delete the copied dirs from internal storage
	    					deleteAllFiles(sdDir);
	    				}
	    				else {
	    					//TODO alert that directories exist from a previous install & ?
	    					//can only see how this would matter if user was reinstalling app
	    					//and you could let them know previous files exist & do they want to keep them
	    					//or delete them & start with a fresh install
	    					System.out.println("extDir already exists");
	    				}
	    			}
	    			else {
	    				System.out.println("error unzipping dittybot directories");
	    				//TODO should handle this. Inform user & direct accdgly
	    				success = false;
	    				return success;
	    			}	
	    		}    		
	    	}
	    	else  { //then file named 'appIDfile' was found so app was installed prior to this run
				System.out.println("app is already installed");			
				
				try {
					gv.appID = readAppIdFile(appIDfile);					
				} catch (IOException e) {					
					e.printStackTrace();
				} //retrieve the appID from storage
				System.out.println("appID " + gv.appID);
				
				/**
				 * TODO right here I wish I could verify somehow the appID against something like a device ID
				 * but from what I have read there is no guarantee a device carries a hardware ID. Kicking around
				 * idea of generating another UUID on each run that is stored with appID on dB Network. Each time 
				 * user starts app the server is pinged to see if both numbers match. If they don't then it will
				 * show that two or more devices are trying to use the same appID
				 */
				
				
				//TODO make sure sdCard directories still there as well	
				//TODO should also check if the dittybot files are still on internal storage
				
				//------------------------------------------------------------------------------				
				
				/*this is a development aid to quickly remove just SD card DittyBot directories				
				String extPath = Environment.getExternalStorageDirectory().toString() + "/DittyBot";
				File extDir = new File(extPath);
				if (extDir.exists()) deleteAllFiles(extDir);
				if (!extDir.exists()) {
					System.out.println("OK deleted DittyBot SD card directories");
				}
				*/			
				
				/*
				this is a development aid to quickly remove ALL DittyBot directories
				File dittybot = new File(getFilesDir() + "/dittybot");		
				
				if (dittybot.exists()) {
					System.out.println("trying to delete dittybot");
					deleteAllFiles(dittybot);
					
					String extPath = Environment.getExternalStorageDirectory().toString() + "/DittyBot";
					File extDir = new File(extPath);
					if (extDir.exists()) deleteAllFiles(extDir);
					if (!dittybot.exists()) {
						System.out.println("OK deleted dittybot");
					}				
				}
				*/
			}			
			return success;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {			
			
		    spinner.setVisibility(View.GONE);		    
		    splashTV.setVisibility(View.GONE);
			
			gv.appInstalled = result;
			
			if (gv.appInstalled) {	
				System.out.println("appInstalled = true");			
				
				//set up the instruments master list
				new instmtSetup().execute();								
			}
			else {
				System.out.println("******* APP INSTALL FAILED ********");
				//something went wrong TODO handle error
				//need to have noted the error above already
			}
		}		
	}
	
	private class instmtSetup extends AsyncTask<Void, Void, Boolean> {	
		
		ProgressBar spinner = (ProgressBar) findViewById(R.id.splashPB);
		TextView splashTV = (TextView) findViewById(R.id.splashTV);
		
		@Override
		protected void onPreExecute(){
			System.out.println("LaunchActivity/instmtSetup()");		
			
		    spinner.setVisibility(View.VISIBLE);
		    splashTV.setText("Initializing Instruments");
		    splashTV.setVisibility(View.VISIBLE);
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			boolean success = true;			
					
			gv.instruments = new ArrayList<Instruments>(); //init master list of instruments installed in app
			
		   	//create the entries in master instrument list directly from the instrument directories
	    	String path = gv.appPath + "/dittybot/instruments/";
	    	File instmtsDir = new File(path);
	    	if (instmtsDir.isDirectory()) {
	    		System.out.println("instmtsDir OK");
	    		
	    		String[] instmtDirs = instmtsDir.list();
	    		
	    		for (int i=0; i < instmtDirs.length; i++) { 	    			
	    			Instruments instrument = new Instruments();	    			
	    			String delimiter = "\\.";
	    			String[] elements = instmtDirs[i].split(delimiter); //split the dir name into # & name
	    			try {
	    				instrument.instmtNum = Integer.parseInt(elements[0]);
					} catch (NumberFormatException e) {					
						e.printStackTrace();
						instrument.status = setupError(instrument.status, 
    							"LaunchActivity/instmtSetup: parseInt error reading instmtNum");	    						    					
    					//System.out.println(instrument.status);
					}
	    			instrument.name = elements[1];	
	    			gv.instruments.add(instrument);
	    		}	    		
	    	}
	    	else {
	    		System.out.println("LaunchActivity/instmtSetup: problem with instruments directory");
	    		//TODO handle error. A big one
	    		success = false;
	    		return success;
	    	}
	    	
	    	//now set up sample arrays by counting the sample files in dirs	    	
	    	for (int i=0; i < gv.instruments.size(); i++) {
	    		String dirName = gv.instruments.get(i).instmtNum + "." + gv.instruments.get(i).name;
	    		String samples = gv.appPath + "/dittybot/instruments/" + dirName + "/samples";	    		
	    		File samplesDir = new File(samples);
	    		if (samplesDir.isDirectory()) {
	    			String[] sampleList = samplesDir.list();
	    			gv.instruments.get(i).samples = new Sample[sampleList.length]; //restrict length to same #	    			
	    			String delimiter = "\\.";
	    			for (int j=0; j < sampleList.length; j++) {	    				
	    				String[] elements = sampleList[j].split(delimiter);
	    				if (elements[1].contentEquals("wav")) { //verify all samples have .wav file extension
		    				Sample sample = new Sample(); //init a sample object and put sample file name in it read from dir
		    				sample.fileName = sampleList[j];
		    				gv.instruments.get(i).samples[j] = sample; //add sample object to instrument entry
	    				}
	    				else {
	    					gv.instruments.get(i).status = setupError(gv.instruments.get(i).status, 
	    							"LaunchActivity/instmtSetup: sample file does not have wav extension");	    						    					
	    					//System.out.println(gv.instruments.get(i).status);	    						    		    		
	    				}
	    			}
	    		}
	    		else {
	    			gv.instruments.get(i).status = setupError(gv.instruments.get(i).status, 
							"LaunchActivity/instmtSetup: problem with samples directory");  					   					
					//System.out.println(gv.instruments.get(i).status); 	  					    		
	    		}
	    	}
	    	
	    	//now read each instrument's info.txt file info in and verify
	    	for (int i=0; i < gv.instruments.size(); i++) {
	    		String dirName = gv.instruments.get(i).instmtNum + "." + gv.instruments.get(i).name; //use master list info
	    		String infoPath = gv.appPath + "/dittybot/instruments/" + dirName + "/info.txt";
	    		int sampleCt = 0; //to verify that #sample lines in info.txt match #samples in dir
	    		File info = new File(infoPath);
				String textLine = null;
				try {
					FileReader fileReader = new FileReader(info);
					BufferedReader textReader = new BufferedReader(fileReader);			
					while ((textLine = textReader.readLine()) != null ) {							
						String delimiter = "\\|";
						String[] elements = textLine.split(delimiter);
						if (elements[0].contentEquals("name")) { //verify name in info.txt matches the name in dir
							if (elements[1].contentEquals(gv.instruments.get(i).name)) {
								//System.out.println("LaunchActivity/instmtSetup: name match verified");
							}
							else {
								gv.instruments.get(i).status = setupError(gv.instruments.get(i).status, 
										"LaunchActivity/instmtSetup: name mismatch in info.txt file");
								//System.out.println(gv.instruments.get(i).status);								
							}
						}
						if (elements[0].contentEquals("inum")) {
							if (Integer.parseInt(elements[1]) != gv.instruments.get(i).instmtNum) {
								gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
										"LaunchActivity/instmtSetup: instmtNum mismatch in info.txt file");
							}																					
						}
						if (elements[0].contentEquals("samplerate")) {								
							try {
								gv.instruments.get(i).sampleRate = Integer.parseInt(elements[1]);
							} catch (NumberFormatException e) {								
								e.printStackTrace();
								gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
										"LaunchActivity/instmtSetup: parseInt failed on sampleRate in info.txt");
								//System.out.println(gv.instruments.get(i).status);
							}								
						}
						if (elements[0].contentEquals("sample")) {
							sampleCt++;
							//verify first that the sample line has correct # elements
							if (elements.length == 7) { //TODO make a var instead of hardcoded #
								//System.out.println("sample line has correct number elements");
								
								//match the sample object fileName to the info.txt sample file name
								boolean found = false;
								for (int j=0; j < gv.instruments.get(i).samples.length; j++) {
									if (elements[1].contentEquals(gv.instruments.get(i).samples[j].fileName)) {
										//System.out.println("LaunchActivity/instmtSetup: sample fileName match verified");
										found = true;										
										
										try {
											gv.instruments.get(i).samples[j].numSamples = Integer.parseInt(elements[2]);
											//System.out.println("numSamples " + gv.instruments.get(i).samples[j].numSamples);
											
										} catch (NumberFormatException e) {											
											e.printStackTrace();
											gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
												"LaunchActivity/instmtSetup: parseInt error for numSamples in info.txt");
										}
										
										try {
											gv.instruments.get(i).samples[j].baseNote = Integer.parseInt(elements[3]);
											//System.out.println("baseNote " + gv.instruments.get(i).samples[j].baseNote);
											int note = gv.instruments.get(i).samples[j].baseNote;
											if (note < 0 || note > 127) { //then note range is out of bounds
												gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
														"LaunchActivity/instmtSetup: baseNote value is out of bounds");	
											}
										} catch (NumberFormatException e) {											
											e.printStackTrace();
											gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
													"LaunchActivity/instmtSetup: parseInt error for baseNote in info.txt");
										}
										
										try {
											gv.instruments.get(i).samples[j].baseRate = Float.parseFloat(elements[4]);
											//System.out.println("baseRate " + gv.instruments.get(i).samples[j].baseRate);
										} catch (NumberFormatException e) {											
											e.printStackTrace();
											gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
													"LaunchActivity/instmtSetup: parseFloat error for baseRate in info.txt");
										}
										
										try {
											gv.instruments.get(i).samples[j].loNote = Integer.parseInt(elements[5]);
											//System.out.println("loNote " + gv.instruments.get(i).samples[j].loNote);
										} catch (NumberFormatException e) {											
											e.printStackTrace();
											gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
													"LaunchActivity/instmtSetup: parseInt error for loNote in info.txt");
										}
										
										try {
											gv.instruments.get(i).samples[j].hiNote = Integer.parseInt(elements[6]);
											//System.out.println("hiNote " + gv.instruments.get(i).samples[j].hiNote);
										} catch (NumberFormatException e) {											
											e.printStackTrace();
											gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
													"LaunchActivity/instmtSetup: parseInt error for hiNote in info.txt");
										}
										
										break;
									}
								}
								if (!found) {
									gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
										"LaunchActivity/instmtSetup: no match found for sample file name in info.txt");
								}								
							}
							else {
								gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
										"LaunchActivity/instmtSetup: sample line has wrong number elements in info.txt");
								//System.out.println(gv.instruments.get(i).status);								
							}															
						}							
				    }
					textReader.close();
					fileReader.close();	//close info.txt file					
					
					//verify samplerate was set
					if (gv.instruments.get(i).sampleRate == -1) {
						gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
								"LaunchActivity/instmtSetup: sampleRate was not set");
						//System.out.println(gv.instruments.get(i).status);
					}
					
					//verify info.txt file contains same # sample lines as samples in instmt samples dir
					if (sampleCt == gv.instruments.get(i).samples.length) {
						//System.out.println("LaunchActivity/instmtSetup: sample count match verified");
					}
					else {
						gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
								"LaunchActivity/instmtSetup: # sample lines in info.txt does not match # samples in dir");
						//System.out.println(gv.instruments.get(i).status);
					}
					
				} 
				catch (IOException e) {	
					e.printStackTrace();				
					gv.instruments.get(i).status = setupError(gv.instruments.get(i).status,
							"LaunchActivity/instmtSetup: error opening/reading info.txt file");
					//System.out.println(gv.instruments.get(i).status);				
				} 	    		
	    	}  	    	
			
			return success; //TODO not really used
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			
			spinner.setVisibility(View.GONE);		    
		    splashTV.setVisibility(View.GONE);
		    
		    //go through instruments and remove any that had errors
		    List<Integer> badInstmts = new ArrayList<Integer>(); //index #'s bad instruments 
		    for (int i=0; i < gv.instruments.size(); i++) {
		    	if (gv.instruments.get(i).status.contentEquals("OK")) {
		    		System.out.println(gv.instruments.get(i).instmtNum + " " + gv.instruments.get(i).name + " OK");
		    	}
		    	else {
		    		System.out.println(gv.instruments.get(i).instmtNum + " " + gv.instruments.get(i).name + " has problems");
		    		System.out.println(gv.instruments.get(i).status);
		    		badInstmts.add(i);		    		
		    	}
		    }
		    
		    for (int i=0; i < badInstmts.size(); i++) {
			   badInstmt(badInstmts.get(i));
		    }
		    
		    new drumSetup().execute();
		}
	}
	
	private void badInstmt(Integer index) { //remove instruments from master list that did not load properly
		//this might happen when users create/share custom instruments.. badly
		dialogsOpen++; //increment var making sure all dialogs are acknowledged before leaving Activity
		
		final Dialog dialog = new Dialog(this);     	
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	dialog.setContentView(R.layout.alertdlg);
    	
    	int inum = gv.instruments.get(index).instmtNum;
    	String iname = gv.instruments.get(index).name; 
    	String status = gv.instruments.get(index).status;
    	
    	TextView title = (TextView) dialog.findViewById(R.id.alertTitleTV);
    	title.setText("Instrument Initialization Failed    "); 
    	
    	TextView msg = (TextView) dialog.findViewById(R.id.alertMsgTV);    	
    	msg.setText("There was a problem loading instrument: " + inum + ". " + iname + ". " +
    			" Error: " + status + " . This instrument has been disabled.");    	
    	
    	Button okBtn = (Button) dialog.findViewById(R.id.alertOKbtn);
    	
    	gv.instruments.remove(index); //remove the bad instrument from master list
    	
    	okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {		
				
				dialogsOpen--;				
				dialog.dismiss();	
				
				if (lvlCheck && dialogsOpen == 0) {
					gotoNext();
				}				
			}
		});
    	
    	dialog.show();		
	}
	
	private class drumSetup extends AsyncTask<Void, Void, Boolean> {
		
		ProgressBar spinner = (ProgressBar) findViewById(R.id.splashPB);
		TextView splashTV = (TextView) findViewById(R.id.splashTV);
		
		@Override
		protected void onPreExecute(){
			System.out.println("LaunchActivity/drumSetup()");
		    spinner.setVisibility(View.VISIBLE);
		    splashTV.setText("Initializing Drums");
		    splashTV.setVisibility(View.VISIBLE);
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			boolean success = true;
			
			gv.drums = new ArrayList<Drums>();
			
			//set up master drums list by reading the drums dirs directly
			String path = gv.appPath + "/dittybot/drums/base_drums/";
	    	File drumsDir = new File(path);
	    	if (drumsDir.isDirectory()) {
	    		System.out.println("drumsDir OK");
	    		String delimiter = "\\.";
	    		String[] elements;
	    		
	    		String[] drumDirs = drumsDir.list();
	    		
	    		//set the drumNum and name by splitting the dir label
	    		for (int i=0; i < drumDirs.length; i++) { 	    			
	    			//Drums drum = new Drums();
	    			gv.drums.add(new Drums());
	    			elements = drumDirs[i].split(delimiter); 
	    			try {
	    				gv.drums.get(i).drumNum = Integer.parseInt(elements[0]);
					} catch (NumberFormatException e) {						
						e.printStackTrace();
						gv.drums.get(i).status = setupError(gv.drums.get(i).status, 
    							"LaunchActivity/drumSetup: parseInt error reading drumNum");	    						    					
    					//System.out.println(gv.drums.get(i).status);
					}
	    			gv.drums.get(i).name = elements[1];	
	    			
	    			//grab the sample file name from the drum dir
	    			String drumPath = path + drumDirs[i];	    			
	    			File drumDir = new File(drumPath);
	    			if (drumDir.isDirectory()) {
	    				String[] drumFiles = drumDir.list(); //list the files in the specific drum directory
	    				boolean found = false;
	    				for (int j=0; j < drumFiles.length; j++) {
	    					elements = drumFiles[j].split(delimiter);	    					
	    					if (elements[1].contentEquals("wav")) {
	    						found = true;
	    						gv.drums.get(i).fileName = drumFiles[j];	
	    						break;
	    						//System.out.println("drum.fileName " + gv.drums.get(i).fileName);
	    					}	    						    					
	    				}
	    				if(!found) {
	    					gv.drums.get(i).status = setupError(gv.drums.get(i).status, 
	    							"LaunchActivity/drumSetup: no sample file was found");
	    					//System.out.println(gv.drums.get(i).status);
	    				}
	    			}	    			
	    		}		    		 
	    	}
	    	else {
	    		System.out.println("LaunchActivity/drumSetup: problem with drums/base_drums directory");
	    		//TODO handle error. A big one
	    		success = false;
	    		return success;
	    	}	    	
	    	
	    	//read each drum's info.txt file info in and verify
	    	for (int i=0; i < gv.drums.size(); i++) {
	    		String dirName = gv.drums.get(i).drumNum + "." + gv.drums.get(i).name; //use master list info
	    		String infoPath = gv.appPath + "/dittybot/drums/base_drums/" + dirName + "/info.txt";
	    		File info = new File(infoPath);
				String textLine = null;
				try {
					FileReader fileReader = new FileReader(info);
					BufferedReader textReader = new BufferedReader(fileReader);			
					while ((textLine = textReader.readLine()) != null ) {							
						String delimiter = "\\|";
						String[] elements = textLine.split(delimiter);
						if (elements[0].contentEquals("name")) { //verify name in info.txt matches the name in dir
							if (elements[1].contentEquals(gv.drums.get(i).name)) {
								//System.out.println("LaunchActivity/drumSetup: name match verified");
							}
							else {
								gv.drums.get(i).status = setupError(gv.drums.get(i).status, 
										"LaunchActivity/drumSetup: name mismatch in info.txt file");
								//System.out.println(gv.drums.get(i).status);								
							}
						}
						if (elements[0].contentEquals("dnum")) {
							if (Integer.parseInt(elements[1]) == gv.drums.get(i).drumNum) {
								//System.out.println("LaunchActivity/drumSetup: drumNum match verified");
							}
							else {
								gv.drums.get(i).status = setupError(gv.drums.get(i).status,
										"LaunchActivity/drumSetup: drumNum mismatch in info.txt file");
								//System.out.println(gv.drums.get(i).status);
							}														
						}
						if (elements[0].contentEquals("samplerate")) {								
							try {
								gv.drums.get(i).sampleRate = Integer.parseInt(elements[1]);
							} catch (NumberFormatException e) {								
								e.printStackTrace();
								gv.drums.get(i).status = setupError(gv.drums.get(i).status,
										"LaunchActivity/drumSetup: parseInt failed on sampleRate in info.txt");
								//System.out.println(gv.drums.get(i).status);
							}								
						}
						if (elements[0].contentEquals("sample")) {
							//verify the sample line has correct # elements
							if (elements.length == 3) { //TODO make a var instead of hardcoded #
								if (elements[1].contentEquals(gv.drums.get(i).fileName)) {
									//System.out.println("LaunchActivity/drumSetup: fileName match verified");
									try {
										gv.drums.get(i).numSamples = Integer.parseInt(elements[2]);
									} catch (NumberFormatException e) {											
										e.printStackTrace();
										gv.drums.get(i).status = setupError(gv.drums.get(i).status,
											"LaunchActivity/drumSetup: parseInt error for numSamples in info.txt");
										//System.out.println(gv.drums.get(i).status);
									}
								}
								else {
									gv.drums.get(i).status = setupError(gv.drums.get(i).status,
										"LaunchActivity/drumSetup: file name mismatch in info.txt");
									//System.out.println(gv.drums.get(i).status);
								}
							}
							else {
								gv.drums.get(i).status = setupError(gv.drums.get(i).status,
										"LaunchActivity/drumSetup: sample line has wrong number elements in info.txt");
								//System.out.println(gv.drums.get(i).status);								
							}
						}
					}					
				}
				catch (IOException e) {	
					e.printStackTrace();				
					gv.drums.get(i).status = setupError(gv.drums.get(i).status,
							"LaunchActivity/drumSetup: error opening/reading info.txt file");
					//System.out.println(gv.drums.get(i).status);				
				} 
	    	}
			
			return success;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			
			ProgressBar spinner = (ProgressBar) findViewById(R.id.splashPB);
		    spinner.setVisibility(View.GONE);
		    
		    for (int i=0; i < gv.drums.size(); i++) {
		    	if (gv.drums.get(i).status.contentEquals("OK")) {
		    		System.out.println(gv.drums.get(i).drumNum + " " + gv.drums.get(i).name + " OK");
		    	}
		    	else {
		    		System.out.println(gv.drums.get(i).drumNum + " " + gv.drums.get(i).name + " has problems");
		    		System.out.println(gv.drums.get(i).status);
		    		//TODO alert user. don't allow drum
		    	}
		    }	
		    
		    
		    //go through instruments and remove any that had errors
		    List<Integer> badDrums = new ArrayList<Integer>(); //index #'s bad instruments 
		    for (int i=0; i < gv.drums.size(); i++) {
		    	if (gv.drums.get(i).status.contentEquals("OK")) {
		    		System.out.println(gv.drums.get(i).drumNum + " " + gv.drums.get(i).name + " OK");
		    	}
		    	else {
		    		System.out.println(gv.drums.get(i).drumNum + " " + gv.drums.get(i).name + " has problems");
		    		System.out.println(gv.drums.get(i).status);
		    		badDrums.add(i);		    		
		    	}
		    }
		    
		    for (int i=0; i < badDrums.size(); i++) { //loop over and remove all bad drums from master list
			   badDrum(badDrums.get(i));
		    }
		    		    
			new getNoteNames().execute();
		}
	}
	
	private void badDrum(Integer index) { //remove instruments from master list that did not load properly
		//this might happen when users create/share custom instruments.. badly
		dialogsOpen++; //increment var making sure all dialogs are acknowledged before leaving Activity
		
		final Dialog dialog = new Dialog(this);     	
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	dialog.setContentView(R.layout.alertdlg);
    	
    	int dnum = gv.drums.get(index).drumNum;
    	String dname = gv.drums.get(index).name; 
    	String status = gv.drums.get(index).status;
    	
    	TextView title = (TextView) dialog.findViewById(R.id.alertTitleTV);
    	title.setText("Drum Initialization Failed    "); 
    	
    	TextView msg = (TextView) dialog.findViewById(R.id.alertMsgTV);    	
    	msg.setText("There was a problem loading drum: " + dnum + ". " + dname + ". " +
    			" Error: " + status + " . This drum has been disabled.");    	
    	
    	Button okBtn = (Button) dialog.findViewById(R.id.alertOKbtn);
    	
    	gv.instruments.remove(index); //remove the bad instrument from master list
    	
    	okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {		
				
				dialogsOpen--;				
				dialog.dismiss();	
				
				if (lvlCheck && dialogsOpen == 0) {
					gotoNext();
				}				
			}
		});
    	
    	dialog.show();		
	}
	
	private class getNoteNames extends AsyncTask<Void, Void, Boolean> {	//the A-G #/flat & octave # notation 0-127
		
		ProgressBar spinner = (ProgressBar) findViewById(R.id.splashPB);
		TextView splashTV = (TextView) findViewById(R.id.splashTV);
		
		@Override
		protected void onPreExecute(){
			System.out.println("LaunchActivity/getNoteNames()");
			spinner.setVisibility(View.VISIBLE);
		    splashTV.setText("Loading Notation");
		    splashTV.setVisibility(View.VISIBLE);
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			boolean success = true;
			
	    	gv.noteNames = new String[128];
	    	String path = getFilesDir() + "/dittybot/notetabzb.txt";
	    	File notefile = new File(path);
	    	
	    	String textLine = null;
	    	int index = 0;
			try {
				FileReader fileReader = new FileReader(notefile);
				BufferedReader textReader = new BufferedReader(fileReader);			
				while ((textLine = textReader.readLine()) != null ) {				
					gv.noteNames[index] = textLine;
					index++;
			    }
				textReader.close();
				fileReader.close();			
			}
			catch(IOException e) {
				e.printStackTrace();
				System.out.println("fileReader error in getNoteNames()");
				success = false;
				//TODO error handling			
			}	
			
			return success;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {			
			
			spinner.setVisibility(View.GONE);		    
		    splashTV.setVisibility(View.GONE);
		    
		    //checkLicense();
		    licenseUI();
		}
	}
	
	private String setupError(String statusStr, String errorStr) { //handle adding error strings to status vars
		String newStatus = null;
		if (statusStr.contentEquals("OK")) { //then first error so just overwrite the "OK"			
			newStatus = errorStr;
		}
		else { //then status contains multiple error messages so pipe delimit and add to list
			newStatus = statusStr + "|" + errorStr; 
		}
		return newStatus;
	}
    
    private String readAppIdFile(File appIDfile) throws IOException {
    	System.out.println("readAppIdFile()");
    	RandomAccessFile f = new RandomAccessFile(appIDfile, "r");
    	byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return new String(bytes);
    }
    
	private void deleteAllFiles(File fileOrDirectory) { //development aid to 86 files easily
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
            	deleteAllFiles(child); //recursive to drill into subdirs

        fileOrDirectory.delete();
    }
	
	private void gotoNext() { //once everything clears go to the next Activity			 
		Intent intent = new Intent(this, MainMenu.class); 
		startActivity(intent);
	}
	
	private void licenseUI() {    	
    	System.out.println("licenseUI()");
    	ProgressBar spinner = (ProgressBar) findViewById(R.id.splashPB);
		TextView splashTV = (TextView) findViewById(R.id.splashTV);
		spinner.setVisibility(View.VISIBLE);
	    splashTV.setText("Verifying License");
	    splashTV.setVisibility(View.VISIBLE);
	    
	    checkLicense();
	}
	
    private void checkLicense() {    	
    	System.out.println("checkLicense()");    	
    	mChecker.checkAccess(mLicenseCheckerCallback); //calls back when check done on licensing server	    	
    }
    
	private void licenseErrorDlg(int errcode) { //called by MyLicenseCheckerCallback if license check fails 
		System.out.println("licenseDlg() " + errcode);
		//TODO not doing anything with the error code in this. maybe log it or inform user in more detail
		
		dialogsOpen++;
		
		final Dialog dialog = new Dialog(this);     	
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	dialog.setContentView(R.layout.alertdlg);
    	
    	TextView title = (TextView) dialog.findViewById(R.id.alertTitleTV);
    	title.setText("App License Verification Failed    "); 
    	
    	TextView msg = (TextView) dialog.findViewById(R.id.alertMsgTV);    	
    	msg.setText("The license for this app could not be verified. " +
				"The app will still function on your device, but you will not have access " +
				"to the DittyBot Network or advanced features until the license can be verified.");    	
    	
    	Button okBtn = (Button) dialog.findViewById(R.id.alertOKbtn);
    	
    	okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
				
				dialogsOpen--;
				if (lvlCheck && dialogsOpen == 0) { //lvlCheck would have to be true by default if here, but check anyway
					if (gv.firstRun) System.out.println("FIRST RUN!"); //TODO need to figure out when/where to handle this
					gotoNext();
				}								
			}
		});
    	
    	dialog.show();
	}
	
	//-------------------------- inner class for licensing callback ---------------------------    
    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
    	
    	public void allow(int policyReason) {
    		if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }  
    		System.out.println("LVL allow: " + policyReason);   
    		
    		lvlCheck = true;
    		gv.licenseOK = true;
    		
    		if (lvlCheck && dialogsOpen == 0) {
				gotoNext();
			}  		
    	}
    	
    	public void dontAllow(int policyReason) {
    		if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }  
    		System.out.println("LVL don't allow: " + policyReason);   		
    		
    		//check if returned code is retry
    		if (policyReason == 291) {
    			retry_count++;
    			if (retry_count <= MAX_RETRIES) {
    				checkLicense();
    			}
    			else {
    				System.out.println("reached max license verification tries in LaunchActivity");
    				lvlCheck = true;
    				gv.licenseOK = false;
    				licenseErrorDlg(policyReason);
    			}
    		}
    		else {
    			lvlCheck = true;
    			gv.licenseOK = false;
    			licenseErrorDlg(policyReason);
    		}
    		
    	}
    	
    	public void applicationError(int errorCode) {
    		/**
    		 * not exactly sure what this is for. I think it covers things like
    		 * not granting required permissions in AndroidManifest
    		 */    		
    		
    		if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }  
    		System.out.println("applicationError(): " + errorCode);
    		
    		lvlCheck = true;
    		gv.licenseOK = false;
    		
    		licenseErrorDlg(errorCode);    		
    	}
    }
}
