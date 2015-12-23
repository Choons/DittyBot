package com.dittybot.app;

/**
 * This class deals with instrument tracks
 * 
 * Tracks are collections of score parts called Riffs that are played on the same instrument
 * or raw audio parts like vocals
 * 
 * First couple lines of a track file contain info about track
 * each successive line references a start time (offset) & riff or audio file depending on track type 
 * An audio track has start times & audio file (.wav mp3/ogg?) name pairs instead of riff file names  
 */

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.text.TextUtils;

public class TrackOLD extends RelativeLayout {	
			 	
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
	public int color = 0xFFFFFF; //set at run time. not stored in file	
	private boolean isOn = true; //set by ON/OFF toggle in track control (only set & used at runtime)
	
	public int atNote = 0;  //keeps track of total notes played	
	
	//UI	
	public int uiState = 0; //how should be drawn to screen. collapsed/expanded etc.
	public int _topY;
	public int _height;
	public int _width;
	public int _topMargin = 2;
	
	public ImageView imageView; //holds pic of track's loaded instrument
	public int iv_width; // = 100; //arbitrary choice, seems to be a good size
	public int iv_height; // = 100;
	
	public TextView textView;
	
	public TrackLED led; //UI on/off indicator
	
	
	public TrackOLD(Context context, int img_width, int img_height, int led_width) {		
		super(context);
		
		notes = new ArrayList<Integer>();				
		
		//UI		
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 
				LayoutParams.WRAP_CONTENT);
		params.topMargin = _topMargin; 
		this.setLayoutParams(params);
		this.setBackgroundColor(0xFF555555);	
		
		led = new TrackLED(context, img_height, led_width);
		this.addView(led);
		
		iv_width = img_width;
		iv_height = img_height;
		
		
		//add up components into single w & h values
		_height = _topMargin + iv_height;
		_width = iv_width;
		
		//set up the image view for the instmt pic
		imageView = new ImageView(context);	
		//imageView.setBackgroundColor(0xFF00FF00);
		//imageView.setPadding(8, 0, 0, 0);
		//imageView.setBackgroundResource(R.drawable.rndbtnbkggrn);
		//imageView.setBackgroundResource(R.drawable.rndbtnbkgred);
		
		RelativeLayout.LayoutParams iv_params = 
				new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
				LayoutParams.WRAP_CONTENT);
		iv_params.addRule(RelativeLayout.CENTER_VERTICAL);
		iv_params.width = iv_width;
		iv_params.height = iv_height;	
		iv_params.addRule(RelativeLayout.RIGHT_OF, led.getId());
		imageView.setLayoutParams(iv_params);		
		this.addView(imageView);	
		
		//		
		textView = new TextView(context);
		RelativeLayout.LayoutParams tv_params = 
				new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
				LayoutParams.WRAP_CONTENT);
		//tv_params.addRule(RelativeLayout.RIGHT_OF, imageView.getId());
		tv_params.addRule(RelativeLayout.CENTER_VERTICAL);
		tv_params.leftMargin = 8;	
		textView.setLayoutParams(tv_params);
		textView.setTextColor(0xFFFFFFFF);		
		textView.setMaxLines(1);
		textView.setSingleLine(true);
		textView.setEllipsize(TextUtils.TruncateAt.END); //likely never occurs
		textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		this.addView(textView);		
		
		this.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {					   		
				System.out.println("Track click ID: " + ID);
				SongMixerOLD2.trackNotesView(ID);
			}    		
    	});		
		
	}
	
	public void setIsOn(boolean state) { //toggle playable state on/off
		if (state) {
			isOn = true;
			led.setOn(true);
		}
		else {
			isOn = false;
			led.setOn(false);
		}
	}
	
	public boolean getIsOn() { //query playable state
		return isOn;
	}
}
