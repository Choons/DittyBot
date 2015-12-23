package com.dittybot.app;

/**
 * test class to send an email with file attachment from within app
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;

public class EmailFileTest extends Activity {
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	System.out.println("EmailMidi onCreate()");
        super.onCreate(savedInstanceState); 
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.alertdlg); 		
        
        final Intent emailIntent = new Intent(Intent.ACTION_SEND);
        
        //Subject for the email
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "My Recording");
        //Body text
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Created with DittyBot");        
        
        //file path. *must preface with 'file://'
      	String fileName = "file://" + Environment.getExternalStorageDirectory().toString() 
      				+ "/DittyBot/Audio/04-The Clash-Rock The Casbah.ogg";
        //Mime type of the attachment 
        emailIntent.setType("audio/3gp");
        //Full Path to the attachment
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(fileName));
        
        //I just put it in an alertdlg as an example
        Button okBtn = (Button) findViewById(R.id.alertOKbtn);
        okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {					
				startActivity(Intent.createChooser(emailIntent, "Send email..."));			
			}
		});
        
	}
}
