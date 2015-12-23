package com.dittybot.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class PlayLine extends View {
	
	private GlobalVars gv;
	
	private Paint paint; 
	public int color = 0xFF00FFFF;
	public float height;
	public float width;	
	public float offset;
	public float xpos = 0; //position of drawn playLine	
	public boolean isStatic = false;
	
	public PlayLine(Context context, int mwidth, int mheight, int moffset) {
		super(context);
		
		gv = ((GlobalVars)getContext().getApplicationContext());
		
		height = mheight;
		width = mwidth;	
		offset = moffset;
		
		paint = new Paint();
		paint.setColor(color);
		paint.setStrokeWidth(1);
	}
	
	public void setColor(int newcolor) {
		paint = new Paint();
		paint.setColor(newcolor);
		paint.setStrokeWidth(1);
	}
	
	@Override
    protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);		
		
		if (!isStatic) {
			float svxl = SongMixerOLD2.bodySV.getScrollX(); //left edge of screen
			float svxr = svxl + gv.screenWidth - offset - 1; //right edge of screen
			if (svxl > xpos) xpos = svxl;
			if (xpos > svxr) xpos = svxr;
			
			if (xpos >= width) xpos = width-1;
			if (xpos < 0) xpos = 0;			
						
			canvas.drawLine(xpos, 0, xpos, height, paint);			
		}
		else {
			canvas.drawLine(xpos, 0, xpos, height, paint);
		}				
	}
}
