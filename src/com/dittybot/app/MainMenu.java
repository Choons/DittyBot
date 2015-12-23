package com.dittybot.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;


public class MainMenu extends Activity {	
	
	private GlobalVars gv;	
	private float px_scale; //scale factor to calculate dp's from
	
	static HorizontalScrollView menuSV;
	private LinearLayout menuLL;
		
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mainmenu);
		
		gv = ((GlobalVars)getApplicationContext());	
		
		px_scale = getResources().getDisplayMetrics().density;
		
		menuSV = (HorizontalScrollView) findViewById(R.id.mainmenuSV);
		menuLL = (LinearLayout) findViewById(R.id.mainmenuLL);			
		
		new LoadMenu().execute();
	}
	
	private class LoadMenu extends AsyncTask<Void, Void, List<MainMenuItem>> {
		
		ProgressBar spinner = (ProgressBar) findViewById(R.id.mmenuPB);
		TextView loadingTV = (TextView) findViewById(R.id.mmenuTV);
		
		@Override
		protected void onPreExecute(){
			
			spinner.setVisibility(View.VISIBLE);
		    loadingTV.setText("Loading Menu");
		    loadingTV.setVisibility(View.VISIBLE);			
		}
		
		@Override
		protected List<MainMenuItem> doInBackground(Void... params) {
						
			List<MainMenuItem> menuItems = new ArrayList<MainMenuItem>();
			
			String menuPath = gv.appPath + "/dittybot/mainmenu.txt";
			File menuFile = new File(menuPath);
			String textLine = null;
			String delimiter = "\\|";
			
			String optionName = null;
			String picName = null;
			int picWidth = -1;
			int picHeight = -1;
			String className = null;
			
			try {
				FileReader fileReader = new FileReader(menuFile);
				BufferedReader textReader = new BufferedReader(fileReader);			
				while ((textLine = textReader.readLine()) != null ) {				
					String[] elements = textLine.split(delimiter);
					optionName = elements[0];
					picName = elements[1];
					picWidth = Integer.parseInt(elements[2]);
					picHeight = Integer.parseInt(elements[3]);
					className = "com.dittybot.app." + elements[4];					
					
					final MainMenuItem panel = new MainMenuItem(MainMenu.this, optionName, picName, picWidth, picHeight,
							className, gv.screenHeight);	
					
					panel.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							
							gotonext(panel.className);
						}						  		
			    	});
													
					menuItems.add(panel); 
			    }
				textReader.close();
				fileReader.close();					
			}
			catch(IOException e) {
				e.printStackTrace();
				System.out.println("fileReader error in MainMenu.loadMenu()");			
				//TODO error handling			
			}	
			
			return menuItems;
		}
		
		@Override
		protected void onPostExecute(List<MainMenuItem> menuItems) {
			
			spinner.setVisibility(View.GONE);		    
		    loadingTV.setVisibility(View.GONE);		    
		    
		    for (int i=0; i < menuItems.size(); i++) {
		    			    	
		    	LinearLayout.LayoutParams menuParams = 
		    			new LinearLayout.LayoutParams(menuItems.get(i).maxWidth, menuItems.get(i).maxHeight);
				menuParams.gravity = Gravity.CENTER;

		    	if (i == 0) { //first menu panel, set left spacer, don't add left margin
		    		
		    		int spacerW = (gv.screenWidth - menuItems.get(i).maxWidth) / 2;
		    		Button spacerL = new Button(MainMenu.this);
		    		spacerL.setVisibility(View.INVISIBLE); //can't see it, but still takes up space
		    		menuLL.addView(spacerL);
		    		spacerL.getLayoutParams().width = spacerW;
		    				    		
		    	}
		    	else { //add left margin to interior menuItem's
		    		
		    		float f_maxWidth = menuItems.get(i).maxWidth;
		    		int margin = (int) (menuItems.get(i).mgnRatio * f_maxWidth);
		    		menuParams.leftMargin = margin;			    		
		    	}
		    	
		    	menuItems.get(i).index = i+1;
	    		menuItems.get(i).setLayoutParams(menuParams);
	    		menuLL.addView(menuItems.get(i));		    	
		    }
		    
		    //finally add right spacer to end of scrollview
		    int index = menuItems.size() - 1;
		    int spacerW = (gv.screenWidth - menuItems.get(index).maxWidth) / 2;
		    Button spacerR = new Button(MainMenu.this);
    		spacerR.setVisibility(View.INVISIBLE);
    		menuLL.addView(spacerR);
    		spacerR.getLayoutParams().width = spacerW;    		
		}
	}
	
	private void gotonext(String className) {
		
		Intent intent = new Intent().setClassName(this, className);
		startActivity(intent);
	}

}
