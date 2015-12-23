package com.dittybot.app;

/**
 * called when the SD card isn't reporting that it's mounted
 */

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class SDCardError extends Activity {
	
	private TextView sdErrTitle;	
	private TextView sdErrMsg;
	private Button OKBtn;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	System.out.println("onCreate() SDCardError");
        super.onCreate(savedInstanceState);
        
        initGui();
        
	}
	
	
	private void initGui() {
		setContentView(R.layout.alertdlg);
		
		sdErrTitle = (TextView) findViewById(R.id.alertTitleTV);
		sdErrTitle.setText("SD Card Error");
		
		sdErrMsg = (TextView) findViewById(R.id.alertMsgTV);
		sdErrMsg.setText("There is a problem with the SD card on this device. " +
				"Press OK to close the app and ensure SD card is mounted and working properly");
		
		OKBtn = (Button) findViewById(R.id.alertOKbtn);
		OKBtn.setOnClickListener(onClickListener);
	}
	
	public OnClickListener onClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.alertOKbtn:			
				closeApp();
				break;
			
			}
		}
	};
	
	private void closeApp() {
		System.out.println("closeApp()");
		System.exit(0); //should close the app completely as this is only activity that should be on stack
	}

}
