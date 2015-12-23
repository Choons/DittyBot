package com.dittybot.app;

//class for instrument sample details

public class Sample {
	
   	public int patchID = -1; //$0 of the sample.pd patch for this sample
	public String fileName = null;
	public float numSamples = -1;
	public int baseNote = -1; //the actual note the instrument was recorded playing	
	public float baseRate = -1; //sampleRate/numSamples & then tuned to perfect pitch. precalculated in info.txt
	public int loNote = -1;
	public int hiNote = -1;
}
