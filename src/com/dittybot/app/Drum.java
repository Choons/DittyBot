package com.dittybot.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.puredata.core.PdBase;
import org.puredata.core.PdListener;

import android.content.Context;
import android.widget.RelativeLayout;

public class Drum {
	
	private GlobalVars gv;
	
	public int drumNum = -1; //MIDI GM drum numbers
	public String name = null;
	public String dirName = null; //kind of redundant as built from drumNum & name
	public int index = -1;
	public String status = "OK"; //status flag	
	public List<Integer> score;
	public int atNote = 0;		
	public boolean isOn = true;
	public int color;
	
	
	public Drum(Context context, int dnum) {
		//super(context);		
		gv = ((GlobalVars)context.getApplicationContext());
		//find the drum in master list by number
		boolean found = false; //make sure a valid instmtNum passed in. don't know how it ever couldn't be but..		
		for (int i=0; i < gv.drums.size(); i++) {
			if (dnum == gv.drums.get(i).drumNum) { //match by number to master list entry			
				found = true;
				index = i;
				drumNum = dnum;
				name = gv.drums.get(i).name;
				dirName = drumNum + "." + name;	
				
				score = new ArrayList<Integer>(); //initialze here after match found
				
				break;
			}
		}
		if (!found) { //no installed drum with that number was found
			status = "no installed drum with number " + drumNum + " was found";
			System.out.println(status);
		}		 		
	}
	
	public void loadDrum() {
		System.out.println("loadDrum()");
		if (status.contentEquals("OK")) {
			//check if need to load drum sample
			if (gv.drums.get(index).instances == 0) {
				
				gv.drums.get(index).instances++;
				
				//load the sample.pd patch
				String samplePatch = gv.appPath + "/dittybot/patches/sample.pd";
				try {
					gv.drums.get(index).patchID = PdBase.openPatch(samplePatch);
				} catch (IOException e) {						
					e.printStackTrace();
					System.out.println("loadDrum(): error loading sample.pd patch");
					//TODO wow so a sample.pd loading problem would kill the app. Not sure how to handle this 
					return;
				}
				
				//now load the drum sample into the sample.pd array
				String openMsg = gv.drums.get(index).patchID + "-openfile"; 
	  			String fileName = gv.drums.get(index).fileName;						  			
	  			String filePath = "../drums/base_drums/" + dirName + "/" + fileName;	//relative path
	  			System.out.println(filePath);
	  			
	  			//listen for the sample.pd patch to broadcast back the numSamples loaded
	  			String nsmpls = gv.drums.get(index).patchID + "nsmpls";							  		
				gv.pdRcvr.addListener(nsmpls, numSamplesRcvr);		  			
	  			
	  			PdBase.sendMessage(openMsg, filePath);
			}
			else if ((gv.drums.get(index).instances > 0)) { //drum sample should already be loaded
				gv.drums.get(index).instances++; //so just increment the count of instances of the drum
			}
		}
		else {
			System.out.println("loadDrum() error: " + status);
			//TODO inform user to use a different drum
		}
	}
	
	public void loadDrumOLD() {
		System.out.println("loadDrum()");
		
		//find the drum in master list by number
		boolean found = false; //make sure a valid instmtNum passed in. don't know how it ever couldn't be but..		
		for (int i=0; i < gv.drums.size(); i++) {
			if (drumNum == gv.drums.get(i).drumNum) { //match by number to master list entry			
				found = true;
				index = i;				
				name = gv.drums.get(i).name;
				dirName = drumNum + "." + name;				
				break;
			}
		}
		if (!found) { //no installed drum with that number was found
			//TODO handle error
			System.out.println("loadDrum(): no installed drum with that number was found");
			return; //kick out
		}
		else {
			//check if need to load drum sample
			if (gv.drums.get(index).instances == 0) {
				
				gv.drums.get(index).instances++;
				
				//load the sample.pd patch
				String samplePatch = gv.appPath + "/dittybot/patches/sample.pd";
				try {
					gv.drums.get(index).patchID = PdBase.openPatch(samplePatch);
				} catch (IOException e) {						
					e.printStackTrace();
					System.out.println("loadDrum(): error loading sample.pd patch");
					//TODO wow so a sample.pd loading problem would kill the app. Not sure how to handle this 
					return;
				}
				
				//now load the drum sample into the sample.pd array
				String openMsg = gv.drums.get(index).patchID + "-openfile"; 
	  			String fileName = gv.drums.get(index).fileName;						  			
	  			String filePath = "../drums/base_drums/" + dirName + "/" + fileName;	//relative path
	  			System.out.println(filePath);
	  			
	  			//listen for the sample.pd patch to broadcast back the numSamples loaded
	  			String nsmpls = gv.drums.get(index).patchID + "nsmpls";							  		
				gv.pdRcvr.addListener(nsmpls, numSamplesRcvr);		  			
	  			
	  			PdBase.sendMessage(openMsg, filePath);
			}
			else if ((gv.drums.get(index).instances > 0)) { //drum sample should already be loaded
				gv.drums.get(index).instances++; //so just increment the count of instances of the drum
			}
		}
	}
	
    private PdListener numSamplesRcvr = new PdListener.Adapter() {		
		@Override
    	public void receiveFloat(String source, float x) {
			gv.pdRcvr.removeListener(source, numSamplesRcvr); 			
			System.out.println(source + " numSamples in drum file: " + x);
			
			String[] elements = source.split("n");			
			int id = Integer.parseInt(elements[0]); //pull the $0 out
			
			if (id == gv.drums.get(index).patchID) {				
				//gv.drums.get(index).instances = 1; //since this only gets fired when was 0 before
				System.out.println("Drum numSamples verified");
				/**
				 * this doesn't handle when something goes wrong and no value is sent back from the patch
				 * TODO - put a timeout timer on this so if not received in a few seconds can handle error
				 */
			}
			else {
				status = "drumNum " + drumNum + " numSamples mismatch";
				System.out.println(status);
				//TODO handle error
			}
		}
    };
	
	public void closeDrum() {
		
		gv.drums.get(index).instances--;
		
		if (gv.drums.get(index).instances == 0) {
			PdBase.closePatch(gv.drums.get(index).patchID);
		}
					
		//gv = null;
		drumNum = -1;
		name = null;
		dirName = null;
		status = "OK";
		score = null;
		atNote = 0;
		color = -1;
	}

}
