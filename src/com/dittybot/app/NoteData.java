package com.dittybot.app;

//little utility class to hold the note data as the imod.pd uses it

public class NoteData {	
	
	float start = -1; //millisecond note start time from dbs/song.tracks.notes format 
	float arNum = -1; //the $0 of the sample.pd used to play note
	float numSamples = -1; //number samples in the sample file used to play note
	float dur = -1; //duration of note in milliseconds 
	float rate = -1; //slew rate Pure Data phasor uses to play note at the correct pitch

}
