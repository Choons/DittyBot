package com.dittybot.app;

public class Drums {  //used in master list of available & loaded drums
	
	public int drumNum = -1;
	public String name = null;
	public String fileName = null; //file name of the wav drum sample
	public int patchID = -1; //of the sample.pd file the drum sample opened in
	public float sampleRate = -1; 
	public float numSamples = -1;    	
	public int instances = 0;   
	public String status = "OK"; //status string so can report any init errors. Check for "OK" before using
}
