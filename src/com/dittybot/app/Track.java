package com.dittybot.app;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;

public class Track {
	
	private Context context;
	
	public String info = ""; //up to 256 characters. could put what written for/on.	
	public int volume = 50; //track master volume 0 -> 100 percent
	//public int pan = 50; //0 -> 100, 0=full left, 100=full right	
	
	public List<Integer> notes;	//start time ms/note/duration ms/#vol pts/vol pts/#pan pts/pan pts/#bend pts/bend pts. no set stride, but at least 9
	//a note may have dynamic volume, pan, or pitch that can change over duration of the note
	//each dynamic field is preceded by a leader value that tells how many points of data the field contains
	//each dynamic field data point is a target|time pair that is slewed to linearly (so each actually takes two spots in list)
	
	public List<Integer> dyn; //dynamics info/instructions, if any. Parsed after the #dynbytes if non-zero 
		
	//run time variables
	public int ID = -1; //used in MainScreen to find & draw track
	public Instrument instrument; 
	public int offset = 0; //?? maybe 86 this.. amount of time before 1st note of track plays
	public int length = 0; // in milliseconds. calculated at run time, not in file 		
	public boolean isOn = true; //set by ON/OFF toggle in track control (only set & used at runtime)
	
	public int atNote = 0;  //keeps track of total notes played	
	
	//private float size; //pixel value used for drawing to screen
	//private float led_width;
	
	public Track(Context context) {		
		
		this.context = context;
		
		notes = new ArrayList<Integer>();
		
	}
	
	public void buildUI(float size, float led_width) {
		
		
	}
	
	public void destroyUI() {
		
	}

}
