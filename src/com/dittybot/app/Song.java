package com.dittybot.app;

/**
 * Songs are the top level abstraction of DittyBot
 * They are a collection of tracks of instrument or audio parts used in a song
 * 
 * Song files store:
 * song.name
 * song.info
 * 
 * the following track properties on each line:
 * offset in milliseconds (int)
 * instrument/audio (String) - the instrument.dirName or 'audio' for raw audio tracks
 * track file name (String)
 * volume - 0 - 100 (int)
 * pan/balance - 0 represents far left, 100 represents far right (int)
 */

import java.util.ArrayList;
import java.util.List;

public class Song {
	
	//variables stored in song file	
	public String name = ""; //name of song (as would appear on an album, for instance). up to 256 chars
	public String info = ""; //user defined info about song. up to 256 chars
	public double tempo = 120; //in BPM. The default value
	
	//runtime variables	
	public String fileName = ""; //compact name to store song on SD card. .dbs extension
	public int length = 0; //total song length in milliseconds. found by longest track TODO may want to store in song file?
	
	public List<Track> tracks; //instrument tracks
	public List<DrumTrack> drum_tracks;
	public List<AudioTrack> audio_tracks;
	
	public Song() {
		tracks = new ArrayList<Track>();
		drum_tracks = new ArrayList<DrumTrack>();
		audio_tracks = new ArrayList<AudioTrack>();
	}
}
