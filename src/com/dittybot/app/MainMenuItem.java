package com.dittybot.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout.LayoutParams;

public class MainMenuItem extends ImageView {	
	
	//fields stored in mainmenu.txt
	public String optionName; //plain English option description
	public String imgName; //image file name (no extension as using a drawable)
	public int imgWidth; //width in pixels of unaltered image file
	public int imgHeight; //height in pixels of unaltered image file
	public String className; //class that contains the code to run the 
	
	//derived values
	public float scale = 0.9f; //arbitrary, draw menu image max 90% height of screen
	public int maxHeight; //largest height in pixes the menu image will be drawn on screen
	public int maxWidth; //largest width in pixes the menu image will be drawn on screen
	public float mgnRatio = .05f; //arbitrary choice % of menu img width 
	
	public int index; //position in the menu linear layout left to right
	
	//variables used to resize menu panel on fly
	//private int svx; //scrollview x value
	//private int prevx = 0; //only true if dn't give scrollview an initial offset
	//private int dx;	

	public MainMenuItem(Context context, String oName, String iName, int iWidth, int iHeight,
			String cName, int scrnHgt) {
		super(context);	
		
		optionName = oName;
		imgName = iName;
		imgWidth = iWidth;
		imgHeight = iHeight;
		className = cName;
		
		maxHeight = (int) (scrnHgt * scale); //TODO maybe add code so image never gets sized larger than original
		
		float f_maxHeight = maxHeight;
		float f_imgHeight = imgHeight;
		float imgRatio = f_maxHeight/f_imgHeight;	
		
		maxWidth = (int) (imgWidth * imgRatio);			
		
		int pic_id = context.getResources().getIdentifier(imgName, "drawable", context.getPackageName()); //how to use a string var to load a drawable
		this.setImageResource(pic_id);		
	}
	
	@Override
    protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		/**
		 * gave up on this for moment. This setup allows for resizing of the panel on fly.
		 * Want a semi-carousel thing where the menu panels look like they recede a bit as scrolled away from
		 * center. Architecture is here, but need to move on right now
		 */
		
		//System.out.println("MainMenuItem.onDraw()");		
		//System.out.println("menuLL w: " + MainMenu.menuLL.getWidth());
		//System.out.println(index + " " + this.getLeft());
		//System.out.println(MainMenu.menuSV.);
		
		//svx = MainMenu.menuSV.getScrollX(); //left edge of screen
		//System.out.println(svx);
		//dx = svx - prevx;
		//prevx = svx;	
		
		//this.getLayoutParams().height -= dx;
		//this.getLayoutParams().width -= dx;		
		//this.requestLayout(); //important to force redraw
	}

}
