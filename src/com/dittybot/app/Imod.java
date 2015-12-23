package com.dittybot.app;

//utility class to hold details about loaded imod.pd patches

public class Imod {
	
	int patchID = -1; //the $0 ID of the imod.pd patch
	int arraySize = -1; //total # elements in imod array (stride 5)
	float startTime = -1; //start time of 1st note 
	int numNotes = -1; //number of notes the imod plays
	float endTime = -1; //
	
	//data for current note when paused
	/*
	int atNote = -1;
	
	float gapTime = -1;
	float sampleID = -1; //$0 ID of the sample.pd that plays the note stopped on
	float numSamples = -1; 
	float dur = -1;
	float rate = -1;
	*/
	
}
