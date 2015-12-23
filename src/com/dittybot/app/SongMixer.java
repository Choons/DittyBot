package com.dittybot.app;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.puredata.core.PdBase;
import org.puredata.core.PdListener;

import com.dittybot.app.SongMixerOLD2.FileAdapter;
import com.dittybot.app.SongMixerOLD2.PromptRunnable;
import com.dittybot.app.SongMixerOLD2.songDraw;
import com.dittybot.app.SongMixerOLD2.songLoad;
import com.dittybot.app.SongMixerOLD2.songSort;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
		iv.setImageResource(R.drawable.scofile48);
		
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
				
		    	case R.id.radioBtnsRB1:		    		
		    		dialog.dismiss();
		    		songChooser();
		    		break;
		    	case R.id.radioBtnsRB2:		    		
		    		dialog.dismiss();
		    		//songNew();		    		
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
		System.out.println("songChooser()");
		
		fileChooser("Songs", new PromptRunnable() { //Songs is directory name
			
			public void run() {
				//get the returned file name
				String filename = this.getValue();
				System.out.println(filename);				
				new songLoad().execute(filename); //AsyncTask
				
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
					int pan = in.read();
					System.out.println("track.pan " + pan);					
					
					//now parse by track type
					
					if (type == 0) { 		//instrument track						
						
						Track track = new Track(SongMixer.this);
						
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
						drum_track.pan = pan;
						
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
//==============================================================================================================	
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
	
	private void menu() {
		
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
