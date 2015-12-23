package com.dittybot.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.text.TextUtils;

public class Breadcrumb extends FrameLayout {	
	
	private float height; //in pixels
	private float width;
	private float sharpy = 0.5f; //arbitrary. controls "pointiness" of triangular ends
	private float point; //x offset of arrow points on crumb graphic 
	//private float gap; //small gap between crumbs
	
	public String dirPath; //full file path
	
	public Breadcrumb(Context context, int color, int height_dp, int width_px, String dirName, String path) {
		super(context);		
		
		this.dirPath = path;
				
		//convert the dp value to pixels
		float scrDensity = getResources().getDisplayMetrics().density;
		//this.gap = 2*scrDensity; //arbitrary gap in dp
		height  = height_dp * scrDensity;				
		
		point = sharpy * height/2; //point of triangle		
		//this.width = width_px - gap;
		this.width = width_px;
		
		//add a breadcrumb graphic object
		Crumb crumb = new Crumb(context, color);
		this.addView(crumb);
		
		//add a textview for folder name
		TextView nameTV = new TextView(context);
		nameTV.setGravity(Gravity.CENTER);		
		nameTV.setMaxLines(1);
		nameTV.setEllipsize(TextUtils.TruncateAt.END);
		nameTV.setText(dirName);
		//nameTV.setPaintFlags(nameTV.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);
		FrameLayout.LayoutParams text_params = new FrameLayout.LayoutParams((int) (width - 2.5*point), LayoutParams.WRAP_CONTENT);
		text_params.gravity = Gravity.CENTER;		
		nameTV.setLayoutParams(text_params);
		this.addView(nameTV);
		
		//set layout size
		int int_hgt = (int) (height + 0.5f); //convert to int for layout
		int int_len = (int) (width + point + 0.5f);
		
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(int_len, int_hgt);
		lp.leftMargin = (int) (-point); //negative margin to make arrows "interlock" 
		this.setLayoutParams(lp);		
	}	
	

	
	public class Crumb extends View { //internal class to draw the breadcrumb arrow shape
		
		private Path path;
		private Paint paint;		
		private int drawColor;
		private int drawColor2; //slightly darker hue of baseColor to run a gradient with
		private int pressColor = 0xFF00FF00; //green when user touches crumb

		public Crumb(Context context, int color) {
			super(context);
			
			path = new Path();			
			paint = new Paint();
			
			//create a gradient color			 
			drawColor = color;
			float[] hsv = new float[3];
			Color.colorToHSV(drawColor, hsv);			
			hsv[2] *= 0.5f; // value component made darker
			drawColor2 = Color.HSVToColor(hsv); //secondary color for gradient	
			paint.setColor(drawColor2);
			
			//drawing path for breadcrumb arrow
			path.lineTo(point, height/2);
			path.lineTo(0, height);
			path.lineTo(width-2, height);
			path.lineTo(width-2 + point, height/2);
			path.lineTo(width-2, 0);	
			
			//set layout size
			int int_hgt = (int) (height + 0.5f); //convert to int for layout
			int int_len = (int) (width + point + 0.5f);
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(int_len, int_hgt);
			this.setLayoutParams(lp);
			
			this.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {		
					
					switch (event.getAction()){					
					case MotionEvent.ACTION_DOWN:						
						press();						
					}
						
					return false;
				}			
			});		
			
		}
		
		private void press() { //change color on press to give user feedback on which one they hit
			System.out.println("press()");
			drawColor = pressColor;
			this.invalidate();
		}		
		
		@Override
	    protected void onDraw(Canvas canvas) {
			 super.onDraw(canvas);				 
			 
			 paint.setShader(new LinearGradient(0, 0, 0, height, drawColor, drawColor2, Shader.TileMode.CLAMP));
			 
			 canvas.drawPath(path, paint);
		     
		}
		
	}

}
