package com.dittybot.app;

import java.util.List;

import android.content.Context;

public class Track {
	
	private Context context;
	
	public String info = ""; //up to 256 characters. could put what written for/on.	
	public int volume = 50; //0 -> 100 percent
	public int pan = 50; //0 -> 100, 0=full left, 100=full right	
	public List<Integer> notes;	//start/note/duration/volume/#dynbytes   stride 5
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
		
	}
	
	public void buildUI(float size, float led_width) {
		
		
	}
	
	public void destroyUI() {
		
	}

}
