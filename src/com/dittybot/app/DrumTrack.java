package com.dittybot.app;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.widget.RelativeLayout;

public class DrumTrack {
	
	public String info = ""; //up to 256 characters. could put what written for/on. 1 byte in file TODO I think this needs to be added to the dbs file format
	public int volume = 50; //master track volume. 0 -> 100 percent
	public int pan = 50; //master track pan. 0 -> 100, 0=full left, 100=full right	
	public List<Drum> drums; //drum tracks can have many drum instruments in contrast to musical instrument tracks
	public boolean isOn = true;
	public int uiState = 0; //how should be drawn to screen. collapsed/expanded etc.
	
	public DrumTrack(Context context) {
		//super(context);
		
		drums = new ArrayList<Drum>();		
	}
	
	public void buildUI(float size, float led_width) {
		
		
	}
	
	public void destroyUI() {
		
	}
}
