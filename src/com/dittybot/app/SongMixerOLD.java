package com.dittybot.app;

/**
 * renamed to save. Contains the structure to use a playLine that would traverse to midscreen
 * when playbtn pressed, and then remain at midscreen while body scrollview passes under
 * The logic got overcomplex for something I thought was likely to be confusing to users anyway
 */

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.puredata.core.PdBase;
import org.puredata.core.PdListener;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
//import android.view.ViewTreeObserver;
//import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class SongMixerOLD extends Activity implements MaxScrollListener, ScrollViewListener {
	
	private GlobalVars gv;	
	
	private Song song = null; //the single song instance to work with
	private boolean songLoaded = false;
	private boolean firstLoad = true; //flag when song first loaded so can reset playLine etc.
	
	private List<Imod> imods = new ArrayList<Imod>(); //data about loaded imod.pd's
	
	private Midi midi = null;
	
	private int dfltSongNum = 0; //used for auto-generating numbered file names for new songs 
	
	private List<Integer> trackUiList; //keeps track of order to display tracks on screen. track type/ID# stride 2	
	
	private TextView titleTV;
	private TextView timeTV;	
	
	private ObservableScrollView sideSV = null; //custom scrollview that gives more info than stock one
	private LinearLayout iTracksLL; 
	private LinearLayout dTracksLL;
	private LinearLayout aTracksLL; 
	public static MaxScrollView bodySV = null; //custom 2D scrollview
	private RelativeLayout bodyRL = null; //inside bodySV. holds note/score graphics
	
	private float zoom_factor = 5; //zoom in & out on note data
	
	private float TRKSIZE_DP = 60; //h/w of tracks ui, pic dimensions in device independent pixels (dp)
	private int trkSize_px; //h/w of tracks ui, pic dimensions in actual pixels on screen 	
	
	private PlayLine playLine; //scrubber line shows playback progress in song 
	private PlayLine staticLine; //jitter-free stand-in for playLine at midscreen
	
	private ImageView playBtn;	
	private int playState = 0;	//0 = not playing, 1 = playing, 2 = at end of clip
	float playTime = 0; //current time song is cued to in milliseconds
	float startTime; //used with endTime to get playTime time with System.nanoTime()	
	float endTime;
	float currTime; //used in updateUI() to animate UI components
	float prevTime;	
	int lastImodID = -1; //ID of the imod patch that ends latest/last
	
	private ImageView rewBtn;
	private Button dbotBtn;
	private Button songsBtn;
	private Button netBtn;
	private Button helpBtn;
	private Button midiBtn;
	private Button instmtBtn;
	private Button drumsBtn;
	private Button addTrkBtn;
	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	System.out.println("onCreate() MainScreen dB1.0");
        super.onCreate(savedInstanceState);        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //TODO make settable
        
        gv = ((GlobalVars)getApplicationContext());    	
		
		initGui();
		
		songsDialog(); //on start prompt user to load a song
		/*
		if (!songLoaded) { //prompt user to load a song on startup
			songsDialog();
		}
		*/         
	}	
	
	private void initGui() {		
		
		setContentView(R.layout.songmixer);
		
		//convert set-sized items from dp to pixels to create UI programmatically		
		trkSize_px = (int) (TRKSIZE_DP * this.getResources().getDisplayMetrics().density + 0.5f);
		
		
		trackUiList = new ArrayList<Integer>();
		
		
		titleTV = (TextView) findViewById(R.id.smTitle);
		titleTV.setText("Song Title");
		
		timeTV = (TextView) findViewById(R.id.smRunTimeTV);
		timeTV.setText("Time:");
		
		//add track type UI headers' gradient rect backgrounds
		int[] rcolors = {0xFF7F0000, 0xFF222222};		
		GradientDrawable rrect = new GradientDrawable(Orientation.TOP_BOTTOM, rcolors);		
		RelativeLayout iTrkHdrLL = (RelativeLayout) findViewById(R.id.smIHdrRL);
		iTrkHdrLL.setBackgroundDrawable(rrect);		
				
		int[] gcolors = {0xFF209040, 0xFF222222};
		GradientDrawable grect = new GradientDrawable(Orientation.TOP_BOTTOM, gcolors);		
		RelativeLayout dTrkHdrLL = (RelativeLayout) findViewById(R.id.smDHdrRL);
		dTrkHdrLL.setBackgroundDrawable(grect);		
		
		int[] bcolors = {0xFF2040A0, 0xFF222222};
		GradientDrawable brect = new GradientDrawable(Orientation.TOP_BOTTOM, bcolors);		
		RelativeLayout aTrkHdrLL = (RelativeLayout) findViewById(R.id.smAHdrRL);
		aTrkHdrLL.setBackgroundDrawable(brect);
		
		sideSV = (ObservableScrollView) findViewById(R.id.smOSV);
		sideSV.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//System.out.println("sideSV " + event);
				//bodySV.getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
			
		});
		
		
		
		bodySV = (MaxScrollView) findViewById(R.id.smMSV);
		
		bodySV.setOnTouchListener(new OnTouchListener() {			
			public boolean onTouch(View v, MotionEvent event) {
				v.getParent().getParent().requestDisallowInterceptTouchEvent(true); //prevent sideSV from taking touch event
				
				float thisY = event.getY();
				
				switch (event.getAction()){
				case MotionEvent.ACTION_DOWN:
					//System.out.println("bodySV ACTION_DOWN");
					//bodySV.removeScrollViewListener(MainScreen.this);
					bodySV.setTag(thisY); //hacky way to store a previous y value ;-)
					break;
				case MotionEvent.ACTION_MOVE:
					//System.out.println("bodySV ACTION_MOVE");
					float prevY = (Float) bodySV.getTag();
					float diffY = prevY - thisY;					
					//System.out.println("diffY " + diffY);					
					bodySV.setTag(thisY + diffY);
					sideSV.scrollBy(0, (int)diffY);					
					break;
				case MotionEvent.ACTION_UP:
					//System.out.println("bodySV ACTION_UP");
					//bodySV.setScrollViewListener(MainScreen.this);
					break;
					
				}				
				
				return false;
			}
			
		});
		
		bodyRL = (RelativeLayout) findViewById(R.id.smBodyRL);	
		
		bodyRL.setOnTouchListener(new OnTouchListener() {			
			public boolean onTouch(View v, MotionEvent event) {
				System.out.println("bodyRL touch " + event.getAction());
				switch (event.getAction()){
				case MotionEvent.ACTION_DOWN:
					System.out.println("ACTION_DOWN");
					//sideScroll(false);
					//bodyScroll(true);	
					//full_y = event.getY(); //done globally as longclick has no getY method					
					setPlayline(event.getX());									
					break;				
				}
				return false;   
			}
		});	
		
		
		iTracksLL = (LinearLayout) findViewById(R.id.smITracksLL);
		dTracksLL = (LinearLayout) findViewById(R.id.smDTracksLL);
		aTracksLL = (LinearLayout) findViewById(R.id.smATracksLL);		
		
		
		playBtn = (ImageView) findViewById(R.id.smPlayIV);
		playBtn.setOnClickListener(onClickListener);		
		
		rewBtn = (ImageView) findViewById(R.id.smRewIV);
		rewBtn.setOnClickListener(onClickListener);			
		
		dbotBtn = (Button) findViewById(R.id.smDittyBotBtn);
		dbotBtn.setOnClickListener(onClickListener);
		
		songsBtn = (Button) findViewById(R.id.smSongsBtn);
		songsBtn.setOnClickListener(onClickListener);
		
		netBtn = (Button) findViewById(R.id.smNetworkBtn);
		netBtn.setOnClickListener(onClickListener);
		
		helpBtn = (Button) findViewById(R.id.smHelpBtn);
		helpBtn.setOnClickListener(onClickListener);
		
		midiBtn = (Button) findViewById(R.id.smMidiBtn);
		midiBtn.setOnClickListener(onClickListener);
		
		instmtBtn = (Button) findViewById(R.id.smInstmtBtn);
		instmtBtn.setOnClickListener(onClickListener);
		
		drumsBtn = (Button) findViewById(R.id.smDrumsBtn);
		drumsBtn.setOnClickListener(onClickListener);
		
	}
	
	private void initGuiOLD() {
		
		//setContentView(R.layout.mainscreen);
		setContentView(R.layout.songmixer);
		
		//draw a rounded gradient rectangle background for songCtrl header
		int[] colors = {0xFF444444, 0xFF111111};
		GradientDrawable rrect = new GradientDrawable(Orientation.TOP_BOTTOM, colors);		
		rrect.setCornerRadius(5);		
		//LinearLayout sCtrlHdrLL = (LinearLayout) findViewById(R.id.sCtrlHdrLL);
		//sCtrlHdrLL.setBackgroundDrawable(rrect);
		
		trackUiList = new ArrayList<Integer>(); 
		
		//initialize the controls bar on left side of main screen		
		sideSV = (ObservableScrollView) findViewById(R.id.smOSV);
		sideSV.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//System.out.println("sideSV " + event);
				//bodySV.getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
			
		});
		
		iTracksLL = (LinearLayout) findViewById(R.id.smITracksLL);
		//sideLL = new LinearLayout(this);
		//sideLL.setOrientation(LinearLayout.VERTICAL);		
		//sideSV.addView(sideLL); //to stack track controls, etc. in			
		
		staticLine = new PlayLine(this, gv.screenWidth, gv.screenHeight, trkSize_px);	
		staticLine.isStatic = true;
		RelativeLayout.LayoutParams line_params = 
				new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		line_params.addRule(RelativeLayout.ABOVE, R.id.smPlayBtnsLL);
		line_params.addRule(RelativeLayout.BELOW, R.id.smTitle);		
		staticLine.setLayoutParams(line_params);
		staticLine.setVisibility(View.GONE);
		//RelativeLayout mainRL = (RelativeLayout) findViewById(R.id.mainRL);
		RelativeLayout mainRL = (RelativeLayout) findViewById(R.id.smRL);
		mainRL.addView(staticLine);
		
		/*
		sideSV.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//can use to "hold" screen w/ left thumb while doing ops on noteblocks
				switch (event.getAction()){
				case MotionEvent.ACTION_DOWN:
					//System.out.println("mainOSV ACTION_DOWN");
					if (playState != 1) {						
						//noteBodyScroll(false);	
						//noteBarScroll(true);
						//onNoteBar = true;
					}
					else return true; // "locks out" noteBar while playing
				break;
				case MotionEvent.ACTION_UP:					
					//System.out.println("mainOSV ACTION_UP");
					//onNoteBar = false;
				break;
				//case MotionEvent.ACTION_POINTER_2_DOWN:
										
				//break;	
				}
				
				return false;
			}			
		});	
		*/
		//bodySV = (MaxScrollView) findViewById(R.id.mainMSV);
		bodySV = (MaxScrollView) findViewById(R.id.smMSV);
		//bodySV.setScrollViewListener(this); //fires the onScrollChanged listener when set			
		bodySV.setOnTouchListener(new OnTouchListener() {			
			public boolean onTouch(View v, MotionEvent event) {
				v.getParent().getParent().requestDisallowInterceptTouchEvent(true); //prevent sideSV from taking touch event
				
				float thisY = event.getY();
				
				switch (event.getAction()){
				case MotionEvent.ACTION_DOWN:
					//System.out.println("bodySV ACTION_DOWN");
					//bodySV.removeScrollViewListener(MainScreen.this);
					bodySV.setTag(thisY); //hacky way to store a previous y value ;-)
					break;
				case MotionEvent.ACTION_MOVE:
					//System.out.println("bodySV ACTION_MOVE");
					float prevY = (Float) bodySV.getTag();
					float diffY = prevY - thisY;					
					//System.out.println("diffY " + diffY);					
					bodySV.setTag(thisY + diffY);
					sideSV.scrollBy(0, (int)diffY);					
					break;
				case MotionEvent.ACTION_UP:
					//System.out.println("bodySV ACTION_UP");
					//bodySV.setScrollViewListener(MainScreen.this);
					break;
					
				}				
				
				return false;
			}
			
		});
		
		bodyRL = (RelativeLayout) findViewById(R.id.smBodyRL);	
		
		bodyRL.setOnTouchListener(new OnTouchListener() {			
			public boolean onTouch(View v, MotionEvent event) {
				//System.out.println("bodyRL touch " + event.getAction());
				switch (event.getAction()){
				case MotionEvent.ACTION_DOWN:
					//sideScroll(false);
					//bodyScroll(true);	
					//full_y = event.getY(); //done globally as longclick has no getY method					
					//setPlayhead(event.getX());									
					break;				
				}
				return false;   
			}
		});	
		/*
		bodyRL.setOnLongClickListener(new View.OnLongClickListener() {			
			public boolean onLongClick(View v) {
				
				if (!inMultiTouch) {
					noteGuideShow(full_y);
					int start = ticks * gv.DFLT_TICK; //ticks was set in ACTION_DOWN by setPlayHead() 
					int note = noteFromY(full_y); //full_y set in ACTION_DOWN event
					int dur = dfltNoteSize; //TODO give a dropdown to choose 1/16th, 1/8th, 1/4 etc
					noteAddVerify(start, note, dur);
				}	
						
				return true;
			}
		});
		*/		
		
		//titleTV = (TextView) findViewById(R.id.mainTitle);
		titleTV = (TextView) findViewById(R.id.smTitle);
		titleTV.setText("Song Title");
		//timeTV = (TextView) findViewById(R.id.mainTimeTV);
		timeTV = (TextView) findViewById(R.id.smRunTimeTV);
		timeTV.setText("Time:");
		
		//playBtn = (ImageView) findViewById(R.id.mainPlayIV);
		playBtn = (ImageView) findViewById(R.id.smPlayIV);
		playBtn.setOnClickListener(onClickListener);
		
		//rewBtn = (ImageView) findViewById(R.id.mainRewIV);
		rewBtn = (ImageView) findViewById(R.id.smRewIV);
		rewBtn.setOnClickListener(onClickListener);
		
		//ffBtn = (ImageView) findViewById(R.id.mainFFIV);
		//ffBtn = (ImageView) findViewById(R.id.smFFIV);
		//ffBtn.setOnClickListener(onClickListener);		
		
		dbotBtn = (Button) findViewById(R.id.smDittyBotBtn);
		dbotBtn.setOnClickListener(onClickListener);
		
		songsBtn = (Button) findViewById(R.id.smSongsBtn);
		songsBtn.setOnClickListener(onClickListener);
		
		netBtn = (Button) findViewById(R.id.smNetworkBtn);
		netBtn.setOnClickListener(onClickListener);
		
		helpBtn = (Button) findViewById(R.id.smHelpBtn);
		helpBtn.setOnClickListener(onClickListener);
		
		midiBtn = (Button) findViewById(R.id.smMidiBtn);
		midiBtn.setOnClickListener(onClickListener);
		
		instmtBtn = (Button) findViewById(R.id.smInstmtBtn);
		instmtBtn.setOnClickListener(onClickListener);
		
		drumsBtn = (Button) findViewById(R.id.smDrumsBtn);
		drumsBtn.setOnClickListener(onClickListener);
		
		//addTrkBtn = (Button) findViewById(R.id.sCtrlAddTrkBtn);
		addTrkBtn.setOnClickListener(onClickListener);
		
	}
	
	public OnClickListener onClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.smPlayIV:			
				playCtrl();
				break;
			case R.id.smRewIV:
				rewind();
				break;
			/*
			case R.id.smFFIV:
				//ffRiff();
				break;
			*/
			case R.id.smDittyBotBtn:			
				//dittyBot();
				break;
			case R.id.smSongsBtn:
				songsDialog();
				break;
			case R.id.smNetworkBtn:
				//network();
				break;
			case R.id.smHelpBtn:
				//helpDialog();
				break;
			case R.id.smMidiBtn:
				midiChooser();
				break;
			case R.id.smInstmtBtn:
				//instmtChooser(); 
				break;
			case R.id.smDrumsBtn:
				//drumChooser(); 
				break;
			/*
			case R.id.sCtrlAddTrkBtn:
				trackAddDialog();
				break;
			*/
			}
		}		
	};
	
//==== SONG FUNCTIONS ==============================================================================================
	
	private void songsDialog() { //UI to load new or existing song
		System.out.println("songsDialog()");
		
		final Dialog dialog = new Dialog(this); 
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
    	dialog.setContentView(R.layout.radiobtns);
    	
    	ImageView iv = (ImageView) dialog.findViewById(R.id.radioBtnsIV);
		iv.setImageResource(R.drawable.scofile48);
		
		TextView tv = (TextView) dialog.findViewById(R.id.radioBtnsTV);
		tv.setText("Choose a Song Option:  ");
		
		final RadioGroup rg = (RadioGroup) dialog.findViewById(R.id.radioBtnsRG);
		
		RadioButton editRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB1);
		editRB.setText("  Edit Song Properties"); //necessary??
		
		RadioButton savedRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB2);
		savedRB.setText("  Load a saved Song");
		
		RadioButton newRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB3);
		newRB.setText("  Create a new Song");
		
		if (!songLoaded) {
			editRB.setVisibility(View.GONE);			
			savedRB.setChecked(true);
		}
		else {
			editRB.setChecked(true);
		}		
		
		Button okBtn = (Button) dialog.findViewById(R.id.radioBtnsOK);
		Button cancelBtn = (Button) dialog.findViewById(R.id.radioBtnsCncl);			
		
		okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				switch (rg.getCheckedRadioButtonId()) {
				case R.id.radioBtnsRB1:					
					dialog.dismiss();
					songEdit();
					break;
		    	case R.id.radioBtnsRB2:		    		
		    		dialog.dismiss();
		    		songChooser();
		    		break;
		    	case R.id.radioBtnsRB3:		    		
		    		dialog.dismiss();
		    		songNew();		    		
		    	}						
			}    		
    	});  
		
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}						
		});
		
		dialog.show();
	}
	
	private void songEdit() {
		
	}
	
	private void songChooser() {
		System.out.println("songChooser()");
		
		fileChooser("Songs", new PromptRunnable() { //Songs is directory name
			
			public void run() {
				//get the returned file name
				String filename = this.getValue();
				//put the code here to run when the selection is made				
				new songLoad().execute(filename); //AsyncTask
				
			}
			
		});
	}
	
	private void songNew() {
		
	}
	
	public class songLoad extends AsyncTask <String, Integer, String> {		
		
		@Override
		protected void onPreExecute() { 
			super.onPreExecute();
			
			trackUiList.clear(); //clear out the track display list for new song
			
			ProgressBar spinner = (ProgressBar) findViewById(R.id.smProgSpnr);
		    spinner.setVisibility(View.VISIBLE);			
		}
		
		@Override
		protected String doInBackground(String... params) {
			System.out.println("songLoad " + params[0]); //the passed in file name
			
			song = new Song(); //re-initialize for new song coming in
			
			String fileName = params[0];			
			String filePath = gv.extStorPath + "/DittyBot/Songs/" + fileName;
			
			File song_file = new File(filePath); //TODO maybe verify file exists etc.
			
			//open the file			
			try {
				RandomAccessFile in = new RandomAccessFile(song_file, "r"); //open in read mode
								
				//--------- Song Info -------------------------------------
				//check for the DBSF magic seq
				String dbsf = "";
				for (int i=0; i < 4; i++) {	        	
		        	char c = (char) in.read();
		        	dbsf += c;	        	
		        }			
				if (!dbsf.contentEquals("DBSF")) {
					//TODO error_message 
					return null; //kick out of processing the file
				}
				System.out.println("songLoad " + dbsf + " seq OK");
				
				//read 1 byte song.fileName length field
				int nl = in.read();
				System.out.println("song name length: "+ nl);
				
				//read nl # bytes in & convert back to a song name string 
				String name = "";
				for (int i=0; i < nl; i++) {
					char c = (char) in.read();
					name += c;
				}
				song.name = name;
				System.out.println("song name: " + song.name);
				
				//read 1 byte song.info length field
				int il = in.read();
				System.out.println("song info length: "+ il);
				
				//read il # bytes in & convert back to song info string
				String info = "";
				for (int i=0; i < il; i++) {
					char c = (char) in.read();
					info += c;
				}
				song.info = info;
				System.out.println("song info: " + song.info);
				
				//read a double (8 bytes) for song.tempo
				song.tempo = in.readDouble();
				System.out.println("tempo " + song.tempo);
				
				//------------ Tracks Data ---------------------------------------------
				
				while (in.getFilePointer() < in.length()) {
					
					//find/verify DBTK magic seq for track begin					
					String dbtk = "";
					for (int i=0; i < 4; i++) {	        	
			        	char c = (char) in.read();
			        	dbtk += c;	        	
			        }			
					if (!dbtk.contentEquals("DBTK")) {
						//TODO handle error
						return null; //kick out of processing the file
					}
					System.out.println("songLoad() " + dbtk + " seq OK");
									
					//1 byte track type 0=instmt, 1=audio, 2=percussion
					int type = in.read();
					System.out.println("track type: " + type);					
										
					//1 byte to tell how many bytes to read for track.info 
					int tib = in.read();
					System.out.println("# track info bytes: " + tib);
					
					//read track info ASCII byte chars
					String trackinfo = "";
					for (int i=0; i < tib; i++) {	        	
			        	char c = (char) in.read();
			        	trackinfo += c;	        	
			        }
										
					//1 byte master volume 					
					int volume = in.read();
					System.out.println("track.volume " + volume);
					
					//1 byte master pan					
					int pan = in.read();
					System.out.println("track.pan " + pan);					
					
					//now parse by track type
					if (type == 0) { 		//instrument track
						TrackOLD track = new TrackOLD(SongMixerOLD.this, trkSize_px, trkSize_px, 8); //TODO create a var instead of hardcode
						track.info = trackinfo;
						track.volume = volume;
						track.pan = pan;
						
						//assign a unique runtime ID # to track for UI purposes						
						int id = 0;	
						if (song.tracks.size() == 0) { //no tracks in song yet so just assign it 0
							System.out.println("assigning track ID " + id);
							track.ID = id;
						}
						else {
							boolean unique = false;
							while (!unique) {
								boolean dirty = false;
								for (int i=0; i < song.tracks.size(); i++) {
									if (id == song.tracks.get(i).ID) {
										dirty = true;
										id++;									
									}
									if (i == song.tracks.size()-1 && !dirty) {
										System.out.println("assigning track ID " + id);
										track.ID = id;
										unique = true;
									}																
								}							
							}
						}											
						
						//1 byte for instrument number code
						int inum = in.read();
						track.instrument = new Instrument(SongMixerOLD.this, inum); 
						System.out.println("track.instrument.instmtNum " + track.instrument.instmtNum);
						
						// int (4bytes) for how many bytes of note data follows 
						int dataBytes = in.readInt();
						System.out.println("dataBytes to read " + dataBytes);						
						
						long targetPtr = in.getFilePointer() + dataBytes;							
						
						while (in.getFilePointer() < targetPtr) { //loop over note data							
							
							track.notes.add(in.readInt()); //start time in milliseconds (int 4 bytes)								
							track.notes.add(in.read()); //note 1 byte (0-127)							
							track.notes.add((int) in.readShort()); //duration - unsigned short 2 bytes - allows for max sustained note of ~65 seconds							
							track.notes.add(in.read()); //volume - 1 byte
							
							//1 byte to tell how many bytes of note dynamic info/instructions follow like bends (up to 256 bytes)
							int dynBytes = in.read();
							track.notes.add(dynBytes);
							
							//dynamics info/instructions (up to 256 bytes)
							for (int i=0; i < dynBytes; i++) {								
								track.dyn.add(in.read()); //store note dynamics in own array so can maintain consistent stride in notes array for easier/faster playback
							}							
						}
						
						System.out.println("type=0 track end. pointer: " + in.getFilePointer());
						//song.tracks.add(track);
					}
					
					else if (type == 1) { //audio track
						//TODO needs full implement
						AudioTrack audio_track = new AudioTrack(SongMixerOLD.this);						
						audio_track.info = trackinfo;
						audio_track.volume = volume;
						audio_track.pan = pan;
						
						song.audio_tracks.add(audio_track);
					}
					
					else if (type == 2) { //percussion track	
						System.out.println("songLoad() parsing type=2 percussion track");
						DrumTrack drum_track = new DrumTrack(SongMixerOLD.this);						
						drum_track.info = trackinfo;
						drum_track.volume = volume;
						drum_track.pan = pan;
						
						int numDrums = in.read(); //subtracks, 1 for each perc instmt in drum track
						System.out.println("numDrumTks " + numDrums);
						
						for (int i=0; i < numDrums; i++) {									
							//1 byte for drum number code
							int dNum = in.read();
							
							Drum drum = new Drum(SongMixerOLD.this, dNum);
							drum_track.drums.add(drum);
							int dIndex = drum_track.drums.size() - 1;
							
							//int (4 bytes) for how many bytes of note/strike data follows
							int numbytes = in.readInt();
							
							int byteCount = 0;
							while (byteCount != numbytes) {									
								drum_track.drums.get(dIndex).score.add(in.readInt()); byteCount+=4; //start time
								drum_track.drums.get(dIndex).score.add(in.read()); byteCount++; //volume
								drum_track.drums.get(dIndex).score.add(in.read()); byteCount++; //pan								
							}							
						}						
					
						song.drum_tracks.add(drum_track);
					}						
				}
				
				in.close(); //close file stream				
				System.out.println("Song has: " + song.tracks.size() + " tracks");
				
				//Closed file stream & picking up rest in onPostExecute()				
			}
			catch (IOException e) {			
				e.printStackTrace();
				System.out.println("error opening dbs song file in songLoad()");
				//TODO handle error
			}		
			
			return null;
		}
		
		@Override
		   protected void onPostExecute(String result) {
		      super.onPostExecute(result);			      
		      
		      //OK have all the song data loaded. need to load instruments & handle drawing UI
		      
		      int songLength = songLength(); //calculate/set the song length
		      System.out.println("songLoad() songLength " + songLength);
		      //finish loading in order of instmt tracks, audio tracks, drum tracks (as stored in dbs file)
		      
		      for (int i=0; i < song.tracks.size(); i++) {		    	  
		    	  
		    	  song.tracks.get(i).instrument.loadInstrument();
		    	  
		    	  int type = 0;
		    	  trackUiList.add(type);
		    	  trackUiList.add(song.tracks.get(i).ID);
		      }
		      		      
		      for (int i=0; i < song.audio_tracks.size(); i++) {
		    	  //TODO implement
		    	  int type = 1;
		    	  //trackUiList.add(type);
		      }		      
		      
		      for (int i=0; i < song.drum_tracks.size(); i++) { //there can be more than one drum track, each containing a set of drums 
		    	  
		    	  //TODO drum tracks will likely need an ID field
		    	  int type = 2;
		    	  //trackUiList.add(type);	
		    	  //song.drum_tracks.get(i).drums.ID
		    	  //OK this one needs inner loop
		    	  for (int j=0; j < song.drum_tracks.get(i).drums.size(); j++) {
		    		  
		    		  song.drum_tracks.get(i).drums.get(j).loadDrum();  
		    		  
		    	  }
		      }	
		      
		      firstLoad = true;
		      
			  ProgressBar spinner = (ProgressBar) findViewById(R.id.smProgSpnr);
			  spinner.setVisibility(View.GONE);
			  
			  new songSort().execute();
		      new songDraw().execute();
		      
		      /**
		       * TODO likely need to lock out play Button etc. while these final ops happen
		       * could also do a toast or suc informing user that the song is now loaded
		       */
		}		
	}
	
	
	public class songSort extends AsyncTask <Void, Void, Boolean> {
		/**
		 * sorts all instrument notes and drum hits into as many imod patches
		 * as required to play all without "stepping on"/truncating each other		
		 **/
		
		List<Varray> varrays = new ArrayList<Varray>();
		
		@Override
		protected void onPreExecute() {
			System.out.println("songSort()");
			
			imods.clear(); //clear list of previous imods			
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			boolean success = true;
			
			//put all instrument and drum notes into a single unordered list with the data in the form imod uses			
			List<NoteData> notesList = new ArrayList<NoteData>();
			
			boolean sampleFound; //true when note matched to a sample's note range
			
			//add instrument track notes		
			for (int i=0; i < song.tracks.size(); i++) {
				
				if (song.tracks.get(i).isOn) { //if track is set to playable
					
					int index = song.tracks.get(i).instrument.index; //index for instrument in master list					
					
					for (int j=0; j < song.tracks.get(i).notes.size(); j+=5) {	
						
						NoteData noteData = new NoteData(); //little object class to hold the 5 variables
						
						noteData.start = song.tracks.get(i).notes.get(j);
						
						//find right sample that plays note and calculate rate
						sampleFound = false;
						int note = song.tracks.get(i).notes.get(j+1);
						if (note < 0 || note > 127) System.out.println("note out of range " + note); //TODO handle error						
						
						for (int k=0; k < gv.instruments.get(index).samples.length; k++) {
							int loNote = gv.instruments.get(index).samples[k].loNote;
							int hiNote = gv.instruments.get(index).samples[k].hiNote;														
															
							if (note >= loNote && note <= hiNote) { //then have right sample for note								
								sampleFound = true;
								noteData.arNum = gv.instruments.get(index).samples[k].patchID;	//"ar" is appended in imod						
								noteData.numSamples = gv.instruments.get(index).samples[k].numSamples;
								float baseNote = gv.instruments.get(index).samples[k].baseNote;
								float baseRate = gv.instruments.get(index).samples[k].baseRate;
								float diff = baseNote - note;
								noteData.rate = (float) (Math.pow(0.944287, diff) * baseRate); //rate to play sample to get desired pitch
								break;							
							}							
						} //k loop	
						if (!sampleFound) {
							System.out.println("songSort: sample not found that matches " + note + " instmtNum " + gv.instruments.get(index).instmtNum);
							//TODO handle error
						}
						
						noteData.dur = song.tracks.get(i).notes.get(j+2);						
						
						notesList.add(noteData);
					} //j loop 
				}
			} //i loop			
			System.out.println("after notes imodList.size() " + notesList.size());
			
			
			//then add all the drum hits			
			for (int i=0; i < song.drum_tracks.size(); i++) {
				for (int j=0; j < song.drum_tracks.get(i).drums.size(); j++) {					
					
					if (song.drum_tracks.get(i).drums.get(j).isOn) {
						
						int index = song.drum_tracks.get(i).drums.get(j).index; //index in master drums list						
						int patchID = gv.drums.get(index).patchID;					
						float sampleRate = gv.drums.get(index).sampleRate;					
						float numSamples = gv.drums.get(index).numSamples;					
						float dur = numSamples/sampleRate * 1000; //in ms. derive since not stored in dbs format					
						float rate = sampleRate/numSamples;	
						
						System.out.println("songSort() drum " + song.drum_tracks.get(i).drums.get(j).drumNum
						 + " patchID " + patchID + " sampleRate " + sampleRate + " numSamples " + numSamples 
						  + " dur " + dur + " rate " + rate);						
						
						for (int k=0; k < song.drum_tracks.get(i).drums.get(j).score.size(); k+=3) {
							
							NoteData drumData = new NoteData();
							
							drumData.start = song.drum_tracks.get(i).drums.get(j).score.get(k);							
							drumData.arNum = patchID;						
							drumData.numSamples = numSamples;
							drumData.dur = dur; 
							drumData.rate = rate;												
							
							notesList.add(drumData);
						}	
					}									
				}
			}
			System.out.println("after drums imodList.size() " + notesList.size());			
			
			//sort full noteList by startTime using a quicksort			
			int lowIndex = 0;
			int hiIndex = notesList.size() - 1;
			
			//double startTime = System.nanoTime(); //just to benchmark the sort
			
			if (quickSort(notesList, lowIndex, hiIndex)) { //yeah, it's fuckin fast
				/*
				 * //just to benchmark the sort
				double endTime = System.nanoTime(); 
				double diff = endTime - startTime;
				double bil = 1000000000; //divisor to convert from nanoseconds to seconds
				double duration = diff/bil;
				System.out.println("songSort(): quickSort() succeeded in " + duration + " seconds");
				*/
				
				//step through sorted list and eliminate all overlaps of notes by creating varrays				
				varrays.add(new Varray()); //add first varray (always at least one)				 
				
				while (notesList.size() > 0) { //each note gets popped off list when added to a varray
					
					boolean placed = false;
					
					for (int i=0; i < varrays.size(); i++) {
						
						//check if next note will fit in a varray without overlapping previous note						
						if (notesList.get(0).start > varrays.get(i).endTime) { //no overlap so add to this varray
							float gapTime = notesList.get(0).start - varrays.get(i).endTime;
							varrays.get(i).data.add(gapTime); //the time between the notes
							varrays.get(i).data.add(notesList.get(0).arNum);
							varrays.get(i).data.add(notesList.get(0).numSamples);
							varrays.get(i).data.add(notesList.get(0).dur); 
							varrays.get(i).data.add(notesList.get(0).rate);
							
							//set new endTime for varray
							varrays.get(i).endTime = notesList.get(0).start + notesList.get(0).dur;							
							
							placed = true;
							break;
						}
					}
					
					if (!placed) { //the note overlapped every available varray so add a new one and put note in it
						
						Varray v = new Varray();
						float gapTime = notesList.get(0).start;
						v.data.add(gapTime);
						v.data.add(notesList.get(0).arNum);
						v.data.add(notesList.get(0).numSamples);
						v.data.add(notesList.get(0).dur); 
						v.data.add(notesList.get(0).rate);
						
						v.endTime = notesList.get(0).start + notesList.get(0).dur;
						
						varrays.add(v);	
					}
					
					//remove the placed note from the imodList
					notesList.remove(0);
				}
				
				System.out.println("finished loading varrays");
				System.out.println("varrays.size() " + varrays.size());
				for (int i=0; i < varrays.size(); i++) {
					System.out.println("varray " + i + " size " + varrays.get(i).data.size() +
							" start " + varrays.get(i).data.get(0) + " end " + varrays.get(i).endTime);
				}
				
			}
			else {
				System.out.println("songSort(): quickSort() failed");
				//TODO handle error
			}		
		
			return success; //TODO not really used yet. Here to implement better error checking with
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			
			if (result) {				
				
				String imodPatch = gv.appPath + "/dittybot/patches/imod.pd";	
				
				lastImodID = -1; //reset any previous value. ID of imod patch that ends last (end of song)
				float hiEndTime = -1; //use to find the latest endTime of all the imods
				
				while (varrays.size() > 0) {					
					
					int ar_size = varrays.get(0).data.size();
					float[] data = new float[ar_size];
					for (int i=0; i < ar_size; i ++) { //convert arraylist to an array to send to patch
						data[i] = varrays.get(0).data.get(i);
					}
					float endTime = varrays.get(0).endTime;					
					varrays.remove(0); //remove duplicate data from memory
					
					int imodID = -1;
					try {
						imodID = PdBase.openPatch(imodPatch);						
						
						if (imodID != -1) {
							System.out.println("imodID " + imodID);
							
							Imod imod = new Imod();							
							imod.patchID = imodID;		
							imod.endTime = endTime;
							if (imod.endTime > hiEndTime) {
								hiEndTime = imod.endTime;
								lastImodID = imod.patchID; //used ine doneRcvr() to detect end of song playback
							}
														
							PdBase.sendFloat(imodID + "-arlen", ar_size); //resize the array for data
							PdBase.writeArray(imodID + "na", 0, data, 0, data.length); //inject the data into the imod array
							imod.arraySize = PdBase.arraySize(imodID + "na");
							System.out.println("imod array size: " + imod.arraySize); //TODO use to verify
							
							PdBase.sendBang(imodID + "f"); //prime the first note
							
							String pauseTag = imodID + "paused";
							gv.pdRcvr.addListener(pauseTag, pauseRcvr);
							String doneTag = imodID + "done";
							gv.pdRcvr.addListener(doneTag, doneRcvr);
							
							imods.add(imod); //add to list of current open imod patches
						}
						else {
							//TODO handle error imod.pd patch didn't open properly
						}						
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} //end while loop					
			}
			else {
				//TODO something went wrong above. Handle error
			}				
		}	
	}
	
    private boolean quickSort(List<NoteData> imodList, int lowIndex, int hiIndex) { //like a mug
        
    	boolean done = false;
    	
        int i = lowIndex;
        int j = hiIndex;
        
        //start at middle index        
        float pivot = imodList.get(lowIndex+(hiIndex-lowIndex)/2).start;
        // Divide into two arrays
        while (i <= j) {
            
            while (imodList.get(i).start < pivot) {
                i++;
            }
            while (imodList.get(j).start > pivot) {
                j--;
            }
            if (i <= j) {
                //exchange values
            	NoteData temp = imodList.get(i);                
                imodList.set(i, imodList.get(j));
                imodList.set(j, temp);
        
                //move index to next position on both sides
                i++;
                j--;
            }
        }
        //recursive
        if (lowIndex < j)
            quickSort(imodList, lowIndex, j);
        if (i < hiIndex)
            quickSort(imodList, i, hiIndex);
        
        done = true;        
        return done;
    }
	
	public class songDraw extends AsyncTask <String, Integer, String> { //using AsyncTask for loading pics etc.
		
		int songUiWidth;
		int songUiHeight = 0; //note that not counting the height of the instrument track header as position all below it
		int trkHdrHgt; //height of track type headers drawn on screen
		float playLinePos;
		RelativeLayout iTrkHdrLL = (RelativeLayout) findViewById(R.id.smIHdrRL); //instrument tracks header
		RelativeLayout dTrkHdrLL = (RelativeLayout) findViewById(R.id.smDHdrRL); //drum tracks header
		RelativeLayout aTrkHdrLL = (RelativeLayout) findViewById(R.id.smAHdrRL); //audio tracks header
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			//show the working/spinner
		    ProgressBar spinner = (ProgressBar) findViewById(R.id.smProgSpnr);
		    spinner.setVisibility(View.VISIBLE);
		    
		    if (firstLoad) playLinePos = 0;
			else playLinePos = playLine.xpos; //store before remove so can re-add
		    
		    //clear the screen & draw all each time (probably not optimal, but simplifies things a lot)
			iTracksLL.removeAllViews();
			bodyRL.removeAllViews();
		    
			//measure how much width & height needed for layout
			float song_l = song.length;
		    songUiWidth = (int) ((song_l/zoom_factor) + 0.5);
			if (gv.screenWidth > songUiWidth) songUiWidth = gv.screenWidth;	//case when start new song				
						
			//go through trackUiList and determine heights & positions of everything as will be drawn to screen. ** This relies on defining sizes and placement of all elements ahead of time
			for (int i=0; i < trackUiList.size(); i += 2) { //track type/track.ID (stride 2)
				
				int type = trackUiList.get(i);
				int id = trackUiList.get(i+1);
				
				if (type == 0) { //instrument track					
					for (int j=0; j < song.tracks.size(); j++) {
						if (id == song.tracks.get(j).ID) {
							//song.tracks.get(j)._topY = songUiHeight;
							//TODO in here is where might handle track.uiState, though may handle in Track classes
							//System.out.println("songDraw() itrack topY " + song.tracks.get(j)._topY);
							//songUiHeight += song.tracks.get(j)._height;
							break;
						}
					}					
				}								
				
				if (type == 2) { //drum track
					System.out.println("songDraw() draw track");
				}				
				
				if (type == 1) { //audio track
					System.out.println("songDraw() audio track");
					
				}					
			}	
			
			songUiHeight += dTrkHdrLL.getLayoutParams().height; //add height of drum tracks header
			songUiHeight += aTrkHdrLL.getLayoutParams().height; //add height of audio tracks header
		     
			System.out.println("full_height " + songUiHeight);
		     
			bodyRL.getLayoutParams().width = songUiWidth; 
			bodyRL.getLayoutParams().height = songUiHeight;			
			bodyRL.requestLayout();						
		}
		
		@Override
		protected String doInBackground(String... params) { 
		  
		  //load the instrument pics in the track objects
		  for (int i=0; i < song.tracks.size(); i++) {
				//TrackOLD iTrack = song.tracks.get(i);				
				
				//String instmtDir = iTrack.instrument.dirName;			
				//int instmtNum = iTrack.instrument.instmtNum;			
				//String imgPath = gv.appPath + "/dittybot/instruments/" + instmtDir + "/pics/" + instmtNum + ".jpg";
				
				//Bitmap img = BitmapFactory.decodeFile(imgPath);			
				//iTrack.imageView.setImageBitmap(img);
	      }
	      
	      for (int i=0; i < song.audio_tracks.size(); i++) {
	    	  //TODO get height of each AudioTrack and add to full_height
	      }
	      
	      for (int i=0; i < song.drum_tracks.size(); i++) {
	    	//TODO get height of each DrumTrack and add to full_height
	      }		
			
	      return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);	      
			System.out.println("post execute SongDraw()"); 
	    
			//loop over trackUiList & add all track UI to screen
			for (int i=0; i < trackUiList.size(); i += 2) { //track type/track.ID (stride 2)
				int type = trackUiList.get(i);
				int id = trackUiList.get(i+1);
				
				if (type == 0) { //instrument track				
					for (int j=0; j < song.tracks.size(); j++) {
						if (id == song.tracks.get(j).ID) {
							//TrackOLD iTrack = song.tracks.get(j);
							//iTracksLL.addView(iTrack); //add track UI to sidebar
							
							//int y = iTrack._topY + iTrack._topMargin; //TODO this seems wonky
							//int ht = iTrack._height - iTrack._topMargin + 1; //yeah wonky. empirically saw need to add a pixel as some mismatch in way Android these things
							
							//add trackblocks to bodyRL
							/*
			    			for (int k=0; k < iTrack.notes.size(); k+=5) { //start, note, duration, volume, #dynbytes
			    				int start = iTrack.notes.get(k);
			    				int note = iTrack.notes.get(k+1);
			    				int dur = iTrack.notes.get(k+2);	    				
			    				
			    				TrackBlock tblock= new TrackBlock(SongMixerOLD.this, y, ht, start, note, dur, zoom_factor);
			    				
			    				RelativeLayout.LayoutParams block_params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
			    						LayoutParams.WRAP_CONTENT);
			    				
			    				block_params.width = (int)tblock.width; //TODO this simple truncation needs to be dealt with. see notes			
			    				block_params.height = (int)tblock.height;
			    				block_params.leftMargin = (int)tblock.offsetH;			
			    				block_params.topMargin = (int)tblock.offsetV;			
			    							
			    				tblock.setLayoutParams(block_params);		    				
			    				
			    				bodyRL.addView(tblock);	
			    			}	
							*/
							break;
						}
					}					
				}
				
				if (type == 1) { //audio track
					System.out.println("songDraw() audio track");
					
				}
				
				if (type == 2) { //drum track
					System.out.println("songDraw() draw track");
				}
			}
			
			//int offset = trkSize_px + song.tracks.get(0).led.width; //TODO this is pretty wonky. Should have something like a trkCtrlWidth or such. This offset is so the playLine never goes off right side of screen in a fling
			//playLine = new PlayLine(SongMixerOLD.this, songUiWidth, songUiHeight, offset);
			//playLine.xpos = playLinePos;			
			playLine.invalidate(); //forces redraw at set location
			bodyRL.addView(playLine, songUiWidth, songUiHeight);			
			
			//staticLine = new PlayLine(SongMixerOLD.this, gv.screenWidth, songUiHeight, offset);
			staticLine.isStatic = true;
			staticLine.setVisibility(View.GONE);
						
			RelativeLayout.LayoutParams line_params = 
					new RelativeLayout.LayoutParams(gv.screenWidth, songUiHeight);
			line_params.addRule(RelativeLayout.BELOW, iTrkHdrLL.getId());
			staticLine.setLayoutParams(line_params);
			
			RelativeLayout mainScrollRL = (RelativeLayout) findViewById(R.id.smOSVRL);			
			mainScrollRL.addView(staticLine);
			
			//bring the track type headers to top of z order so playLine(s) run below them
			RelativeLayout dTrkHdrLL = (RelativeLayout) findViewById(R.id.smDHdrRL);
			dTrkHdrLL.bringToFront();
			dTrkHdrLL.invalidate();
			
			RelativeLayout aTrkHdrLL = (RelativeLayout) findViewById(R.id.smAHdrRL);
			aTrkHdrLL.bringToFront();
			aTrkHdrLL.invalidate();
			
			System.out.println("songDraw() postExecute: bodyRL width " + bodyRL.getWidth() + " height " + bodyRL.getHeight());
			System.out.println("songDraw() postExecute: bodySV width " + bodySV.getWidth() + " height " + bodySV.getHeight());

	        //remove the working/please wait spinner
	        ProgressBar spinner = (ProgressBar) findViewById(R.id.smProgSpnr);
		    spinner.setVisibility(View.GONE);
		      
	   }
	}
	
	private int songLength() { //calculate total song length in ms
		System.out.println("songLength()");		
		
		int longest = -1;
				
		for (int i=0; i < song.tracks.size(); i++) {
			
			int trackEnd = -1;
			
			for (int j=0; j < song.tracks.get(i).notes.size(); j+=5) { //start, note, dur, volume, #dynBytes
				int start = song.tracks.get(i).notes.get(j);
				int dur = song.tracks.get(i).notes.get(j+2);
				int endTime = start + dur; //find the note that ends the latest in the track
				if (endTime > trackEnd) trackEnd = endTime;
				if (endTime > longest) longest = endTime;
			}
			
			//this reports each track's start end and length in milliseconds TODO store in Track class?
			int instmtNum = song.tracks.get(i).instrument.instmtNum;
			int trackStart = song.tracks.get(i).notes.get(0); //because notes list arranged by start time
			int trackLen = trackEnd - trackStart;			
		}
		
		for (int i=0; i < song.audio_tracks.size(); i++) {
			//TODO implement
		}
		
		for (int i=0; i < song.drum_tracks.size(); i++) { //possible to have multiple tracks in song
			for (int j=0; j < song.drum_tracks.get(i).drums.size(); j++) {  //each drum in track
				for (int k =0; k < song.drum_tracks.get(i).drums.get(j).score.size(); k+=3) {  //start volume pan
					int index = song.drum_tracks.get(i).drums.get(j).index;
					float nSamples = gv.drums.get(index).numSamples;
					float sRate = gv.drums.get(index).sampleRate;
					float hitDur_ms = (nSamples/sRate) * 1000; //convert secs to ms
					int d_time = (int) (song.drum_tracks.get(i).drums.get(j).score.get(k) + hitDur_ms); 
					if (d_time > longest) longest = d_time;					
				}
			}			
		}
		
		if (longest != -1) {
			song.length = longest;			
			System.out.println("song.length: " + song.length);
			return longest;
		}
		else {
			System.out.println("songLength() error. No length was found");
			return longest;
		}				 
	}	
	
	public class songSave extends AsyncTask <String, Integer, String> {		
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();	
			System.out.println("***** songSave *******");
		}
		
		@Override
		protected String doInBackground(String... params) {
			
			byte[] dbsf = new byte[4]; //DBSF magic seq as 1 byte ASCII chars to ID file as a DittyBot song file
			dbsf[0] = 68; //D
			dbsf[1] = 66; //B
			dbsf[2] = 83; //S
			dbsf[3] = 70; //F
			
			byte[] dbtk = new byte[4]; //DBTK magic seq ID at beginning of DittyBot tracks
			dbtk[0] = 68; //D
			dbtk[1] = 66; //B
			dbtk[2] = 84; //T
			dbtk[3] = 75; //K		
			
			String filePath = gv.extStorPath + "/DittyBot/Songs/" + song.fileName;
			
			File song_file = new File(filePath); //TODO maybe verify file exists etc.
			System.out.println("filePath " + filePath);
			
			try {
				RandomAccessFile out = new RandomAccessFile(song_file, "rw");
				
				//----------- Song Info -----------------------------------------------
				//4 bytes DBSF file ID seq 
				out.write(dbsf); 
				
				//1 byte song.name length field					 
				byte nb = (byte) song.name.length(); //TODO make sure elsewhere user can't use more than 256 chars
				out.write(nb); 	//out.writeByte(nl); I think this would work too.. writes low 8 bits of int				
				//convert each character in name string to its ASCII value & then write a byte for it					
				out.write(song.name.getBytes("US-ASCII"));					
				
				//1 byte song.info length field					
				byte ib = (byte) song.info.length();
				out.write(ib);				
				//convert each character in song info string to its ASCII value & then write a byte for it				
				out.write(song.info.getBytes("US-ASCII"));
				
				//8 bytes write tempo
				out.writeDouble(song.tempo);
				
				
				//------------- Track Data -----------------------------------------------
				
				for (int i=0; i < song.tracks.size(); i++) { //instrument tracks
					//4 bytes DBTK magic seq marks begin of each track
					out.write(dbtk);
					
					//1 byte write track type
					byte typeb = 0;
					out.write(typeb);
					
					//1 byte for track.info length field
					byte tib = (byte) song.tracks.get(i).info.length();
					out.write(tib);
					//convert each character in track info string to its ASCII value & then write a byte for it				
					out.write(song.tracks.get(i).info.getBytes("US-ASCII"));
					
					//1 byte volume
					byte vb = (byte) song.tracks.get(i).volume;
					out.write(vb);
					
					//1 byte pan
					byte pb = (byte) song.tracks.get(i).pan;
					out.write(pb);
					
					//-------- Notes Data -----------
					
					//1 byte for instrument number code
					byte inum = (byte) song.tracks.get(i).instrument.instmtNum;
					out.write(inum);						
					
					//4 bytes int for # bytes of note data that track contains
					long numBytesPtr = out.getFilePointer();
					int numBytes = 0; //keep count of bytes written
					out.writeInt(numBytes); //a placeholder initially. come back & write total numBytes written 
					
					ListIterator<Integer> pointer = song.tracks.get(i).notes.listIterator();
					
					//write all note data-- start, note, duration, volume, # dynamic bytes, dynamic info (if any)												
					while (pointer.hasNext()) {
						out.writeInt(pointer.next()); numBytes += 4; //start ms 4 bytes
						out.write(pointer.next()); numBytes++; //note 1 byte (RandomAccessFile writes 8 least sig bits this way)
						out.writeShort(pointer.next()); numBytes += 2; //duration 2 bytes (RandomAccessFile writes 2 least sig bytes of int)
						out.write(pointer.next()); numBytes++; //volume 1 byte
						
						int nDynBytes = pointer.next();	//# bytes of following dynamic info/instructions (up to 256 bytes) 1 byte						
						out.write(nDynBytes); numBytes++;  
						if (nDynBytes > 0) { //if any, write the ASCII byte values of the dynamics info
							System.out.println("songSave() nDynBytes > 0");
							for (int n=0; n < nDynBytes; n++) {
								out.write(pointer.next()); //1 byte for each ASCII character used in dynamics info
								numBytes++;
							}
						}
					}
					
					long currPtr = out.getFilePointer(); 
					out.seek(numBytesPtr); //jump back to # bytes data field
					out.writeInt(numBytes);   //and write how many bytes were written to file
					out.seek(currPtr); //return to latest pointer position
					System.out.println("numBytes " + numBytes + " numBytesPtr " + numBytesPtr + " currPtr " + currPtr);					
				}
				
				for (int i=0; i < song.audio_tracks.size(); i++) {  //audio tracks
					
					/* THIS ALL NEEDS TO BE WORKED OUT
					
					//4 bytes DBTK magic seq marks begin of each track
					out.write(dbtk);
					
					//1 byte write track type
					byte typeb = 1;
					out.write(typeb);
					
					//1 byte for track.info length field
					byte tib = (byte) song.audio_tracks.get(i).info.length();
					out.write(tib);
					//convert each character in track info string to its ASCII value & then write a byte for it				
					out.write(song.audio_tracks.get(i).info.getBytes("US-ASCII"));
					
					//1 byte volume
					byte vb = (byte) song.audio_tracks.get(i).volume;
					out.write(vb);
					
					//1 byte pan
					byte pb = (byte) song.audio_tracks.get(i).pan;
					out.write(pb);	
					
					//-------- Notes Data -----------
					
					//4 bytes int for # bytes of audio data that track contains	
					long numBytesPtr = out.getFilePointer();
					int numBytes = 0; //keep count of bytes written
					out.writeInt(0); //a placeholder initially. come back & write total numBytes written 
					
					ListIterator<Object> audptr = song.audio_tracks.get(i).listIterator();						
					while (audptr.hasNext()) {
						//start time ms 4 bytes
						int start = (Integer) audptr.next();
						out.writeInt(start);
						numBytes += 4;
						 
						String fileName = (String) audptr.next();
						//# file name bytes to read - 1 byte
						int fnamelen = fileName.length();
						out.write(fnamelen);
						numBytes++;
						//audio file name characters (up to 256 bytes)
						for (int j=0; j < fnamelen; j++) {
							char c = fileName.charAt(j);
							out.write(c);
							numBytes++;
						}							
						
						//duration ms 2 bytes
						int duration = (Integer) audptr.next();
						out.writeShort(duration);
						numBytes += 2;
						
						//volume - 1 byte
						int volume = (Integer) audptr.next();
						out.write(volume);
						numBytes++;							
						
						//# dynamic bytes - 1 byte						
						int nDynBytes = (Integer) audptr.next();	//# bytes of following dynamic info/instructions (up to 256 bytes) 1 byte						
						out.write(nDynBytes);
						numBytes++;
						//dynamic info/instructions
						if (nDynBytes > 0) { //if any, write the ASCII byte values of the dynamics info
							for (int n=0; n < nDynBytes; n++) {
								int asciival = (Integer) audptr.next();
								out.write(asciival); //1 byte for each ASCII character used in dynamics info
								numBytes++;
							}
						}													
					}
					
					long currPtr = out.getFilePointer(); 
					out.seek(numBytesPtr); //jump back to # bytes data field
					out.writeInt(numBytes);   //and write how many bytes were written to file
					out.seek(currPtr); //return to latest pointer position
					System.out.println("numBytes " + numBytes + " numBytesPtr " + numBytesPtr + " currPtr " + currPtr);	
					
					*/
				}
				
				for (int i=0; i < song.drum_tracks.size(); i++) {  //drum tracks
					//4 bytes DBTK magic seq marks begin of each track
					out.write(dbtk);
					
					//1 byte write track type
					byte typeb = 2;
					out.write(typeb);
					
					//1 byte for track.info length field
					byte tib = (byte) song.drum_tracks.get(i).info.length();
					out.write(tib);
					//convert each character in track info string to its ASCII value & then write a byte for it				
					out.write(song.drum_tracks.get(i).info.getBytes("US-ASCII"));
					
					//1 byte volume
					byte vb = (byte) song.drum_tracks.get(i).volume;
					out.write(vb);
					
					//1 byte pan
					byte pb = (byte) song.drum_tracks.get(i).pan;
					out.write(pb);
					
					//-------- Notes Data -----------
					
					System.out.println("songSave() writing type=2 track");					
					int numdtks = song.drum_tracks.get(i).drums.size();
					System.out.println("numdtks " + numdtks);
					//1 byte - write # drum subtracks in the percussion track
					out.write(numdtks);
					
					for (int q=0; q < numdtks; q++) { //loop over each drum subtrack
						
						//1 byte drum number code
						System.out.println("drumNum= " + song.drum_tracks.get(i).drums.get(q).drumNum);
						out.write(song.drum_tracks.get(i).drums.get(q).drumNum);
						
						//int (4 bytes) for how many bytes of note data follows - 6 bytes per note/hit- 4 bytes for startTime, 1 byte volume, 1 byte pan
						int dbytes = 6 * (song.drum_tracks.get(i).drums.get(q).score.size() / 3); //stride 3 for each "hit", 4+1+1 bytes
						out.writeInt(dbytes);
						
						ListIterator<Integer> pointer = song.drum_tracks.get(i).drums.get(q).score.listIterator();							
						while (pointer.hasNext()) {								
							//start time in milliseconds (int 4 bytes) 
							out.writeInt(pointer.next());
							//volume - 1 byte
							out.write(pointer.next());
							//pan - 1 byte
							out.write(pointer.next());
						}
					}				
				}				
				
				out.close(); //close file stream
			}
			catch (IOException e) {			
				e.printStackTrace();			
			}
			
			return null;
		}
		
		@Override
		   protected void onPostExecute(String result) {
		      super.onPostExecute(result);		      
		      
		}		
	}	

//==== TRACK FUNCTIONS =============================================================================================
	
	public static void trackNotesView(int trackID) {
		System.out.println("trackNotesView() " + trackID);
	}
	
	
	private void trackAddDialog() {
		System.out.println("trackAddDialog()");
		
		final Dialog dialog = new Dialog(this); 
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
    	dialog.setContentView(R.layout.radiobtns);
    	
    	//TODO image to left of dialog title. If can find an icon that works for track use instead
    	ImageView iv = (ImageView) dialog.findViewById(R.id.radioBtnsIV);
		iv.setImageResource(R.drawable.scofile48);
    	
		TextView tv = (TextView) dialog.findViewById(R.id.radioBtnsTV);
		tv.setText("Add a Track to this song:  ");
		
		final RadioGroup rg = (RadioGroup) dialog.findViewById(R.id.radioBtnsRG);
		
		RadioButton savedRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB1);
		savedRB.setText("  Load a saved Track"); 
		
		RadioButton newRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB2);
		newRB.setText("  Create a new Track");
		
		//hide the unused radio button
		RadioButton unusedRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB3);
		unusedRB.setVisibility(View.GONE);
		
		Button okBtn = (Button) dialog.findViewById(R.id.radioBtnsOK);
		Button cancelBtn = (Button) dialog.findViewById(R.id.radioBtnsCncl);			
		
		okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				switch (rg.getCheckedRadioButtonId()) {
				case R.id.radioBtnsRB1: //Load a saved Track					
					dialog.dismiss();					
					trackSavedDlg();
					break;
		    	case R.id.radioBtnsRB2: //Create a new Track		    		
		    		dialog.dismiss();		    		
		    		trackTypeDlg();
		    		break;		    			    		
		    	}						
			}    		
    	});  
		
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}						
		});    	
    	
    	dialog.show();
	}
	
	private void trackTypeDlg() {
		System.out.println("trackTypeDlg() ");
		
		final Dialog dialog = new Dialog(this); 
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
    	dialog.setContentView(R.layout.radiobtns);
    	
    	//TODO image to left of dialog title. If can find an icon that works for track use instead
    	ImageView iv = (ImageView) dialog.findViewById(R.id.radioBtnsIV);
		iv.setImageResource(R.drawable.scofile48);
    	
		TextView tv = (TextView) dialog.findViewById(R.id.radioBtnsTV);
		tv.setText("Choose a track type:  ");
		
		final RadioGroup rg = (RadioGroup) dialog.findViewById(R.id.radioBtnsRG);
		
		RadioButton instmtRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB1);
		instmtRB.setText("  Instrument Track"); 
		
		RadioButton audioRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB2);
		audioRB.setText("  Audio Track");
		
		RadioButton drumRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB3);
		drumRB.setText("  Drum Track");
		
		Button okBtn = (Button) dialog.findViewById(R.id.radioBtnsOK);
		Button cancelBtn = (Button) dialog.findViewById(R.id.radioBtnsCncl);			
		
		okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				switch (rg.getCheckedRadioButtonId()) {
				case R.id.radioBtnsRB1: //Instrument Track					
					dialog.dismiss();
					trackAdd(0);
					break;
		    	case R.id.radioBtnsRB2: //Audio Track		    		
		    		dialog.dismiss();
		    		trackAdd(1);
		    		break;	
		    	case R.id.radioBtnsRB3: //Drum Track		    		
		    		dialog.dismiss();		    		
		    		trackAdd(2);
		    		break;
		    	}						
			}    		
    	});  
		
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}						
		});    	
    	
    	dialog.show();		
	}
	
	private void trackAdd(int type) { //0=instrument track, 1=audio track, 2=drum track
		System.out.println("trackAdd() " + type);
		//add the type track specified to song
		//prompt user to add track info, set volume/pan if wish
		//maybe have default track info in there like: song name, acoustic guitar track 1 
		
	}
	
	private void trackSavedDlg() { //called from trackAddDialog() when user chooses to add a saved track
		System.out.println("trackSavedDlg()");
		
		//Give a song list for them to drill into for track selections
		final String dirPath = gv.extStorPath + "/DittyBot/Songs/";
		
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.filechsrui);
		
		TextView title = (TextView) dialog.findViewById(R.id.fchsrTitleTV);
    	title.setText("  Select a song to view saved tracks:  ");
    	
    	Button doneBtn = (Button) dialog.findViewById(R.id.fchsrCloseBtn);
    	doneBtn.setText("Done"); //set up to allow user to access songs list numerous times if wish, then hit Done
    	
    	//hide buttons except for cancelBtn
    	ImageButton upBtn = (ImageButton) dialog.findViewById(R.id.fchsrUpBtn);
    	upBtn.setVisibility(View.GONE);    	
    	Button newBtn = (Button) dialog.findViewById(R.id.fchsrNewBtn);
    	newBtn.setVisibility(View.GONE);    	
    	Button pasteBtn = (Button) dialog.findViewById(R.id.fchsrPasteBtn);
    	pasteBtn.setVisibility(View.GONE);    	
    	Button helpBtn = (Button) dialog.findViewById(R.id.fchsrHelpBtn);
    	helpBtn.setVisibility(View.GONE);
    	
    	ListView lv = (ListView) dialog.findViewById(R.id.filechsrLV);
    	List<String> fileList = new ArrayList<String>();    	
    	lv.setAdapter(new FileAdapter(this, android.R.layout.simple_list_item_1, R.id.fnameTV, fileList, dirPath));
    	
    	lv.setOnItemClickListener(new OnItemClickListener() {	
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,	long id) {				
				
				LinearLayout fitem = (LinearLayout) view;							
				TextView file = (TextView) fitem.getChildAt(1);
				String filename = (String) file.getText();				
								
				String fullPath = dirPath + "/" + filename;				
				
				File fobj = new File(fullPath); //not sure how it could be anything else but yeah why not?
				if (fobj.isFile()) {		
					//trackSavedDlg2(filename);
					new trackSaved2().execute(filename);
				}								 
			} 			      	
    	});
    	
    	doneBtn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
			}    		
    	});
		
    	dialog.show();
	}
	
	public class trackSaved2 extends AsyncTask <String, Integer, String> { //called from trackSavedDlg(). Displays the tracks of the selected song		
				
		final Dialog dialog = new Dialog(SongMixerOLD.this);
		
		ProgressBar spinner;
		TextView title;		
		LinearLayout tracksLL;
		
		String songFname;
		String songName = "";
		String songInfo = "";
		double tempo;
		
		List<TrackOLD> tracks = new ArrayList<TrackOLD>(); //instrument tracks
		List<AudioTrack> audio_tracks = new ArrayList<AudioTrack>();
		List<DrumTrack> drum_tracks = new ArrayList<DrumTrack>();	
		
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			System.out.println("**** trackSaved2() ****");
			 
	    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);    	
	    	dialog.setContentView(R.layout.trackchsr);	
	    	
	    	spinner = (ProgressBar) dialog.findViewById(R.id.tkcProgSpnr);
		    spinner.setVisibility(View.VISIBLE);
		    
		    title = (TextView) dialog.findViewById(R.id.tkcTitleTV);
		    tracksLL = (LinearLayout) dialog.findViewById(R.id.tkcLL);
		    
	    	Button doneBtn = (Button) dialog.findViewById(R.id.tkcBtn1);
	    	doneBtn.setText("Done");
	    	
	    	doneBtn.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {				
					dialog.dismiss();
				}    		
	    	});
	    	
	    	dialog.show();			
		}
		
		@Override
		protected String doInBackground(String... params) {
			
			songFname = params[0];			
			String filePath = gv.extStorPath + "/DittyBot/Songs/" + songFname;
			
			File song_file = new File(filePath); //TODO maybe verify file exists etc.			
			try {
				RandomAccessFile in = new RandomAccessFile(song_file, "r");
				
				//--------- Song Info -------------------------------------
				//check for the DBSF magic seq
				String dbsf = "";
				for (int i=0; i < 4; i++) {	        	
		        	char c = (char) in.read();
		        	dbsf += c;	        	
		        }			
				if (!dbsf.contentEquals("DBSF")) {
					//TODO let user know the song file appears to be corrupted 
					System.out.println("trackSaved2 DBSF seq is bad. exiting");
					return null; //kick out of processing the file
				}			
				
				//read 1 byte song.fileName length field
				int nl = in.read();					
				//read nl # bytes in & convert back to a song name string 				
				for (int i=0; i < nl; i++) {
					char c = (char) in.read();
					songName += c;
				}				
				System.out.println("song name: " + songName);
				
				//read 1 byte song.info length field
				int il = in.read();				
				//read il # bytes in & convert back to song info string				
				for (int i=0; i < il; i++) {
					char c = (char) in.read();
					songInfo += c;
				}				
				System.out.println("song info: " + songInfo);
				
				//read a double (8 bytes) for song.tempo
				tempo = in.readDouble();
				System.out.println("song tempo " + tempo);
				
				//------------ Tracks Data ---------------------------------------------
				while (in.getFilePointer() < in.length()) {
					
					//find/verify DBTK magic seq for track begin					
					String dbtk = "";
					for (int i=0; i < 4; i++) {	        	
			        	char c = (char) in.read();
			        	dbtk += c;	        	
			        }			
					if (!dbtk.contentEquals("DBTK")) {
						//TODO handle error - let user know file is corrupt
						return null; //kick out of processing the file
					}					
									
					//1 byte track type 0=instmt, 1=audio, 2=percussion
					int type = in.read();
					System.out.println("track type: " + type);					
										
					//1 byte to tell how many bytes to read for track.info 
					int tib = in.read();
					System.out.println("# track info bytes: " + tib);
					
					//read track info ASCII byte chars
					String trackinfo = "";
					for (int i=0; i < tib; i++) {	        	
			        	char c = (char) in.read();
			        	trackinfo += c;	        	
			        }
										
					//1 byte master volume 					
					int volume = in.read();
					System.out.println("track.volume " + volume);
					
					//1 byte master pan					
					int pan = in.read();
					System.out.println("track.pan " + pan);					
					
					//now parse by track type
					if (type == 0) { 		//instrument track
						System.out.println("trackSaved2 parsing type=0 instmt track");
						TrackOLD track = new TrackOLD(SongMixerOLD.this, trkSize_px, trkSize_px, 8);
						tracks.add(track); //add track to this local global list
						track.info = trackinfo;
						track.volume = volume;
						track.pan = pan;
						
						//assign a unique runtime ID # to track for UI purposes	
						//this isn't really necessary as ID's are searched local to parent first
						int id = 0;	
						if (song.tracks.size() == 0) { //no tracks in song yet so just assign it
							System.out.println("assigning track ID " + id);
							track.ID = id;
						}
						else {
							boolean unique = false;
							while (!unique) {
								boolean dirty = false;
								for (int i=0; i < song.tracks.size(); i++) {
									if (id == song.tracks.get(i).ID) {
										dirty = true;
										id++;									
									}
									if (i == song.tracks.size()-1 && !dirty) {
										System.out.println("assigning track ID " + id);
										track.ID = id;
										unique = true;
									}																
								}							
							}
						}																	
						
						//1 byte for instrument number code
						int inum = in.read();
						track.instrument = new Instrument(SongMixerOLD.this, inum);
						System.out.println("track.instrument.instmtNum " + track.instrument.instmtNum);
						
						// int (4bytes) for how many bytes of note data follows 
						long dataPtr = in.getFilePointer();						
						track.setTag((int) dataPtr); //store this pointer so don't have to store all the track data in this track object yet
						int dataBytes = in.readInt(); //read the value so can jump over data for now but still retain reading frame
						long targetPtr = in.getFilePointer() + dataBytes;
						in.seek(targetPtr);						

						System.out.println("type=0 track end. pointer: " + in.getFilePointer());						
					}
					
					else if (type == 1) { //audio track
						System.out.println("trackSaved2 parsing type=1 audio track ***THIS SHIT IS BROKE!!");
						//TODO all of it ;-)
						AudioTrack audio_track = new AudioTrack(SongMixerOLD.this);	
						audio_tracks.add(audio_track); //local list
						audio_track.info = trackinfo;
						audio_track.volume = volume;
						audio_track.pan = pan;
						
						//** this won't have the long data section of instmt/drum tracks
					}
					
					else if (type == 2) { //percussion track	
						System.out.println("trackSaved2 parsing type=2 percussion track");
						DrumTrack drum_track = new DrumTrack(SongMixerOLD.this);
						drum_tracks.add(drum_track); //local list
						drum_track.info = trackinfo;
						drum_track.volume = volume;
						drum_track.pan = pan;
						
						int numDrumTks = in.read(); //subtracks, 1 for each perc instmt in drum track
						System.out.println("numDrumTks " + numDrumTks);
						
						for (int i=0; i < numDrumTks; i++) {							
							//1 byte for drum number code
							int dNum = in.read();							 
							
							Drum drum = new Drum(SongMixerOLD.this, dNum);
							drum_track.drums.add(drum);							
							
							//main track data
							long dataPtr = in.getFilePointer(); 
							//drum.setTag((int) dataPtr); //store this pointer so don't have to store all the track data in this track object yet
							int dataBytes = in.readInt(); //int (4bytes) for how many bytes of note data follows
							
							long targetPtr = in.getFilePointer() + dataBytes;
							in.seek(targetPtr);														
						}					
						
						System.out.println("type=2 track end. pointer: " + in.getFilePointer());
					}						
				}				
				
				in.close(); //close song file
				
				//now fetch all the instmt & drum bitmaps (haven't decided what image audiotracks get yet)
				  for (int i=0; i < tracks.size(); i++) {						 										
						int instmtNum = tracks.get(i).instrument.instmtNum;						
						String instmtDir = tracks.get(i).instrument.dirName;
						String imgPath = gv.appPath + "/dittybot/instruments/" + instmtDir + "/pics/" + instmtNum + ".jpg";						
						Bitmap img = BitmapFactory.decodeFile(imgPath);			
						tracks.get(i).imageView.setImageBitmap(img);						
				  }
				  
				  for (int i=0; i < audio_tracks.size(); i++) {
					  //TODO get height of each AudioTrack and add to full_height
				  }
				  
				  for (int i=0; i < drum_tracks.size(); i++) {
					//TODO get height of each DrumTrack and add to full_height
				  }		
			
			}
			catch (IOException e) {			
				e.printStackTrace();			
			}	
			
			return null; 
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);					
			
			title.setText("  Tracks in " + songName + ":  ");			  
	    	
			RelativeLayout iTrackRL = trackHdr(0); //instrument tracks header		
	    	tracksLL.addView(iTrackRL);
	    	//now display instrument tracks in descending order
			for (int i=0; i < tracks.size(); i++) {
				
				final TrackOLD iTrack = tracks.get(i);
				
				//preview button on left to listen to track prior to adding to song
				final ImageView prevBtn = new ImageView(SongMixerOLD.this);
				prevBtn.setId(1);
				boolean playing = false;
				prevBtn.setTag(playing); //way to keep track of playing/not playing state
				prevBtn.setImageResource(R.drawable.playbtnr);
				RelativeLayout.LayoutParams prevBtn_params = 
						new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				//prevBtn_params.topMargin = iTrack._topMargin;
				prevBtn_params.addRule(RelativeLayout.CENTER_VERTICAL);
				prevBtn_params.height = iTrack.iv_height;
				prevBtn_params.width = iTrack.iv_width;
				prevBtn.setLayoutParams(prevBtn_params);
				
				prevBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {					   		
						System.out.println("prevBtn click");						
											
						if (!(Boolean)prevBtn.getTag()) { //then not playing, so play it
							prevBtn.setTag(true);
							prevBtn.setImageResource(R.drawable.stopbtnr); //switch button to stopBtn							
							System.out.println("dataPtr " + iTrack.getTag());
							new trackSaved3(spinner, iTrack).execute(songFname);
							
						}
						else if ((Boolean) prevBtn.getTag()) { //then playing, stop it
							prevBtn.setTag(false);
							prevBtn.setImageResource(R.drawable.playbtnr); //switch button to playBtn
							//call prevStop() or maybe
						}
					}    		
		    	}); 
				
		    	iTrack.addView(prevBtn);
				
		    	//position the track imageview to right of prevBtn
				iTrack.imageView.setId(2);	
				RelativeLayout.LayoutParams iv_params = (RelativeLayout.LayoutParams) iTrack.imageView.getLayoutParams();
				iv_params.addRule(RelativeLayout.RIGHT_OF, 1);				
				
				//set track textview to instrument name & position in middle
				iTrack.textView.setId(3);
				RelativeLayout.LayoutParams tv_params = (RelativeLayout.LayoutParams) iTrack.textView.getLayoutParams();
				iTrack.textView.setText(iTrack.instrument.name);
				tv_params.addRule(RelativeLayout.RIGHT_OF, 2);
				tv_params.addRule(RelativeLayout.LEFT_OF, 4);
				
				//add a textView for m:ss track length 
				TextView lengthTV = new TextView(SongMixerOLD.this);
				lengthTV.setId(4);
				lengthTV.setTextColor(0xFFFFFFFF);
				//lengthTV.setTextColor(0xFF000000);
				lengthTV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
				lengthTV.setText("m:ss");
				RelativeLayout.LayoutParams lengthTV_params = 
						new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				lengthTV_params.addRule(RelativeLayout.CENTER_VERTICAL);
				lengthTV_params.addRule(RelativeLayout.LEFT_OF, 5);
				lengthTV.setLayoutParams(lengthTV_params);	
				iTrack.addView(lengthTV);	
				
				//add an Info button that will open a dialog to display the track.info
				Button infoBtn = new Button(SongMixerOLD.this);
				infoBtn.setId(5);
				infoBtn.setBackgroundResource(R.drawable.btnblue);
				infoBtn.setTextColor(0xFFFFFFFF);
				infoBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
				infoBtn.setText("Info");
				RelativeLayout.LayoutParams infoBtn_params = 
						new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				infoBtn_params.addRule(RelativeLayout.CENTER_VERTICAL);
				infoBtn_params.addRule(RelativeLayout.LEFT_OF, 6);
				infoBtn_params.leftMargin = 8;
				infoBtn_params.rightMargin = 5;
				infoBtn_params.height = iTrack.iv_height;
				infoBtn_params.width = 60; 
				
				infoBtn.setLayoutParams(infoBtn_params);
				iTrack.addView(infoBtn);
								
				//position an 'Add' button on right side
				Button addBtn = new Button(SongMixerOLD.this);
				addBtn.setId(6);
				addBtn.setBackgroundResource(R.drawable.btnbgn);
				addBtn.setTextColor(0xFFFFFFFF);
				addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); 
				addBtn.setText("Add");								
				RelativeLayout.LayoutParams addBtn_params = 
						new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				addBtn_params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				addBtn_params.addRule(RelativeLayout.CENTER_VERTICAL);
				addBtn_params.height = iTrack.iv_height;
				addBtn_params.width = 60; //arbitrary
				addBtn.setLayoutParams(addBtn_params);				
				iTrack.addView(addBtn);					
								
				tracksLL.addView(iTrack);
			}
			
			RelativeLayout aTrackRL = trackHdr(1); //audio tracks header			
	    	tracksLL.addView(aTrackRL);
			for (int i=0; i < audio_tracks.size(); i++) {
				//tracksLL.addView(audio_tracks.get(i));
			}
			
			RelativeLayout dTrackRL = trackHdr(2); //drum tracks header		
	    	tracksLL.addView(dTrackRL);
			for (int i=0; i < drum_tracks.size(); i++) {
				for (int j=0; j < drum_tracks.get(i).drums.size(); j++) {
					
				}
			}			  
			
			spinner.setVisibility(View.GONE);			 
		}
	}
	
	public class trackSaved3 extends AsyncTask <String, Integer, List<List<Integer>>> { 		
		//called from trackSaved2 when user clicks the prevBtn to play the track
		
		ProgressBar spinner;
		TrackOLD track;
		int dataPtr = -1;
		
		public trackSaved3(ProgressBar spinner, TrackOLD track){ //constructor allows to pass in extra parameters
		    this.spinner = spinner;
			this.track = track;		     
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			System.out.println("**** trackSaved3 ****");			
			spinner.setVisibility(View.VISIBLE);
			
			dataPtr = (Integer) track.getTag();			
		}
		
		@Override
		protected List<List<Integer>> doInBackground(String... params) {			
			/**
			 * note here that will eventually want to fetch the dynamics data as well
			 * so I set it up to pass a list of lists
			 */
			List<List<Integer>> trackData = new ArrayList<List<Integer>>();
			
			List<Integer> notes = new ArrayList<Integer>();
			trackData.add(notes);
			
			List<Integer> dyn = new ArrayList<Integer>();
			trackData.add(dyn);
			
			String songFname = params[0];
			
			String filePath = gv.extStorPath + "/DittyBot/Songs/" + songFname;
			File song_file = new File(filePath); //TODO maybe verify file exists etc.
			try {
				RandomAccessFile in = new RandomAccessFile(song_file, "r");
				
				in.seek(dataPtr);
				int dataBytes = in.readInt();
				System.out.println("dataBytes " + dataBytes);
				long targetPtr = in.getFilePointer() + dataBytes;
				while (in.getFilePointer() < targetPtr) { //loop over note data							
					
					notes.add(in.readInt()); //start time in milliseconds (int 4 bytes)								
					notes.add(in.read()); //note 1 byte (0-127)							
					notes.add((int) in.readShort()); //duration - unsigned short 2 bytes - allows for max sustained note of ~65 seconds							
					notes.add(in.read()); //volume - 1 byte
					
					//1 byte to tell how many bytes of note dynamic info/instructions follow like bends (up to 256 bytes)
					int dynBytes = in.read();
					notes.add(dynBytes);
					
					//dynamics info/instructions (up to 256 bytes)
					for (int i=0; i < dynBytes; i++) {								
						dyn.add(in.read()); //store note dynamics in own array so can maintain consistent stride in notes array for easier/faster playback
					}							
				}
				
				in.close();
			}
			catch (IOException e) {			
				e.printStackTrace();			
			}		
			
			return trackData; 
		}
		
		@Override
		   protected void onPostExecute(List<List<Integer>> trackData) {
			 super.onPostExecute(trackData);	
			
			 System.out.println("trackSaved3 postExecute");
			 track.notes = trackData.get(0);
			 track.dyn = trackData.get(1);  //TODO implement			
			
			 spinner.setVisibility(View.GONE);
			
			 preview("play", track);
		}		
	}
	
	private void preview(String command, final TrackOLD track) {
		System.out.println("preview() " + command);
		
		if (command.contentEquals("play")) {
			/*
			//reset the clock
			PdBase.sendMessage("stop_clock", "stop");
			PdBase.sendFloat("set_clock", 0);
			
			//mute all the current song's tracks by closing tick gates
			for (int i=0; i < song.tracks.size(); i++) {
				PdBase.sendFloat(song.tracks.get(i).instrument.gate, 0);
			}			
			for (int i=0; i < song.audio_tracks.size(); i++) {
				//TODO implement
			}
			for (int i=0; i < song.drum_tracks.size(); i++) {
				for (int j=0; j < song.drum_tracks.get(i).drums.size(); j++) {
					PdBase.sendFloat(song.drum_tracks.get(i).drums.get(j).gate, 0);
				}
			}	
			
			//setup instrument & play
			instmtInit(track);
			primeVoices(track);
			PdBase.sendFloat(track.instrument.gate, 1);
			PdBase.sendBang("start_clock");
			*/
		}
		else if (command.contentEquals("stop")) {
			//stop playback
			/*
			PdBase.sendMessage("stop_clock", "stop");
			
			//close the instrument
			track.instrument.closeInstrument();
			
			//clear the track's data lists
			track.notes.clear();
			track.dyn.clear();
			*/
		}
	}
	
	private RelativeLayout trackHdr(int type) { //track type header/separators
		RelativeLayout trackRL = new RelativeLayout(SongMixerOLD.this);
    	trackRL.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
    			LayoutParams.WRAP_CONTENT));
		
    	ImageView trackIV = new ImageView(SongMixerOLD.this);
    	trackIV.setId(1);
    	trackRL.addView(trackIV);
    	
    	TextView trackTV = new TextView(SongMixerOLD.this);
    	trackTV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    	trackTV.setTextColor(0xFFFFFFFF);
    	RelativeLayout.LayoutParams params = 
				new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
				LayoutParams.WRAP_CONTENT);    	
    	params.addRule(RelativeLayout.RIGHT_OF, 1); 
    	params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
    	trackTV.setLayoutParams(params);
    	trackRL.addView(trackTV);
		
		if (type == 0) { //instmt track
			trackRL.setBackgroundColor(0xFFFF0000);
			trackIV.setImageResource(R.drawable.scofile48);
			trackTV.setText("Instrument Tracks");
		}
		if (type == 1) { //audio track
			trackRL.setBackgroundColor(0xFF00FF00);
			trackIV.setImageResource(R.drawable.micfile48);
			trackTV.setText("Audio Tracks");
		}
		if (type == 2) { //drum track
			trackRL.setBackgroundColor(0xFF0000FF);
			trackIV.setImageResource(R.drawable.drumfile48);
			trackTV.setText("Drum Tracks");
		}
		
		return trackRL;
	}
	
	
//===== MIDI FUNCTIONS ===========================================================================================
	
	private void midiChooser() {
		System.out.println("midiChooser()");
		
		fileChooser("Midi", new PromptRunnable() {
			
			public void run() {
				//get the returned file path
				String filename = this.getValue();
				//put the code here to run when the selection is made				
				new getMidiAsync().execute(filename); //AsyncTask allows long file ops to happen in background thread
				
			}			
		});
	}
	
	public class getMidiAsync extends AsyncTask <String, Integer, String> {	
		
		boolean midiOK; //flag for each step of the processing chain
		
		Dialog dialog = new Dialog(SongMixerOLD.this); 
				
		@Override
		protected void onPreExecute() {
			super.onPreExecute();				
	      
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			dialog.setContentView(R.layout.mididlg);
			
			//TextView title = (TextView) dialog.findViewById(R.id.midiTitle);
		    TextView tv1 = (TextView) dialog.findViewById(R.id.midiTV1);
		    TextView tv2 = (TextView) dialog.findViewById(R.id.midiTV2);
		    TextView tv3 = (TextView) dialog.findViewById(R.id.midiTV3);
		    TextView tv4 = (TextView) dialog.findViewById(R.id.midiTV4);
		    TextView tv5 = (TextView) dialog.findViewById(R.id.midiTV5);
  
			tv1.setText("Please wait...");
			tv2.setVisibility(View.INVISIBLE); //make these invisible while processing midi file
			tv3.setVisibility(View.INVISIBLE);
			tv4.setVisibility(View.INVISIBLE);
			tv5.setVisibility(View.INVISIBLE);
			
			Button OKBtn = (Button) dialog.findViewById(R.id.midiOKbtn);  
			OKBtn.setVisibility(View.GONE);			
  
			dialog.show();
		}

		@Override
			protected String doInBackground(String... params) {	
			
			song = new Song(); //TODO make sure any previous song is saved before this
			
			//create a default song.fileName, song.name & song.info from the MIDI file name			
			String dfltSongName;
			String[] elements = params[0].split("\\.");
			dfltSongName = elements[0];
			song.fileName = dfltSongName + ".dbs";
			song.name = dfltSongName;
			song.info = "This song file was translated from the MIDI file: " + params[0];
			System.out.println("getMidiAsync song.name: " + song.name + " song.info: " + song.info);
			
			String filePath = gv.extStorPath + "/DittyBot/Midi/" + params[0]; //TODO maybe allow for subdirs off Midi dir
			System.out.println("getMidiAsync filePath" + filePath);
			
			midi = new Midi(SongMixerOLD.this, filePath);
			
			if(midi.preProcess()) { //open midi file, get header info & track data locations in file
				System.out.println("**preProcess() done**");			
				System.out.println("fileSize " + midi.fileSize);
				System.out.println("format type: " + midi.format_type);
				System.out.println("tracks found: " + midi.num_tracks);
				System.out.println("time div: " + midi.ppqn);			
			}
			else {
				System.out.println(midi.error_message); 
			}
			
			System.out.println("midi.num_tracks " + midi.num_tracks);
			
			//loop loading and processing each midi track's data
			
			for (int i=0; i < midi.num_tracks; i++) { //includes drum_tracks				
				if (midi.getTrackData(i)) {
					System.out.println("getTrackData() track " + i + " OK");
				} else {
					System.out.println(midi.error_message);
					midiOK = false;
					return null;
				}
				
				List<Integer> notes_ar = new ArrayList<Integer>(); //stores NoteOn/Off info as single values 
				
				if (midi.readMidi(song, notes_ar)) {
					System.out.println("readMidi() track " + i + " OK");
				} else {
					System.out.println("LaunchActivity problem in readMidi()"); //TODO set up error message in Midi class
					midiOK = false;
					return null;
				}
				
				if (midi.formatNotes(song, notes_ar)) {
					System.out.println("formatNotes() track " + i + " OK");
				} else {
					System.out.println("LaunchActivity problem in formatNotes()");
					midiOK = false;
					return null;
				}								
			}
						
			System.out.println("******** this MIDI song has " + song.tracks.size() + " instrument tracks *********");
			System.out.println("and " + song.drum_tracks.size() + " drum tracks *********");
			
			midiOK = true;
			//midi = null; //explicit attempt to reclaim memory			
			
			return null;
		}
		
		@Override
		   protected void onPostExecute(String result) {
		      super.onPostExecute(result);
		      
		      ProgressBar spinner = (ProgressBar) dialog.findViewById(R.id.midiProgSpnr);
		      spinner.setVisibility(View.GONE);
		      
		      Button OKBtn = (Button) dialog.findViewById(R.id.midiOKbtn);
		      OKBtn.setVisibility(View.VISIBLE);	
		      
		      if (midiOK) {
		    	  TextView title = (TextView) dialog.findViewById(R.id.midiTitle);
			      title.setText("MIDI FIle Converted for DittyBot");
			      
				  TextView tv1 = (TextView) dialog.findViewById(R.id.midiTV1);
				  tv1.setText("This MIDI file was successfully converted");
				  
				  TextView tv2 = (TextView) dialog.findViewById(R.id.midiTV2);
				  tv2.setVisibility(View.VISIBLE);
				  tv2.setText("File size: " + midi.fileSize + " bytes");
				  
				  TextView tv3 = (TextView) dialog.findViewById(R.id.midiTV3);
				  tv3.setVisibility(View.VISIBLE);
				  tv3.setText("Format type: " +  midi.format_type);
				  
				  TextView tv4 = (TextView) dialog.findViewById(R.id.midiTV4);
				  tv4.setVisibility(View.VISIBLE);
				  tv4.setText("Tracks: " + midi.num_tracks);
				  
				  TextView tv5 = (TextView) dialog.findViewById(R.id.midiTV5);
				  tv5.setVisibility(View.VISIBLE);
				  tv5.setText("This file will now be loaded in the Song Editor   ");
				  
				  //load the instruments' patches & listeners
				  for (int i=0; i < song.tracks.size(); i++) {
					  //instmtInit(song.tracks.get(i));
					  song.tracks.get(i).instrument.loadInstrument();
				  }
					
				  for (int i=0; i < song.drum_tracks.size(); i++) { //there can be more than one drum track
					  //OK this one needs inner loop
			    	  for (int j=0; j < song.drum_tracks.get(i).drums.size(); j++) {
			    		  //drumInit(song.drum_tracks.get(i).drums.get(j));
			    		  
			    	  }
				  }	
					
				  int songLength = songLength();
				  System.out.println("getMidiAsync songLength " + songLength);
				
				  new songSave().execute();
				  
				  midi = null; //try to force reclaim of memory
		      }
		      else {
		    	  TextView title = (TextView) dialog.findViewById(R.id.midiTitle);
			      title.setText("Error converting MIDI FIle");
			      
				  TextView tv1 = (TextView) dialog.findViewById(R.id.midiTV1);
				  tv1.setText("There was a problem converting this MIDI file. The file may be corrupted." +
				  		"Exiting process.");
				  //TODO need more to handle this?
		      }						
			  	      
			OKBtn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {					
					dialog.dismiss();										
				}    		
	    	}); 		      
		}
	}	
	
	
//===== PLAYBACK FUNCTIONS =======================================================================================	
	
	private void playCtrl() {
		System.out.println("playCtrl()");
		
		if (playState == 0) { //not playing, so play it
			System.out.println("play ");			
			
			if (playLine.getVisibility() == View.GONE) { //bc hit midscreen and replaced by staticLine
				//playLine.xpos = bodySV.getScrollX();
				staticLine.setVisibility(View.GONE);
				playLine.setVisibility(View.VISIBLE);
			}
			
			//check if playLine past midscreen & center view if so
			//int midpt = (gv.screenWidth - (trkSize_px + song.tracks.get(0).led.width))/2; //divide body area in half
			/*
			if ((playLine.xpos - bodySV.getScrollX()) > midpt) {				
				int curr_y = bodyRL.getScrollY();
				//int delta_x = (int) ((playLine.xpos - bodySV.getScrollX()) - midpt);		
				int delta_x = (int) (bodySV.getScrollX() + midpt - playLine.xpos);
				System.out.println("past midpt delta_x " + delta_x);
				bodySV.scrollBy(delta_x, curr_y);				
			}
			*/
			//cue up the imod patches to start at right time 						
			playTime = playLine.xpos * zoom_factor;
			System.out.println("playTime " + playTime);
			
			for (int i=0; i < imods.size(); i++) {
				
				String ID = Integer.toString(imods.get(i).patchID);
				String setIndex = ID + "si";
				
				if (playTime == 0) { //just set all to 0 index
					PdBase.sendFloat(setIndex, 0);
					PdBase.sendBang(ID + "f"); //prime the first note
				}
				else {
					String arName = ID + "na";
					int arSize = PdBase.arraySize(arName);
					int stride = 5;
					
					float runtime = 0; //summed gap & durs			
					float[] time = new float[1]; //wonky, to work with that Pd readarray function to get single value
					int noteIndex = -1; //starting index of note data when found
					float newGap = -1; //adjusted gap if playback was paused in gap, or 0 if paused while playing
					boolean inGap = false; //true if playTime falls within gapTime
					float newDur = -1; //adjusted note duration if playback paused while note playing
					float phase = -1;  //0 to 1 phase value for imod phasor to start play in middle of a note 
					
					for (int j=0; j < arSize; j+=5) {
						
						PdBase.readArray(time, 0, arName, j, 1); //gapTime. precedes every note, even if 0 as often case for 1st note
						float gapTime = time[0];				
						runtime += gapTime; 
						
						if (runtime >= playTime) { //then playTime falls within gapTime of note					
							noteIndex = j;					
							inGap = true;
							newGap = (float) (runtime - playTime);
							break;
						}
						
						PdBase.readArray(time, 0, arName, j+3, 1); //dur
						float dur = time[0];				
						runtime += dur; 
						
						if (runtime >= playTime) { //then playTime falls within playing note					
							noteIndex = j;					
							newGap = 0; //so will pass to playing note immediately
							newDur = (float) (runtime - playTime);
							phase = 1 - (newDur/dur); //phasor sweeps 0 to 1					
							break;
						}
					}
					
					if (noteIndex != -1) { //check that a note was found				
						
						float[] noteData = new float[stride];
						PdBase.readArray(noteData, 0, arName, noteIndex, stride); //read original note data values
						float gapTime = noteData[0];				
						float arNum = noteData[1];				
						float numSamples = noteData[2];				
						float dur = noteData[3];				
						float rate = noteData[4];				
						
						//change values as calculated above
						gapTime = newGap;
						String setGap = ID + "g";
						PdBase.sendFloat(setGap, gapTime);
						
						//set sample array
						String setAr = ID + "a";
						PdBase.sendFloat(setAr, arNum);
						
						//set # samples
						String setSamples = ID + "s";
						PdBase.sendFloat(setSamples, numSamples);
						
						if (!inGap) { //paused while note was playing					
							dur = newDur;					
							//set the phase
							String setPhase = ID + "phase";
							PdBase.sendFloat(setPhase, phase);
						}
						
						//set note dur
						String setDur = ID + "d";
						PdBase.sendFloat(setDur, dur);
						
						//set rate
						String setRate = ID + "r";
						PdBase.sendFloat(setRate, rate);
						
						//set the imod array index to start of next note
						float nextNote = noteIndex + stride; //1st index of next note's data						
						PdBase.sendFloat(setIndex, nextNote);		
						
						
					}
					else {
						System.out.println("playCtrl() note not found"); //TODO handle error
					}			
				}				
			}			
			//-----------------
			
			PdBase.sendBang("play");			
						
			startTime = System.nanoTime()/1000000; //to get in ms	
			startUiLoop();
			playState = 1;			
			playBtn.setImageResource(R.drawable.pauser);						
			
		}
		else if (playState == 1) { //playing, so stop it
			System.out.println("pause");						
						
			PdBase.sendBang("pause");
			stopUiLoop();			
			endTime = System.nanoTime()/1000000;
			playTime += endTime - startTime;	
			playLine.xpos = playTime/zoom_factor;
			System.out.println("playTime: " + playTime + " msecs");
			System.out.println("bodySV.getScrollX() " + bodySV.getScrollX() + " playLine.xpos " + playLine.xpos);
			playState = 0;			
			playBtn.setImageResource(R.drawable.playbtnr);			
		}
		else if (playState == 2) { //at the end
			rewind();
			playCtrl();
		}
	}
	
	private void playCtrlOLD() {
		System.out.println("playCtrl()");
		
		if (playState == 0) { //not playing, so play it
			System.out.println("play ");
			
			System.out.println("song.length " + song.length + " bodyRL width " + bodyRL.getWidth());
			
			//check if playLine past midscreen & center view if so
			/*
			int midpt = (gv.screenWidth - (trkSize_px + song.tracks.get(0).led.width))/2; //divide body area in half
			if ((playLine.xpos - bodySV.getScrollX()) > midpt) {
				int curr_y = bodyRL.getScrollY();
				int delta_x = (int) ((playLine.xpos - bodySV.getScrollX()) - midpt);				
				bodySV.scrollBy(delta_x, curr_y);				
			}			
			*/
			PdBase.sendBang("play");			
			
			startUiLoop();			
			startTime = System.nanoTime();			
			playState = 1;			
			playBtn.setImageResource(R.drawable.pauser);						
			
		}
		else if (playState == 1) { //playing, so stop it
			System.out.println("pause");						
						
			PdBase.sendBang("pause");
			stopUiLoop();			
			endTime = System.nanoTime();
			playTime += endTime - startTime;			
			//System.out.println("playTime: " + playTime/nanodiv + " secs");
			System.out.println("bodySV.getScrollX() " + bodySV.getScrollX() + " playLine.xpos " + playLine.xpos);
			playState = 0;			
			playBtn.setImageResource(R.drawable.playbtnr);			
		}
		else if (playState == 2) { //at the end
			rewind();
			playCtrl();
		}
	}
	
	private void rewind() {
		System.out.println("rewind()");	
		
		staticLine.setVisibility(View.GONE);
		
		playTime = 0;
		PdBase.sendBang("pause"); //pauseRcvr resets imod patch to 0 time off playTime value	
		stopUiLoop();				
		playLine.xpos = 0;
		playLine.invalidate();
		playLine.setVisibility(View.VISIBLE);
		bodySV.scrollTo(0, bodySV.getScrollY());
		playState = 0;		
		playBtn.setImageResource(R.drawable.playbtnr);		
	}
	
	private void setPlayline(float x) { 		
		System.out.println("setPlayhead() x " + x + " bodySV x " + bodySV.getScrollX());		
		
		if (playState == 1) {
			PdBase.sendBang("pause");
			stopUiLoop();			
			playState = 0;			
			playBtn.setImageResource(R.drawable.playbtnr);
		}	
		
		staticLine.setVisibility(View.GONE);
		
		playLine.xpos = x;
		playLine.invalidate();
		playLine.setVisibility(View.VISIBLE);		
	}
	
	private Handler playHandler = new Handler(); //fires at a set rate to animate/update UI
	Runnable uiTimer = new Runnable() {
		@Override 
	    public void run() {
			updateUI();
			playHandler.postDelayed(uiTimer, gv.uiLoop_ms); //currently set to update every 40 ms
	    }
	};	
	
	private void startUiLoop() {
		//uiTimer.run();
		prevTime = startTime;
		playHandler.postDelayed(uiTimer, gv.uiLoop_ms);
	}
	
	private void stopUiLoop() {
		playHandler.removeCallbacks(uiTimer);
	}
	
	
		
	//float tot_ms;
	//int xpos;	
	
	private void updateUI() { 
		/*		
		currTime = System.nanoTime()/1000000;
		int dx = (int) (((currTime - prevTime)/zoom_factor) + 0.5f);
		prevTime = currTime;
		//tot_ms = (currTime - startTime + playTime);  
		//xpos = (int) ((tot_ms/zoom_factor) + 0.5); //rounded to whole pixel value
		System.out.println("dx " + dx);
		
		int trkOffset = trkSize_px + song.tracks.get(0).led.width;
		int midpt = (gv.screenWidth - trkOffset)/2; //divide body area in half	
		
		if (playLine.getVisibility() == View.VISIBLE) {
			if ((playLine.xpos - bodySV.getScrollX()) < midpt) {
				playLine.xpos += dx;
				playLine.invalidate(); //forces redraw of playLine	
			}
			else {					
				playLine.setVisibility(View.GONE);				
				staticLine.xpos = playLine.xpos - bodySV.getScrollX() + trkOffset;
				staticLine.setVisibility(View.VISIBLE);								
			}
		}
		else {
			
			//int spos = (int) (xpos - playLine.xpos);
			//bodySV.scrollTo(spos, bodySV.getScrollY());
			bodySV.scrollBy(dx, bodySV.getScrollY());
		}
		
		/*		
		else { //at end	
			System.out.println("at end");
			playLine.invalidate();  //step once more since post incremented				
			playState = 2; //prime play button for full replay
			playBtn.setImageResource(R.drawable.playbtnr);
		}
		*/
		
	}
	
	private PdListener pauseRcvr = new PdListener.Adapter() { //likely will eliminate this
		@Override
		public void receiveBang(String source) {
			//System.out.println("pauseRcvr() " + source);
		}
		
	};
	
	private PdListener pauseRcvrOLD = new PdListener.Adapter() {
		@Override
		public void receiveBang(String source) {			
			
			double playTime_ms = playTime/1000000; //convert to milliseconds to match imod data
			
			String[] elements = source.split("p");
			int ID = Integer.parseInt(elements[0]); //pull out the imod patchID			
			
			if (playTime == 0) { //true if user hit rewind button, so just reset to beginning index				
				String setIndex = ID + "si";
				PdBase.sendFloat(setIndex, 0);
				PdBase.sendBang(ID + "f"); //prime the first note
				return;
			}
			
			String arName = ID + "na";
			int arSize = PdBase.arraySize(arName);
			int stride = 5;
			
			float runtime = 0; //summed gap & durs			
			float[] time = new float[1]; //wonky, to work with that Pd readarray function to get single value
			int noteIndex = -1; //starting index of note data when found
			float newGap = -1; //adjusted gap if playback was paused in gap, or 0 if paused while playing
			boolean inGap = false; //true if playTime falls within gapTime
			float newDur = -1; //adjusted note duration if playback paused while note playing
			float phase = -1;  //0 to 1 phase value for imod phasor to start play in middle of a note 
			
			for (int i=0; i < arSize; i+=5) {
				
				PdBase.readArray(time, 0, arName, i, 1); //gapTime. precedes every note, even if 0 as often case for 1st note
				float gapTime = time[0];				
				runtime += gapTime; 
				
				if (runtime >= playTime_ms) { //then playTime falls within gapTime of note					
					noteIndex = i;					
					inGap = true;
					newGap = (float) (runtime - playTime_ms);
					break;
				}
				
				PdBase.readArray(time, 0, arName, i+3, 1); //dur
				float dur = time[0];				
				runtime += dur; 
				
				if (runtime >= playTime_ms) { //then playTime falls within playing note					
					noteIndex = i;					
					newGap = 0; //so will pass to playing note immediately
					newDur = (float) (runtime - playTime_ms);
					phase = 1 - (newDur/dur); //phasor sweeps 0 to 1					
					break;
				}
			}
			
			if (noteIndex != -1) { //check that a note was found				
				
				float[] noteData = new float[stride];
				PdBase.readArray(noteData, 0, arName, noteIndex, stride); //read original note data values
				float gapTime = noteData[0];				
				float arNum = noteData[1];				
				float numSamples = noteData[2];				
				float dur = noteData[3];				
				float rate = noteData[4];				
				
				//change values as calculated above
				gapTime = newGap;
				String setGap = ID + "g";
				PdBase.sendFloat(setGap, gapTime);
				
				//set sample array
				String setAr = ID + "a";
				PdBase.sendFloat(setAr, arNum);
				
				//set # samples
				String setSamples = ID + "s";
				PdBase.sendFloat(setSamples, numSamples);
				
				if (!inGap) { //paused while note was playing					
					dur = newDur;					
					//set the phase
					String setPhase = ID + "phase";
					PdBase.sendFloat(setPhase, phase);
				}
				
				//set note dur
				String setDur = ID + "d";
				PdBase.sendFloat(setDur, dur);
				
				//set rate
				String setRate = ID + "r";
				PdBase.sendFloat(setRate, rate);
				
				//set the imod array index to start of next note
				float nextNote = noteIndex + stride; //1st index of next note's data
				String setIndex = ID + "si";
				PdBase.sendFloat(setIndex, nextNote);		
				
				//System.out.println("pauseRcvr DONE");
			}
			else {
				System.out.println("pauseRcvr(): noteIndex == -1"); //TODO handle error
			}				
		}		
	};
	
	private PdListener doneRcvr = new PdListener.Adapter() {
		@Override
		public void receiveBang(String source) {			
			System.out.println("doneRcvr() " + source);
			
			String[] elements = source.split("d");
			int ID = Integer.parseInt(elements[0]);
			if (ID == lastImodID) {
				System.out.println("Song finished! imodID: " + ID);
				
				stopUiLoop();
				endTime = System.nanoTime()/1000000;
				playTime += endTime - startTime;			
				System.out.println("playTime: " + playTime + " msecs");
				System.out.println("bodySV.getScrollX() " + bodySV.getScrollX() + " playLine.xpos " + playLine.xpos);
				playState = 2; //denotes at end so when play button is hit rewind gets called first		
				playBtn.setImageResource(R.drawable.playbtnr);	
			}			
		}		
	};
//=================================================================================================================
		
	private void fileChooser(final String dirName, final PromptRunnable postrun) {
				
		final String dirPath = gv.extStorPath + "/DittyBot/" + dirName; //TODO restricts to DittyBot dir. may want to make more general			
		
    	//final Dialog dialog = new Dialog(this);  
    	final Dialog dialog = new Dialog(this, 666);
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);  
    	//dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x33FFFFFF));
    	dialog.setContentView(R.layout.filechsrui); //adapt existing layout for this
    	
    	TextView title = (TextView) dialog.findViewById(R.id.fchsrTitleTV);
    	title.setText("Select a file to load:");
    	
    	Button cancelBtn = (Button) dialog.findViewById(R.id.fchsrCloseBtn);
    	cancelBtn.setText("Cancel");
    	
    	//hide buttons except for cancelBtn
    	ImageButton upBtn = (ImageButton) dialog.findViewById(R.id.fchsrUpBtn);
    	upBtn.setVisibility(View.GONE);    	
    	Button newBtn = (Button) dialog.findViewById(R.id.fchsrNewBtn);
    	newBtn.setVisibility(View.GONE);    	
    	Button pasteBtn = (Button) dialog.findViewById(R.id.fchsrPasteBtn);
    	pasteBtn.setVisibility(View.GONE);    	
    	Button helpBtn = (Button) dialog.findViewById(R.id.fchsrHelpBtn);
    	helpBtn.setVisibility(View.GONE);
    	
    	ListView lv = (ListView) dialog.findViewById(R.id.filechsrLV);
    	//lv.setBackgroundColor(gv.scrollColor);
    	// FileAdapter is a custom adapter class below
    	List<String> fileList = new ArrayList<String>(); //wonky to pass here instead of making the list in the adapter but roll with it for super class constructor   	
    	lv.setAdapter(new FileAdapter(this, android.R.layout.simple_list_item_1, R.id.fnameTV, fileList, dirPath));
    	
    	lv.setOnItemClickListener(new OnItemClickListener() {	
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,	long id) {				
				
				LinearLayout fitem = (LinearLayout) view;							
				TextView file = (TextView) fitem.getChildAt(1);
				String filename = (String) file.getText();
				
				dialog.dismiss();
								
				String fullPath = dirPath + "/" + filename;				
				
				File fobj = new File(fullPath);
				if (fobj.isDirectory()) {
					//TODO add drill down/up functionality					
				}				
				else if (fobj.isFile()) {						
					
					postrun.setValue(filename);
					postrun.run(); //brilliant effin trick here. essentially returns control to the calling function					
				}								 
			} 			      	
    	});	
    	
    	cancelBtn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
			}    		
    	});
    	
    	dialog.show();
	}

	//Scroll ops ============================================================================================
	
	//these next two set which scrollView acts as the master so both scroll together
	//! not currently used in latest setup, but keeping for the moment
	private void bodyScroll(boolean enable) {
		//System.out.println("noteBodyScroll() " + enable);
		if (enable) {
			bodySV.setScrollViewListener(this);
		}
		else {			
			bodySV.removeScrollViewListener(this);
		}		
	}
	
	private void sideScroll(boolean enable) {		
		if (enable) {
			sideSV.setScrollViewListener(this);
		}
		else {
			sideSV.removeScrollViewListener(this);
		}
	}
	
	//these get called when put .setScrollViewListener(this) on the OSV/MSV scrollviews
	@Override
	public void onScrollChanged(ObservableScrollView scrollView, int x, int y, int oldx, int oldy) {
		System.out.println("osco");
		
		//bodySV.scrollTo(bodySV.getScrollX(), sideSV.getScrollY());	
	}	
	
	@Override
	public void onScrollChanged(MaxScrollView scrollView, int x, int y, int oldx, int oldy) {	
		//System.out.println("oscm scrollUp " + scrollUp);
		//int ystep = 5;
		//if (scrollUp) sideSV.scrollBy(0, ystep);
		//else sideSV.scrollBy(0, -ystep);
		//synchs the noteBar scrollview to the mainMSV
		//String xstr = Integer.toString(x);
		//timeTV.setText(xstr);		
		//sideSV.scrollTo(0, bodySV.getScrollY());		
	}
	
	//==========================================================================================================
	
	public class FileAdapter extends ArrayAdapter<String> { //custom adapter
    	
    	//private List<String> fNames = null;
    	private String[] fnames;
    	private String fpath = null;  	
    	
    	// constructor
		public FileAdapter(Context context, int resource, int textViewResourceId, List<String> fileList, String path) {
			super(context, resource, textViewResourceId, fileList);	
						
			fpath = path;
			
			File fdir = new File(fpath);
			if(fdir.exists() && fdir.isDirectory()) {				
				if (fdir.list() != null) {
					fnames = fdir.list();					
				}
				for (int i=0; i < fnames.length; i++) { //wonky but OK it works
					fileList.add(fnames[i]);
				}	
			}
			else {
				//TODO deal with missing directory 
			}					
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {			
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View row = inflater.inflate(R.layout.file_item, parent, false);
			
			ImageView iv = (ImageView) row.findViewById(R.id.ficonIV);
			TextView tv = (TextView) row.findViewById(R.id.fnameTV);				
			
			tv.setText(fnames[position]);			
						
			File whatsit = new File(fpath + "/" + fnames[position]);
			if (whatsit.exists()) {
				if (whatsit.isDirectory()) {    				
					iv.setImageResource(R.drawable.folder48);
    			}
    			if (whatsit.isFile()) {    				    				
    				String[] temp = fnames[position].split("\\.");    				 
    				if (temp[temp.length-1].contentEquals("dbs")) { 
    					iv.setImageResource(R.drawable.scofile48);
    				}
    				else if (temp[temp.length-1].contentEquals("wav")) {
    					iv.setImageResource(R.drawable.tapefile4_48);
    				}
    				else if (temp[temp.length-1].contentEquals("mid")) {    					
    					iv.setImageResource(R.drawable.midi48);
    				}
    				else {
    					System.out.println("unrecognized file type"); // TODO handle with maybe a question mark img
    				}
    				//TODO add a midi icon image
    			}
			}						
			return row;			
		}    	
    }	
	
    class PromptRunnable implements Runnable { //nifty way to fake getting a return value from a dialog
    	private String v;
    	void setValue(String inV) {
    		this.v = inV;
    	}
    	String getValue() {
    		return this.v;
    	}
    	public void run() {
    		this.run();
    	}
    	
    	/**
    	 * cool little hack that makes it seems like in a function you can say getValue() from a dialog
    	 * and resume right on to next lines of code in the function. What really happens is the whole block
    	 * of code is sent into the dialog & gets executed when user provides input, but from a code readability
    	 * standpoint all the code is defined in the function that calls the dialog. Much more intuitive setup. 
    	 * See midiChooser() in this class for usage
    	 */
    }
    
//==== UTILITY FUNCTIONS ==========================================================================================
    
    

    //===============================================================================================
    
	@Override
    public void onStart() {
    	System.out.println("onStart() MainScreen");    	
    	super.onStart();
    }
    
    @Override
    public void onRestart() {
    	System.out.println("onRestart() MainScreen");    	
    	super.onRestart();    	
    }
    
    @Override
    public void onPause() {
		System.out.println("onPause() MainScreen");
		super.onPause();
    }
    
    @Override
    public void onStop() {
    	System.out.println("onStop() MainScreen");    	
    	super.onStop();     	
    	
    }
    
    @Override
	public void onResume() {
		System.out.println("onResume() MainScreen");		
		super.onResume();				
	}	
    
    @Override
	public void onDestroy() {
    	System.out.println("onDestroy() MainScreen"); //may not be called		
    	super.onDestroy();      	
    	
    }


}
