package com.dittybot.app;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import org.puredata.core.PdBase;
import org.puredata.core.PdListener;

import com.dittybot.app.SongMixerOLD2.PromptRunnable;
import com.dittybot.app.SongMixerOLD2.getMidiAsync;
import com.dittybot.app.SongMixerOLD2.songLoad;
import com.dittybot.app.SongMixerOLD2.songSave;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;


public class SongMixer extends Activity {
	
	private GlobalVars gv;
	
	private GLSurf glView;
	
	private Song song = null;
	private int dfltSongNum = 0; //used for auto-generating numbered file names for new songs 
	
	private List<Integer> trackUiList; //track type/ID# stride 2. Keeps track of order to display tracks on screen. Allows user to move tracks around

	private List<Imod> imods = new ArrayList<Imod>(); //data about loaded imod.pd's
	int lastImodID = -1; //ID of the imod patch that ends latest/last
	
	private Midi midi = null;
	
	private float zoom_factor = 5; //zoom in & out on note data
	
	private TextView titleTV;
	private TextView timeTV;
	 
	private ImageView playBtn;
	private ImageView rewBtn;
	private ImageView recBtn;
	private ImageView addBtn;
	private ImageView loopBtn;
	private ImageView setgsBtn;
	private ImageView menuBtn;
	private ImageView helpBtn;
	
	private int playState = 0;	//0 = not playing, 1 = playing, 2 = at end of clip
	float playTime = 0; //current time song is cued to in milliseconds
	float startTime; //used with endTime to get playTime time with System.nanoTime()	
	float endTime;
	float currTime; //used in updateUI() to animate UI components
	float prevTime;	
	
	private float touchX; //meh. global since Android's stupid click listener has no position info
	private float touchY;
	private float prevX;
	private float prevY;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {   
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        gv = ((GlobalVars)getApplicationContext());
        System.out.println("gv.appPath " + gv.appPath);
        
        setContentView(R.layout.songmixer2);
        
        playBtn = (ImageView) findViewById(R.id.sm_PlayIV);
		playBtn.setOnClickListener(onClickListener);
		
		rewBtn = (ImageView) findViewById(R.id.sm_RewIV);
		rewBtn.setOnClickListener(onClickListener);
		
		recBtn = (ImageView) findViewById(R.id.sm_RecIV);
		recBtn.setOnClickListener(onClickListener);
		
		addBtn = (ImageView) findViewById(R.id.sm_AddIV);
		addBtn.setOnClickListener(onClickListener);
		
		loopBtn = (ImageView) findViewById(R.id.sm_LprIV);
		loopBtn.setTag("off"); //set looping to "off" on startup
		loopBtn.setOnClickListener(onClickListener);
		
		setgsBtn = (ImageView) findViewById(R.id.sm_GearIV);
		setgsBtn.setOnClickListener(onClickListener);
		
		menuBtn = (ImageView) findViewById(R.id.sm_MenuIV);
		menuBtn.setOnClickListener(onClickListener);
		
		helpBtn = (ImageView) findViewById(R.id.sm_HelpIV);
		helpBtn.setOnClickListener(onClickListener);
		
		
		songsDialog(); //on start prompt user to load a song
        
        
        //-----------------------------------------------------------------------------------------
        glView = (GLSurf) findViewById(R.id.sm_GLSurf);   
        glView.setOnTouchListener(new OnTouchListener() {
    		@Override
    		public boolean onTouch(View v, MotionEvent event) {
    			
    			if (glView.renderer != null) {
    				if (event.getAction() == MotionEvent.ACTION_DOWN) {
    					//System.out.println("glView ACTION_DOWN " + event.getX() +" "+ event.getY());
    					glView.renderer.moving = 0;
    					prevX = event.getX();        				
        				prevY = event.getY();
        				
        			}
        			else if (event.getAction() == MotionEvent.ACTION_MOVE) {         				
        				
        				final float dx = event.getX() - prevX;
        				final float dy = -(event.getY() - prevY); //to match the coord layout in GL
        				//System.out.println("glView ACTION_MOVE " + dx +" "+ dy);
        				if (Math.abs(dx) > 2 || Math.abs(dy) > 2) { //make it easier to discern taps
        					glView.renderer.moving = 1; //1= actively moving
        					prevX = event.getX();
            				prevY = event.getY();
            				glView.queueEvent(new Runnable() {
                                @Override
                                public void run() {
                                	glView.renderer.set_xy(dx, dy);
                                }
                            });
        				}
        				
        			}
        			else if (event.getAction() == MotionEvent.ACTION_UP) {
        				//System.out.println("glView ACTION_UP ");
        				if (glView.renderer.moving == 1) { 
        					glView.renderer.moving = 2; //2 = coasting to a stop after user has swiped
        				}
        				else if (glView.renderer.moving == 0) { //user didn't swipe so click it
        					touchX = event.getX();
        					touchY = event.getY();
        					glView.performClick();
        				}
        			}
    			}
    			
    			return true;
    		}
    		
    	});
        
        glView.setOnClickListener(new OnClickListener() { //setup to find out what is being clicked on
			@Override
			public void onClick(View v) {							
				
				glView.queueEvent(new Runnable() { //talk to the render thread
                    @Override
                    public void run() {
                    	
                    	final int code = glView.renderer.hitTest(touchX, touchY); //get a code ID for what/where
                    	
                    	if (code != -1) { //trying to make sure it waits for a return value
                    		runOnUiThread(new Runnable() { //and need this now to call functions back here
                                @Override
                                public void run() {
                                    
                                	testDlg(code);
                                }
                            });
                    	}
                    	else {
                    		System.out.println("nothing was found in glView click -> hitTest");
                    	}
                    	
                    }
                });
										
			}    		
    	}); 
        //--------------------------------------------------------------------------------------------
        
        
		
        
		
	}	//end onCreate()
	
	private OnClickListener onClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.sm_PlayIV:
				playCtrl();					
				break;
			case R.id.sm_RewIV:	
				rewind();								
				break;
			case R.id.sm_RecIV:			
				record();
				break;
			case R.id.sm_AddIV:			
				addTrack();
				break;			
			case R.id.sm_LprIV:				
				loopSet();
				break;
			case R.id.sm_GearIV:			
				settings();
				break;
			case R.id.sm_MenuIV:			
				menu();
				break;
			case R.id.sm_HelpIV:			
				help();
				break;
			
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
		iv.setImageResource(R.drawable.scofile256);
		
		TextView tv = (TextView) dialog.findViewById(R.id.radioBtnsTV);
		tv.setText("Choose a Song Option:  ");
		
		final RadioGroup rg = (RadioGroup) dialog.findViewById(R.id.radioBtnsRG);		
		
		RadioButton savedRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB1);
		savedRB.setText("  Load a saved Song");
		savedRB.setChecked(true);
		
		RadioButton newRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB2);
		newRB.setText("  Create a new Song");
		
		RadioButton hideRB = (RadioButton) dialog.findViewById(R.id.radioBtnsRB3);
		hideRB.setVisibility(View.GONE);
		
		Button okBtn = (Button) dialog.findViewById(R.id.radioBtnsOK);
		Button cancelBtn = (Button) dialog.findViewById(R.id.radioBtnsCncl);			
		
		okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				switch (rg.getCheckedRadioButtonId()) {
				
		    	case R.id.radioBtnsRB1:	//Load a saved Song	    		
		    		dialog.dismiss();
		    		songChooser();
		    		break;
		    	case R.id.radioBtnsRB2:	//Create a new Song	    		
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
	
	private void songChooser() {	
		
		fileChooser("Songs", new PromptRunnable() { //"Songs" is directory name			
			public void run() {													
				new songLoad().execute(this.getValue()); //getValue() returns the song file name of the song the user chose		
			}			
		});
	}
	
	public class songLoad extends AsyncTask <String, Integer, String> {	//receives the song.fileName
		
		@Override
		protected void onPreExecute() { 
			super.onPreExecute();
			
			trackUiList.clear(); //clear out the track display list for new song
			
			ProgressBar spinner = (ProgressBar) findViewById(R.id.sm_ProgSpnr);
		    spinner.setVisibility(View.VISIBLE);			
		}		
		
		@Override
		protected String doInBackground(String... params) {
			System.out.println("songLoad() " + params[0]);
			
			song = new Song(); //re-initialize for new song coming in
			
			String fileName = params[0];			
			String filePath = gv.extStorPath + "/DittyBot/Songs/" + fileName;
			
			File song_file = new File(filePath); //TODO maybe verify file exists etc.
			
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
					//int pan = in.read();
					//System.out.println("track.pan " + pan);					
					
					//now parse by track type -----------------------------
					
					if (type == 0) { 		//instrument track						
						
						Track track = new Track(SongMixer.this);
						
						track.info = trackinfo;
						track.volume = volume;
						//track.pan = pan;
						
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
						track.instrument = new Instrument(SongMixer.this, inum); 
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
						song.tracks.add(track);						
					}
					
					else if (type == 1) { //audio track
						//TODO needs full implement
						/*
						AudioTrack audio_track = new AudioTrack(SongMixerOLD2.this);						
						audio_track.info = trackinfo;
						audio_track.volume = volume;
						audio_track.pan = pan;
						
						song.audio_tracks.add(audio_track);
						*/
					}
					
					else if (type == 2) { //percussion track
						
						System.out.println("songLoad() parsing type=2 percussion track");
						DrumTrack drum_track = new DrumTrack(SongMixer.this);						
						drum_track.info = trackinfo;
						drum_track.volume = volume;
						//drum_track.pan = pan;
						
						int numDrums = in.read(); //subtracks, 1 for each perc instmt in drum track
						System.out.println("numDrumTks " + numDrums);
						
						for (int i=0; i < numDrums; i++) {									
							//1 byte for drum number code
							int dNum = in.read();
							
							Drum drum = new Drum(SongMixer.this, dNum);
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
	
	private void songNew() {
		System.out.println("songNew()");	
		
		String songsDir = gv.extStorPath + "/DittyBot/Songs/"; //top level dir for all songs
		
		song = null;
		song = new Song(); //re-initialize the song object
		
		//auto-generate a default date-based + numbered file name. user can accept or enter own name 		
		Date date = new Date();    	
    	SimpleDateFormat sdf = new SimpleDateFormat("MMddyy", Locale.ENGLISH);    	
    	String datestr = sdf.format(date);    	
    	
    	dfltSongNum++; //number to append to default clip name. TODO need to add a check for if day changed
    	
    	String dfltFileName = "Song" + datestr + "_" + dfltSongNum;
    	
    	//check that the default file name isn't already a file
    	String checkPath = songsDir + dfltFileName; 
    	System.out.println(checkPath);
    	File checkFile = new File(checkPath);
    	if (checkFile.exists()) {    		
    		songNew(); //loop recursively until hit a unique name
    	}
    	else { //default file name is not already in use so present to user
           	final Dialog dialog = new Dialog(this);  
        	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    		dialog.setContentView(R.layout.filenamerui);    		
    		
    		TextView title = (TextView) dialog.findViewById(R.id.filenmrTitleTV);
    		title.setText("Save New Song As:");
    		
    		TextView changePrompt = (TextView) dialog.findViewById(R.id.filenmrTV);
    		changePrompt.setText("Click to change file name:");
    		
    		final EditText nameBox = (EditText) dialog.findViewById(R.id.fileNameET);		
    		nameBox.setHint(dfltFileName); //keeps user from having to delete every letter upon changing
    				
    		Button okBtn = (Button) dialog.findViewById(R.id.fnmrSaveBtn);
    		Button cancelBtn = (Button) dialog.findViewById(R.id.fnmrCancelBtn);
    		
    		okBtn.setOnClickListener(new OnClickListener() {
    			@Override
    			public void onClick(View v) {
    				dialog.dismiss();
    				String dirName;
    				if (nameBox.getText().toString().contentEquals("")) { //true when user accepts default name					
    					dirName = nameBox.getHint().toString();
    				}
    				else {
    					dirName = nameBox.getText().toString();
    				}
    				
    				song.fileName = dirName;
    				
    				System.out.println("song.fileName: " + song.fileName);
    				
    				/**
    				 * 1/14/2016
    				 * OK this is just the stub that gets a name from user
    				 * Need to set up the rest of the song and save the song file
    				 */
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
	
	public class songSort extends AsyncTask <Void, Void, Boolean> {
		/**
		 * sorts all instrument notes and drum hits into as many imod patches (voices)
		 * as required to play all without "stepping on"/truncating each other		
		 **/
		
		List<Varray> varrays = new ArrayList<Varray>(); //Varrays are float lists with single float endTime value
		
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
															
							if (note >= loNote && note <= hiNote) { //then right sample for note								
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
			
			if (quickSort(notesList, lowIndex, hiIndex)) { //yeah, it's fuckin fast!
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
								lastImodID = imod.patchID; //used in doneRcvr() to detect end of song playback
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
	
	public class songDraw extends AsyncTask <String, Integer, String> {
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			//show the working/spinner
		    ProgressBar spinner = (ProgressBar) findViewById(R.id.smProgSpnr);
		    spinner.setVisibility(View.VISIBLE);	
			
		}
		
		@Override
		protected String doInBackground(String... params) {
			
			
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			
	        //remove the working/please wait spinner
	        ProgressBar spinner = (ProgressBar) findViewById(R.id.smProgSpnr);
		    spinner.setVisibility(View.GONE);
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
				//4 bytes DBSF file magic seq 
				out.write(dbsf); 
				
				//1 byte song.name length field					 
				byte nb = (byte) song.name.length(); //TODO make sure elsewhere user can't use more than 256 chars
				out.write(nb);			
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
					
					//1 byte track master volume
					byte vb = (byte) song.tracks.get(i).volume;
					out.write(vb);
					
					
					//1 byte pan
					/*
					 * 1/6/2015 removed pan field at track level & made it a per note field
					byte pb = (byte) song.tracks.get(i).pan;
					out.write(pb);
					*/
					
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
//==============================================================================================================	
	private void fileChooser(final String dirName, final PromptRunnable postrun) {
		
		final String dirPath = gv.extStorPath + "/DittyBot/" + dirName; //TODO restricts to DittyBot dir. may want to make more general			
		
    	final Dialog dialog = new Dialog(this);    	
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
    	lv.setAdapter(new FileAdapter(this, android.R.layout.simple_list_item_1, R.id.pic_textTV, fileList, dirPath));
    	
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
			View row = inflater.inflate(R.layout.pic_text_item, parent, false);
			
			ImageView iv = (ImageView) row.findViewById(R.id.pic_textIV);
			TextView tv = (TextView) row.findViewById(R.id.pic_textTV);				
			
			tv.setText(fnames[position]);			
						
			File whatsit = new File(fpath + "/" + fnames[position]);
			if (whatsit.exists()) {
				if (whatsit.isDirectory()) {    				
					iv.setImageResource(R.drawable.folder48);
    			}
    			if (whatsit.isFile()) {    				    				
    				String[] temp = fnames[position].split("\\.");    				 
    				if (temp[temp.length-1].contentEquals("dbs")) { 
    					iv.setImageResource(R.drawable.scofile256);
    				}
    				else if (temp[temp.length-1].contentEquals("wav")) {
    					iv.setImageResource(R.drawable.tapefile256);
    				}
    				else if (temp[temp.length-1].contentEquals("mid")) {    					
    					iv.setImageResource(R.drawable.midi256);
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
	
	
//===== PLAYBACK FUNCTIONS ====================================================================================	

	
	private void playCtrl() {		
		//glView.renderer.play = true;
		
		if (playState == 0) { //not playing, so play it
			System.out.println("play");
						
			playState = 1;			
			playBtn.setImageResource(R.drawable.pauser128);
		}
		else if (playState == 1) { //playing, so stop it
			System.out.println("pause");
			
			playState = 0;			
			playBtn.setImageResource(R.drawable.playbtnr128);
		}
		else if (playState == 2) { //at the end
			rewind();
			playCtrl();
		}
		
	}
	
	private void rewind() {
		System.out.println("rewind()");		
		
		playTime = 0;
		
		playState = 0;		
		playBtn.setImageResource(R.drawable.playbtnr128);
	}
	
	private void record() {
		
	}
	
	private void addTrack() {
		
	}
	
	private void loop() {
		
	}
	
	private void settings() {
		
	}
	
	private void menu() { //fired when user clicks the menu icon button
		System.out.println("menu()");
		
		final Dialog dialog = new Dialog(this); 
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
    	dialog.setContentView(R.layout.vscrolldlg);
    	
    	ImageView title_iv = (ImageView) dialog.findViewById(R.id.vscrollIV);
    	title_iv.setImageResource(R.drawable.menuwht256);
    	TextView title_tv = (TextView) dialog.findViewById(R.id.vscrollTV);
    	//title_tv.setText("Scroll and tap to select an option");
    	title_tv.setText("Menu");
    	
    	LinearLayout vscLL = (LinearLayout) dialog.findViewById(R.id.vscrollLL);
    	
    	
    	//populate the scrollview with menu option views TODO this should come from a file instead of hard coded so can add to it later
    	
    	LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    	params.setMargins(0, 1, 0, 1); //have to add programmatically as it ignores xml margin settings. bug. separates the items slightly
    	
    	View mainMenu = LayoutInflater.from(this).inflate(R.layout.pic_text_item, null);
    	ImageView mainMenuIV = (ImageView) mainMenu.findViewById(R.id.pic_textIV);
    	mainMenuIV.setImageResource(R.drawable.menugrn256);
    	TextView mainMenuTV = (TextView) mainMenu.findViewById(R.id.pic_textTV);
    	mainMenuTV.setText("Exit to Main Menu");
    	
    	mainMenu.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				Intent intent = new Intent(SongMixer.this, MainMenu.class);				 
				startActivity(intent);	   		    							
			}    		
    	});    	
    	
    	mainMenu.setLayoutParams(params);    	
    	vscLL.addView(mainMenu);
    	
    	View songNew = LayoutInflater.from(this).inflate(R.layout.pic_text_item, null);
    	ImageView songNewIV = (ImageView) songNew.findViewById(R.id.pic_textIV);
    	songNewIV.setImageResource(R.drawable.scofile256);
    	TextView songNewTV = (TextView) songNew.findViewById(R.id.pic_textTV);
    	songNewTV.setText("Create a New Song");
    	
    	songNew.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				dialog.dismiss();	
				songNew();		    							
			}    		
    	});
    	
    	songNew.setLayoutParams(params);
    	vscLL.addView(songNew);   
    	
    	View songLoad = LayoutInflater.from(this).inflate(R.layout.pic_text_item, null);
    	ImageView songLoadIV = (ImageView) songLoad.findViewById(R.id.pic_textIV);
    	songLoadIV.setImageResource(R.drawable.scofile256);
    	TextView songLoadTV = (TextView) songLoad.findViewById(R.id.pic_textTV);
    	songLoadTV.setText("Load a Saved Song");
    	
    	songLoad.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				dialog.dismiss();	    		
				songChooser();						
			}    		
    	});
    	
    	songLoad.setLayoutParams(params);
    	vscLL.addView(songLoad); 
 
    	View midiLoad = LayoutInflater.from(this).inflate(R.layout.pic_text_item, null);
    	ImageView midiLoadIV = (ImageView) midiLoad.findViewById(R.id.pic_textIV);
    	midiLoadIV.setImageResource(R.drawable.midi256);
    	TextView midiLoadTV = (TextView) midiLoad.findViewById(R.id.pic_textTV);
    	midiLoadTV.setText("Import a MIDI Song");
    	
    	midiLoad.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {							
				dialog.dismiss();	    		
				midiChooser();						
			}    		
    	});
    	
    	midiLoad.setLayoutParams(params);
    	vscLL.addView(midiLoad); 
    	
    	
    	dialog.show();
		
		//need a scrollable list w icon left + option text
		//Main Menu
		//Songs - new or saved
		//Midi - import. sub option under Songs dialog or on its own?
		
	}
	
	private void help() {
		
	}
	
	private void loopSet() { //toggle song playback looping
		String tag = loopBtn.getTag().toString();
		if (tag.contentEquals("off")) {	
			loopBtn.setTag("on");
			loopBtn.setImageResource(R.drawable.loopgrn128);
			
		}
		else if (tag.contentEquals("on")) {
			loopBtn.setTag("off");
			loopBtn.setImageResource(R.drawable.loopr128);
			
		}
	}
	
	private void testDlg(int code) {
		System.out.println("testDlg() " + code);
		
		final Dialog dialog = new Dialog(this); 
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
    	dialog.setContentView(R.layout.radiobtns);
    	
    	dialog.show();
    	
	}
	
	private void drawTEST() {
		System.out.println("songDraw()");
		/*
		final float[] vertexData = {
				// X, Y, Z, 
				// R, G, B, A
	            0.0f, 0.0f, 0.0f, 
	            1.0f, 0.0f, 0.0f, 1.0f,	            
	            0.0f, -50f, 0.0f,
	            1.0f, 0.0f, 0.0f, 1.0f,	            
	            50f, -50f, 0.0f, 
	            1.0f, 0.0f, 0.0f, 1.0f,
	            
	            0.0f, 0.0f, 0.0f, 
	            1.0f, 0.0f, 0.0f, 1.0f,
	            50f, -50f, 0.0f, 
	            1.0f, 0.0f, 0.0f, 1.0f,
	            50f, 0.0f, 0.0f, 
	            1.0f, 0.0f, 0.0f, 1.0f }; 
		
		glView.queueEvent(new Runnable() {
            @Override
            public void run() {
            	glView.renderer.init_tBlocks(vertexData);
            }
        });	
		*/
		
		
		String appPath = getFilesDir().getPath();
		
		String instmtsPath = getFilesDir().getPath() + "/dittybot/instruments";
		File instmtsDir = new File(instmtsPath);
		final String[] iImgPaths;
		if (instmtsDir.isDirectory()) {
			String[] instmtList = instmtsDir.list();
			iImgPaths = new String[instmtList.length];
			for (int i=0; i < instmtList.length; i++) {
				
				String[] elements = instmtList[i].split("\\.");
				iImgPaths[i] = appPath+"/dittybot/instruments/"+instmtList[i]+"/pics/"+elements[0]+".jpg";
				System.out.println(instmtList[i]);
        	} 
		}
		else iImgPaths = null; //hack to get around "variable may not have been initialized"
		
		//TODO these need to be calculated from screen density
		final float size = gv.TRKSIZE_DP;
		final float ledWidth = gv.LEDSIZE_DP;
		
		//question here is whether to loop multiple function calls inside one queueEvent block
		//or loop multiple q-events, each calling function one time?
		
		//this works, but ideal?
		glView.queueEvent(new Runnable() {
            @Override
            public void run() {
            	
            	for (int i=0; i < iImgPaths.length; i++) {
            		glView.renderer.init_tCtrl(iImgPaths[i], size, ledWidth);
            	}         	
            	
            }
        });	
		
	}
	
	
//===== MIDI FUNCTIONS ===========================================================================================
	
	private void midiChooser() {
		System.out.println("midiChooser()");
		
		fileChooser("Midi", new PromptRunnable() {
			
			public void run() {				
				String filename = this.getValue(); //the returned file path								
				new getMidiAsync().execute(filename); 				
			}			
		});
	}
	
	public class getMidiAsync extends AsyncTask <String, Integer, String> {	//receives the midi file name
		
		boolean midiOK; //flag for each step of the processing chain
		
		Dialog dialog = new Dialog(SongMixer.this); 
				
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
			
			song = new Song(); //re-initialize
			
			//create a default song.fileName, song.name & song.info from the MIDI file name		
			String midiFname = params[0]; //midi file name passed in on method call
			String rootName;
			String[] elements = midiFname.split("\\."); //discard the .mid part
			rootName = elements[0];
			
			//create a unique name that isn't a duplicate of any in Songs folder- Note: user is unaware of this happening until asked later if want to save the converted file
			String songName = rootName;
			boolean unique = false;
			int count = 1; //to concat a number to duplicates
			while (!unique) {				 
				String checkPath = gv.extStorPath + "/DittyBot/Songs/" + songName  + ".dbs";
				File checkFile = new File(checkPath);
				if (!checkFile.exists()) {
					unique = true; //exit loop, use name
				}
				else { //then a song with that name already exists
					System.out.println("getMidiAsync file name is a duplicate, creating new one");
					songName = rootName + "_" + Integer.toString(count);
					count++;
				}
			}			
			song.fileName = songName + ".dbs";
			song.name = songName;
			song.info = "This song file was translated from the MIDI file: " + midiFname;			
			
			//create a Midi class object 
			String midiFpath = gv.extStorPath + "/DittyBot/Midi/" + midiFname; 			
			midi = new Midi(SongMixer.this, midiFpath);
			
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
				  
					
				  int songLength = songLength();
				  System.out.println("getMidiAsync songLength " + songLength);
				
				  //new songSave().execute();	********* UNCOMMENT after testing				  				  
				  
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
					new songLoad().execute(song.fileName); //assumes songSave has completed
					dialog.dismiss();										
				}    		
	    	});	    	
	    	 		      
		}
	}	
	
//LIBPD =================================================================================================

	private PdListener pauseRcvr = new PdListener.Adapter() { //likely will eliminate this
		@Override
		public void receiveBang(String source) {
			//System.out.println("pauseRcvr() " + source);
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
				
				//stopUiLoop();
				endTime = System.nanoTime()/1000000;
				playTime += endTime - startTime;			
				System.out.println("playTime: " + playTime + " msecs");
				//System.out.println("bodySV.getScrollX() " + bodySV.getScrollX());
				playState = 2; //denotes at end so when play button is hit rewind gets called first		
				playBtn.setImageResource(R.drawable.playbtnr);	
			}			
		}		
	};

//=======================================================================================================
	
    @Override
    protected void onResume() {
        super.onResume();        
        if (glView != null) {
            glView.onResume();
        }
    }
 
    @Override
    protected void onPause() {
        super.onPause();       
        if (glView != null) {
            glView.onPause();
        }
    }

}
