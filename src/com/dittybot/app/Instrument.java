package com.dittybot.app;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

import org.puredata.core.PdBase;
import org.puredata.core.PdListener;

import android.content.Context;

public class Instrument {
	
	private GlobalVars gv;	
	
	public int instmtNum = -1;
	public String name = null;
	public String dirName = null; //eh need this? it's the instmtNum.name	
	public int index = -1; //index in master instruments list where additional info & state found	
	public String status = "OK";
	private int verifCt = 0; //incremented each time verify the call back value sent from sample.pd	
	
	public Instrument(Context context, int inum) {				
		gv = ((GlobalVars)context.getApplicationContext());
		//seek a number match in master list
		boolean found = false; //make sure a valid instmtNum passed in. don't know how it ever couldn't be but..
		for (int i=0; i < gv.instruments.size(); i++) {
			if (inum == gv.instruments.get(i).instmtNum) { //match by number to master list entry				
				found = true;
				index = i;
				instmtNum = inum;
				name = gv.instruments.get(i).name;				
				dirName = instmtNum + "." + name;				
				break;
			}
		}
		if (!found) { //no instrument with that number. Could happen if a dbs file uses a custom one not installed
			status = "no installed instrument with number " + instmtNum + " was found";
			System.out.println(status);			
		}
	}	
	
	public void loadInstrument() {
		System.out.println("loadInstrument() " + instmtNum);
		
		if (status.contentEquals("OK")) {
			//check if need to load instrument samples
			if (gv.instruments.get(index).instances == 0) {	//zero instances means need to load the samples
				
				gv.instruments.get(index).instances++;
				
				//load the sample.pd patches that will hold the wav sample file data
				String samplePatch = gv.appPath + "/dittybot/patches/sample.pd";
				for (int i=0; i < gv.instruments.get(index).samples.length; i++) {
					try {
						gv.instruments.get(index).samples[i].patchID = PdBase.openPatch(samplePatch);
					} catch (IOException e) {						
						e.printStackTrace();
						System.out.println("loadInstrument(): error loading sample.pd patch");
						//TODO wow so a sample.pd loading problem would kill the app. Not sure how to handle this 
						return;
					}					
				}
				
				//now load the instrument samples into the sample.pd's
				for (int i=0; i < gv.instruments.get(index).samples.length; i++) {
					String openMsg = gv.instruments.get(index).samples[i].patchID + "-openfile"; 
		  			String fileName = gv.instruments.get(index).samples[i].fileName;						  			
		  			String filePath = "../instruments/" + dirName + "/samples/" + fileName;	//relative path						  			
		  			
		  			//listen for the sample.pd patch to broadcast back the numSamples loaded
		  			String nsmpls = gv.instruments.get(index).samples[i].patchID + "nsmpls";							  		
					gv.pdRcvr.addListener(nsmpls, numSamplesRcvr);		  			
		  			
		  			PdBase.sendMessage(openMsg, filePath);
				}
			}
			else if ((gv.instruments.get(index).instances > 0)) { //instrument samples should already be loaded
				gv.instruments.get(index).instances++; //so just increment the count of instances of the instrument
			}
		}
		else {
			System.out.println("loadInstrument() error: " + status);
			//TODO inform user to use a different instrument
		}		
	}
	
	public void loadInstrumentOLD() {
		System.out.println("loadInstrument() " + instmtNum);	
		
		//find the instrument in master list by number
		boolean found = false; //make sure a valid instmtNum passed in. don't know how it ever couldn't be but..		
		for (int i=0; i < gv.instruments.size(); i++) {
			if (instmtNum == gv.instruments.get(i).instmtNum) { //match by number to master list entry			
				System.out.println("instmtNum " + instmtNum + 
						" = gv.instruments.get(i).instmtNum " + gv.instruments.get(i).instmtNum);
				found = true;
				index = i;
				System.out.println("index " + index);
				name = gv.instruments.get(i).name;
				System.out.println("name " + name);
				dirName = instmtNum + "." + name;				
				break;
			}
		}	
			
		if (!found) { //no installed instrument with that number was found
			//TODO handle error
			System.out.println("loadInstrument(): no installed instrument with that number was found");
			return; //kick out
		}
		else {
			//check if need to load instrument samples
			if (gv.instruments.get(index).instances == 0) {	//zero instances means need to load the samples
				
				gv.instruments.get(index).instances++;
				
				//load the sample.pd patches that will hold the wav sample file data
				String samplePatch = gv.appPath + "/dittybot/patches/sample.pd";
				for (int i=0; i < gv.instruments.get(index).samples.length; i++) {
					try {
						gv.instruments.get(index).samples[i].patchID = PdBase.openPatch(samplePatch);
					} catch (IOException e) {						
						e.printStackTrace();
						System.out.println("loadInstrument(): error loading sample.pd patch");
						//TODO wow so a sample.pd loading problem would kill the app. Not sure how to handle this 
						return;
					}					
				}
				
				//now load the instrument samples into the sample.pd's
				for (int i=0; i < gv.instruments.get(index).samples.length; i++) {
					String openMsg = gv.instruments.get(index).samples[i].patchID + "-openfile"; 
		  			String fileName = gv.instruments.get(index).samples[i].fileName;						  			
		  			String filePath = "../instruments/" + dirName + "/samples/" + fileName;	//relative path						  			
		  			
		  			//listen for the sample.pd patch to broadcast back the numSamples loaded
		  			String nsmpls = gv.instruments.get(index).samples[i].patchID + "nsmpls";							  		
					gv.pdRcvr.addListener(nsmpls, numSamplesRcvr);		  			
		  			
		  			PdBase.sendMessage(openMsg, filePath);
				}
			}
			else if ((gv.instruments.get(index).instances > 0)) { //instrument samples should already be loaded
				gv.instruments.get(index).instances++; //so just increment the count of instances of the instrument
			}	
		}		
	}
		
    private PdListener numSamplesRcvr = new PdListener.Adapter() {		
		@Override
    	public void receiveFloat(String source, float x) {
			gv.pdRcvr.removeListener(source, numSamplesRcvr); 			
			System.out.println(source + " numSamples in instrument " + instmtNum + " file: " + x);
			
			String[] elements = source.split("n");			
			int id = Integer.parseInt(elements[0]); //pull the $0 out
			
			for (int i=0; i < gv.instruments.get(index).samples.length; i++) {
				if (id == gv.instruments.get(index).samples[i].patchID) { //and match it to sample
					if (x == gv.instruments.get(index).samples[i].numSamples) { //then verified expected data size 
						verifCt++;
						if (verifCt == gv.instruments.get(index).samples.length) {													
							System.out.println("Instrument " + instmtNum + " numSamples verified");
							/**
							 * this doesn't handle the case where the patch sends nothing. For instance,
							 * if the instrument has 6 samples, but only 5 load, there is no error fired.
							 * I think I could set a timer running once the sample loading process starts
							 * and if don't get this OK flag set after maybe 5 seconds then time it out
							 */
						}
					}
					else {
						status = "instmtNum " + instmtNum + " numSamples mismatch";
						System.out.println(status);
						//TODO handle error
					}
					break;
				}
			}			
		}
	};	
	
	public void closeInstrument() {
		
		gv.instruments.get(index).instances--; //decrement the instances field of the matching instrument
		
		if (gv.instruments.get(index).instances == 0) {
			//then close all the sample.pd patches & clear the samples list of instrument
			for (int i=0; i < gv.instruments.get(index).samples.length; i++) {
				PdBase.closePatch(gv.instruments.get(index).samples[i].patchID);
				gv.instruments.get(index).samples[i].patchID = -1;
			}
			
		}
		
		//gv = null;
		instmtNum = -1;
		name = null;
		dirName = null;
		verifCt = 0;
		status = "OK";
	}



}
