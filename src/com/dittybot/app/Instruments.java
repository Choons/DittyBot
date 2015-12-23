package com.dittybot.app;

import java.util.ArrayList;
import java.util.List;

public class Instruments { //used in master list of available & loaded instruments
	
	public int instmtNum = -1;
	public String name = null;
	public int sampleRate = -1;     	
	public int instances = 0; //can be more than one track with same instrument. If goes to zero, then clear the samples list and close all the sample patches    	
	//public List<Sample> samplesOLD = new ArrayList<Sample>();  
	public Sample[] samples;
	public String status = "OK"; //status string so can report any init errors. Check for "OK" before using
}
