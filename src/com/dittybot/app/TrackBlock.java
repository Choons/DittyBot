package com.dittybot.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class TrackBlock extends View {
	
	private RectF block;
	private Paint paint;
	
	public int offsetH; //x - correlates to note start time
	public int offsetV; //y - to line up with track controls on left side of screen
	public int width; //width of block in whole pixels
	public int height; //height of block in whole pixels
	
	public int color = 0xFFFFFFFF; //default- shows if a problem with passing in colors
	int rVal;
	int gVal;
	int bVal;
	int step = 8;
	
	public TrackBlock(Context context, int topY, int ht, int start, int note, int dur, float zoom) {
		super(context);
		
		offsetV = topY;
		height = ht - 1; //simple way to delineate blocks vertically. may change
		
		//do math with double values & round to put back in to int values to try to avoid compounding int truncation loss of resolution 
		double dstart = start;
		double ddur = dur;
		double dzoom = zoom;
		
		double doffsetH = (dstart/dzoom) + 0.5; //add 0.5 so int truncation below rounds up or down properly
		offsetH = (int)doffsetH; 
		
		double dwidth = (ddur/dzoom) + 0.5;
		width = (int)dwidth;		
		
		if (note >= 96) { //blue -> blue/green		
			rVal = 0;
			gVal = 255 - ((note - 96) * step);					
			bVal = 255;			
		}		
		if (note < 96 && note >= 64) { //blue/green -> green				 			
			rVal = 0;
			gVal = 255;	
			bVal = 255 - ((95 - note) * step);
		}
		if (note < 64 && note >= 32) { //green -> green/red				
			rVal = 255 - ((note - 32) * step);			
			gVal = 255;
			bVal = 0;			
		}
		if (note < 32) { //green/red -> red						
			rVal = 255;
			gVal = 255 - ((31 - note) * step);
			bVal = 0;			
		}  
		
		color = Color.argb(255, rVal, gVal, bVal);
		
		paint = new Paint();
		paint.setColor(color); 		
		paint.setStyle(Paint.Style.FILL);
		
		block = new RectF();	    
		block.set(0, 0, width, height); //always set to origin, margins determine position
		
	}
	
	@Override
    protected void onDraw(Canvas canvas) {
		 super.onDraw(canvas);	     
		 
	     canvas.drawRect(block, paint);
	     
	}

}
