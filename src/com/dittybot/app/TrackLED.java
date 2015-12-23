package com.dittybot.app;

/**
 * simple rectangular green/red on/off rectangle like LED indicators on physical hardware
 */

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class TrackLED extends View {
	
	//private float WIDTH_DP = 8; //set width of block in dp *moved to SongMixer global
	public int width; //width of block in whole pixels
	public int height; //height of block in whole pixels		
	private int green = 0xFF00F060;
	private int red = 0xFFE01000;

	public TrackLED(Context context, int hgt, int wd) {
		super(context);
		
		this.setId(1); //just so can set Track IV RIGHT_OF
		
		
		width = wd;
		
		height = hgt;
		this.setBackgroundColor(green);
		
		RelativeLayout.LayoutParams params = 
				new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
				LayoutParams.WRAP_CONTENT);
		params.width = width;
		params.height = height;	
		this.setLayoutParams(params);
		
	}
	
	public void setOn(boolean state) {
		if(state) this.setBackgroundColor(green);
		else this.setBackgroundColor(red);
	}

}
