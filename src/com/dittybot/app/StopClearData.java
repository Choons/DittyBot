package com.dittybot.app;

/**
 * little hack class that disallows user to clear the app data from
 * Settings->Applications->Manage Applications->DittyBot-> "Clear data" option.
 * Instead presents user with a "Manage Space" button that launches this Activity.
 * Prevents wiping of the unique appID generated on 1st run of app, as well as instrument samples.
 * Done by adding android:manageSpaceActivity=".StopClearData" to the application tag in manifest
 */

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class StopClearData extends Activity {	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	System.out.println("StopClearData onCreate()");
        super.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState); 
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.alertdlg);
		
		TextView title = (TextView) findViewById(R.id.alertTitleTV);
		title.setText("Restricted Area");
		
		TextView alert = (TextView) findViewById(R.id.alertMsgTV);
		alert.setText("DittyBot stores critical data here that cannot be modified by users.");
		
		Button okBtn = (Button) findViewById(R.id.alertOKbtn);
        okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {					
				System.exit(0); //should close the app completely as this is only activity that should be on stack			
			}
		});        
	}

}
