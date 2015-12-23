package com.dittybot.app;

import android.content.Context;

public class AudioTrack {
	
	public String info = ""; //up to 256 characters. could put what written for/on. 1 byte in file TODO I think this needs to be added to the dbs file format	
	public int volume = 50; //master volume. 0 -> 100 percent
	public int pan = 50; //master pan. 0 -> 100, 0=full left, 100=full right
	
	
	//run time variables
	public int ID = -1; //used in MainScreen to find & draw track
	public boolean isOn = true;
	
	//UI
	public int uiState = 0; //how should be drawn to screen. collapsed/expanded etc.
	public int _topY;
	public int _height;
	public int _width;
	public int _topMargin = 2;
	
	public AudioTrack(Context context) {
		
	}

}
