package com.dittybot.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import android.content.Context;

/**
 * receives a midi file path, reads midi data and converts it into a DittyBot Song object
 * 11/7
 */

public class Midi {	
	
	private Context context;
	private GlobalVars gv;
	
	private String filepath; //maybe want separate public fileName as well?	
	public long fileSize;
	public short format_type; //midi format. 0=contains just one track, 1=contains multiple tracks to play simultaneously. 1st track is a tempo track w/ timing info, 2=multiple independent tracks with own play times
	public short num_tracks = -1; //number of tracks in song	
	public int time_type = -1; //0=PPQN, 1=SMPTE .. ?need this? going to have tempo in BPM in dbs format
	public int ppqn = -1; //pulses per quarter note. 
	public double fps = -1; //frames per second. only used in time_type = 1 SMPTE files
	public double subframes = -1; //"ticks" per frame. only used in time_type = 1 SMPTE files	
	public double tickms = -1; //fundamental time division that can be derived for both time_type files
	public double pbThldMs = 100; //threshold to help find when pitch bend value "flatlines" for "a while" at a single value. in msec. arbitrary TODO make settable
	public int pbValThld = 512; //threshold delta "jump" for pitch bend value to add another inflection point there. 1/8th the value between 2 MIDI notes. arbitrary TODO make settable	
	public double volThldMs = 100; //threshold to help find when dynamic volume value CC11 "flatlines" for "a while" at a single value.
	public int volValThld = 5; //threshold delta "jump" for volume. arbitrary TODO make settable	
	public double panThldMs = 100; //threshold to help find when dynamic pan/bal value CC8 or CC10 "flatlines" for "a while" at a single value.
	public int panValThld = 5; //threshold delta "jump" for pan. arbitrary TODO make settable
	private List<Long> track_ptrs; //track start & end byte pointers. so start at an index, the end value at index+1
	public List<ChanTrack> chanTracks; //list of raw Note On/Note Off lists for each channel of midi file	
			
	public String error_message = "error"; //use this so can access error info from main UI
	
	
	public Midi(Context incontext, String fpath) {	//constructor
		context = incontext;
		gv = ((GlobalVars)context.getApplicationContext());
		filepath = fpath;		
		track_ptrs = new ArrayList<Long>();			
		chanTracks = new ArrayList<ChanTrack>();
	}
	
	public class ChanTrack { //stores raw midi data separated by channel. assumes only 1 instrument per channel used in midi file		
		
		int channel;
		int mstrVol = 63; //master volume set by CC7 message. range 0 -> 127. default in middle
		
		List<Integer> notes = new ArrayList<Integer>(); 
		
		CCPoint volPt = new CCPoint();
		List<Integer> volList = new ArrayList<Integer>(); //holds each volume target point in value|time pairs
		
		CCPoint panPt = new CCPoint();
		List<Integer> panList = new ArrayList<Integer>(); //holds each pan target point in value|time pairs
		
		CCPoint pbPt = new CCPoint(); 
		List<Integer> bendList = new ArrayList<Integer>(); //holds each pitch bend target point in value|time pairs
		
		/**
		 * these objects are created at same time as the dbs tracks and added to the chanTracks list so the indices 
		 * correlate to those of the dbs tracks in the Song object
		 */
	}
	
	public class CCPoint { //convenience class for midi CC data. acts like a pointer moving along the file's data points		
		int value;		
		int ticks;		
		int direction = 0; //0 when data not yet changing, 1 when values going higher, -1 when values going lower
	}
	
	public boolean convert(Song song) { //full method sequence to read a midi file and convert to dbs file format
		
		boolean status = true;
		
		if (preProcess()) { 
			System.out.println("**preProcess() done**");			
			System.out.println("fileSize " + fileSize);
			System.out.println("format type: " + format_type);
			System.out.println("tracks found: " + num_tracks);
			System.out.println("time div: " + ppqn);	
		}
		else {
			status = false;
			return status;
		}
		
		for (int i=0; i < num_tracks; i++) { 
			
			List<Byte> track_bytes = new ArrayList<Byte>(); 
			
			if (getTrackData(i, track_bytes)) { //load all the track's raw data into memory for faster processing
				System.out.println("getTrackData() track " + i + " OK");
			} else {
				System.out.println(error_message);
				status = false;
				return status;
			}					
			
			if (readMidi(song, track_bytes)) { 
				System.out.println("readMidi() track " + i + " OK");
			} else {
				System.out.println("readMidi() track " + i + " failed"); 
				status = false;
				return status;
			}				
		}		
		
		if (formatNotes(song)) {
			System.out.println("formatNotes() OK");			
		}
		else {
			System.out.println(""); 
			status = false;
			return status;
		}
		
		return status;
	}
	
	public boolean preProcess() { //get the MThd header info & track start and end points in midi file
				
		boolean status = false;
		
		File midi_file = new File(filepath); //TODO maybe verify file exists etc.		
		
		try {
			RandomAccessFile in = new RandomAccessFile(midi_file, "r"); //handy as each read moves pointer
			
			fileSize = in.length();
			
			//check for the MThd header ID string
			String mthd = "";
			for (int i=0; i < 4; i++) {	        	
	        	char c = (char) in.read();
	        	mthd += c;	        	
	        }			
			if (!mthd.contentEquals("MThd")) {
				error_message = "not a valid midi file"; 
				return status; //kick out of processing the file
			}			
			
			int chunk_size = in.readInt(); //always 6 because the header chunk always contains the same 3 word values TODO check that it is 6 or handle error
			if (chunk_size != 6) {
				error_message = "Incorrect chunk size. File may be corrupted"; 
				return status; //kick out of processing the file
			}			
			
			format_type = in.readShort(); //MIDI file type 0, 1, or 2. see the MIDI File Format 
			if (format_type != 0 && format_type != 1 && format_type != 2) {
				error_message = "Incorrect format type. File may be corrupted"; 
				return status; //kick out of processing the file
			}
			
			if (format_type == 2) {
				//TODO these are very rare, and I'm not implementing it right now
				error_message = "This is a format type 2 MIDI file. This file type is not supported"; 
				return status; //kick out of processing the file
			}
			
			num_tracks = in.readShort(); //# tracks in song. 1 - 65,535. TODO verify empirically as often incorrect accdg. to research
			
			
			//---------determine timing scheme used in midi file--------------					
			byte[] time_divs = new byte[2];
			in.read(time_divs); 			
			
			//check whether 1st time div byte is a positive or negative value
			int timeval1 = time_divs[0]; //convert directly to int as signed byte so get possible values -127 to 127
			String timehex1 = String.format("%02X", time_divs[0]); //not used but might if go to a check top bit approach
			String timehex2 = String.format("%02X", time_divs[1]);
						
			if (timeval1 < 0) { //negative first time div byte means SMPTE (frames per second) timing scheme				
				time_type = 1;
				//FPS value should be -24, -25, -29, or -30. possibly -97 for 29.97
				fps = (double) Math.abs(timeval1); //TODO ? Should verify if is one of those four/five expected values
				subframes = (double) Integer.parseInt(timehex2, 16); //resolution per frame (ticks)
				
				double msecs_frame = 1000/fps;
				tickms = msecs_frame/subframes;
				System.out.println("SMPTE file tickms = " + tickms);				
								
				//TODO there's supposedly no tempo info in a SMPTE midi file so
				//will need a way for user to surmise what is a 1/4 or 1/8th note and match tempo manually
				//in both cases (PPQN & SMPTE) I need to know how many msec a "tick" represents
			}
			else {				
				time_type = 0;	
				ppqn = 0; //zero out all the bits so can set individually below
				//spec says use 15 bottom bits to derive value so take all 8 bits of 2nd byte
				int atbit = 0; //bit # of integer setting				
				for (int j=0; j < 8; j++) { 
					if ((time_divs[1] & 1<<j) > 0) { //then bit is set
						ppqn = setBit(atbit, ppqn); //set each bit in the int time_div value
					}
					atbit++;
				}				
				
				//then get bottom 7 bits of 1st byte
				for (int j=0; j < 7; j++) { 
					if ((time_divs[0] & 1<<j) > 0) { //then bit is set
						ppqn = setBit(atbit, ppqn);
					}
					atbit++;
				}
				
				//*** tickms for time_type = 0 PPQN files is set in buildTrack() when the tempo is discovered
			}
			
			//scan for the track start and end points and store in list
			//4D 54 72 6B are the hex codes for MTrk track ID. FF 2F 00 are the track end seq 			
			String hexstr = "";			
			boolean endtrkfound = false;
			
			while (in.getFilePointer() < in.length()) {				
				hexstr = String.format("%02X", in.readByte());				
				if (hexstr.contentEquals("4D")) { //M				
					hexstr = String.format("%02X", in.readByte());
					if (hexstr.contentEquals("54")) { //T
						hexstr = String.format("%02X", in.readByte());
						if (hexstr.contentEquals("72")) { //r
							hexstr = String.format("%02X", in.readByte());
							if (hexstr.contentEquals("6B")) { //k
								
								track_ptrs.add(in.getFilePointer()); //add the track start position
								
								//now scan for the FF 2F 00 end of track seq
								endtrkfound = false;
								while (!endtrkfound && in.getFilePointer() < in.length()) {
									hexstr = String.format("%02X", in.readByte());
									if (hexstr.contentEquals("FF")) {
										hexstr = String.format("%02X", in.readByte());
										if (hexstr.contentEquals("2F")) {
											hexstr = String.format("%02X", in.readByte());
											if (hexstr.contentEquals("00")) {
												
												endtrkfound = true;
												track_ptrs.add(in.getFilePointer()); //add the track end position
												System.out.println("track at: " + track_ptrs.get(track_ptrs.size() - 2) 
														+ " to " + track_ptrs.get(track_ptrs.size() - 1));
												//end position 1 past the track data so use while < end_ptr in loops 												
											}
										}
									}
								}								
							}
						}
					}
				}				
			}			
			
			//System.out.println("pointer " + in.getFilePointer()); //should equal file size
			
			if (num_tracks == track_ptrs.size()/2) { //then the #tracks field in file matches what was found
				System.out.println("tracks found: " + num_tracks); 
			}
			else {
				num_tracks = (short) (track_ptrs.size()/2); //go with own evidence, the #tracks field is known to be unreliable
				System.out.println("mismatch between number of tracks field and tracks found");
				//TODO maybe alert user there is something weird, but mainly just use the value determined by reading the data
			}
			
			in.close(); //close file stream
		}
		catch (IOException e) {			
			e.printStackTrace();
			error_message = "error opening midi file in preProcess()";
			return status;
		}
		
		status = true; //TODO this is totally too glossed over. Set more rigorously
		return status;
	}
	
	public boolean getTrackData(int tracknum, List<Byte> track_bytes) { //loads a track's raw midi data into a RAM array for faster processing
		System.out.println("-------getTrackData " + tracknum + " -------");
		
		boolean status = false;		
		
		int index = tracknum * 2; //start/end ptrs are stored as consecutive pairs in track_ptrs list		
		long start_ptr = track_ptrs.get(index);
		long end_ptr = track_ptrs.get(index+1);
		System.out.println("start_ptr " + start_ptr + " end_ptr " + end_ptr);
		
		int size_field = -1; //track data size field stored in the file. reputed to be often wrong
		
		File midi_file = new File(filepath);
		
		try {
			RandomAccessFile in = new RandomAccessFile(midi_file, "r");
			
			in.seek(start_ptr);	
			
			size_field = in.readInt(); //get the supposed data size stored in file
			
			while (in.getFilePointer() < end_ptr) {
				track_bytes.add(in.readByte()); //put the track data in RAM for faster processing
			}	
			
			in.close();			
		}
		catch (IOException e) {			
			e.printStackTrace();
			error_message = "error opening midi file in getTrackData()";			
			return status;
		}		
		
		System.out.println("reported data size: " + size_field + " size found " + track_bytes.size());
		//the size_field value is apparently notorious for being wrong so just ignore it & use what you find	
		
		/*
		ListIterator<Byte> pointer = track_bytes.listIterator();
		while (pointer.hasNext()) {
			String hexstr = String.format("%02X", pointer.next()); //prints out the hexcode couplets of the track. for testing
			System.out.println(hexstr);
		}
		*/
		
		status = true; //TODO this is totally glossed over. Set more rigorously
		return status;
	}	
	
	
	public boolean readMidi(Song song, List<Byte> track_bytes) { //translate midi data into my dbs format & add to a new track object
		System.out.println("readMidi() ");
		
		boolean status = false;	//flag for whether function ran with/without errors
		boolean sysex_open = false; //meh annoying fucking flag for possible divided midi sysex messages
		boolean tempofound = false; 		
		ListIterator<Byte> pointer = track_bytes.listIterator(); //to iterate over the track data stored in RAM earlier	
		Character evtType = null; //MIDI event type 8,9,A,B,C,D,E, or F. MIDI can use "running status" so need persistent variable
		int delta_ticks; //# of ticks since last message. a variable length quantity (VLQ)
		int total_ticks = 0; //total elapsed ticks from beginning of track
		int length; //data bytes length field in midi files. a variable length quantity (VLQ)
		String chan_hex; //low nibble used for channel in some events 		
		int channel = 1; //will be changed, but helps prevent that 'may not have been initialized' error
		int note = 0; 
		int vel = 0; //volume or expression in Note On	
		int pan = 63; //pan value 0=hard left, 127=hard right			
		int currPbVal = -1; //the value of the latest pitch bend message		
		
		
		while (pointer.hasNext()) {
			
			//--------- get Delta-Time ----------------------------------------------------------
			
			delta_ticks = checkVLQ(pointer); //actually pass the pointer object itself. nifty trick
			//System.out.println("delta_ticks " + delta_ticks);
			
			total_ticks += delta_ticks; //running total of ticks			

			//--------- get MIDI message --------------------------------------------------------------			
			
			//check if a status or data byte to catch incidences of "running status"
			byte midiByte = pointer.next(); 
			int byteType = 0; //0=data byte, 1=status byte
			if ((midiByte & 1 << 7) > 0) byteType = 1;
			boolean running = false;
			
			String hexstr = String.format("%02X", midiByte); //2 hex string codes in one byte
			
			//split out message and channel from the hex string pair
			if (byteType == 1) { //status/event byte							
				Character char1 = hexstr.charAt(0);	//get hex char at top "nibble" = message type
				Character char2 = hexstr.charAt(1);	//get hex char at low "nibble" = midi channel the message goes with	
				chan_hex = String.valueOf(char2);				
				channel = Integer.parseInt(chan_hex, 16) + 1; //convert hex string to int & add 1 so channels run 1-16
				
				evtType = char1; //set whenever a new status byte occurs, otherwise retain for possible "running status" data bytes to follow
			}
			else { //then it's a data byte
				running = true; //flag when a "running status" data byte is read instead of a status byte 
			}
			
			boolean found = false; //flag for whether an existing chantrack with matching channel was found
			
			
			//-------- Evaluate MIDI Messages -----------------------------------------------------------------	
			
			switch (evtType.charValue()) { 
			case '8': //Note Off (*many midi files just use a NoteOn with vel=0 instead of a Note Off message) 								
				if (running) note = midiByte & 0xFF; //use the "running status" byte that was already read
				else note = pointer.next() & 0xFF;
				vel = pointer.next() & 0xFF;
				
				for (int i=0; i < chanTracks.size(); i++) {
					if (chanTracks.get(i).channel == channel) {
						found = true;						
						chanTracks.get(i).notes.add(note);
						chanTracks.get(i).notes.add(vel);
						chanTracks.get(i).notes.add(total_ticks);
						break;
					}
				}				
				if (!found) { //could only happen if somehow there was no instrument assigned to the channel 
					System.out.println("readMidi() NoteOff: no chanTrack found");
					return false; //TODO need to alert user there's a problem with the midi file, trash anything created up to here
				}				
				//System.out.println("-- Note Off n=" + note + " ch=" + channel + " t=" + total_ticks); 								
				break;
			case '9': //Note On								
				if (running) note = midiByte & 0xFF;
				else note = pointer.next() & 0xFF;
				vel = pointer.next() & 0xFF; //velocity. in midi sometimes volume, sometimes triggers alternate	samples			
				
				for (int i=0; i < chanTracks.size(); i++) {
					if (chanTracks.get(i).channel == channel) {
						found = true;						
						chanTracks.get(i).notes.add(note);
						chanTracks.get(i).notes.add(vel);
						chanTracks.get(i).notes.add(total_ticks);
						break;
					}
				}				
				if (!found) { //could only happen if somehow there was no instrument assigned to the channel 
					System.out.println("readMidi() NoteOn: no chanTrack found");
					return false; //TODO need to alert user there's a problem with the midi file
				}					
				System.out.println("++ Note On n=" + note + " ch=" + channel + " vel=" + vel + " t=" + total_ticks); 								
				break;
			case 'A': //Note Aftertouch
				if (running) {} //not using so just move pointer to keep reading frame right
				else pointer.next(); //note
				pointer.next(); //pressure
				System.out.println("A Note Aftertouch");
				break;
			case 'B': //Controller. See list. many things like main volume 7 (0x07), balance 8 (0x08), pan 10 (0x0A)
				int ctrl; //controller type	code
				if (running) ctrl = midiByte & 0xFF;
				else ctrl = pointer.next() & 0xFF; 			
				int value = pointer.next() & 0xFF; //value 0-127
				for (int i=0; i < chanTracks.size(); i++) {
					if (chanTracks.get(i).channel == channel) {
						found = true;	
						if (ctrl == 7) { //master volume. Should only be set once in a proper midi file
							chanTracks.get(i).mstrVol = value;
							System.out.println("master volume set: " + value + " at tick: " + total_ticks);
						}
						if (ctrl == 11) { //expression volume, for dynamic volume changes that can happen while notes play
							volumeBend(i, value, total_ticks);
						}
						if (ctrl == 8 || ctrl == 10) { //dynamic balance or pan message. treating both the same
							panBend(i, value, total_ticks);
						}
						break;
					}
				}				
				//System.out.println("B Controller: ch=" + channel + " cc# " + ctrl + " val " + value);				
				break;
			case 'C': //Program Change ** Instrument assignment ** - making assumption here that this signals start of a new channel/track			
				int instmt_num; //MIDI instrument code # 0-127
				if (running) instmt_num = midiByte & 0xFF;
				else instmt_num = pointer.next() & 0xFF; 		
				ChanTrack chanTrack = new ChanTrack(); //store each midi channel's data separately in custom ChanTrack object
				chanTrack.channel = channel;
				chanTracks.add(chanTrack);				
				setupTrack(song, channel, instmt_num);	//set up a dbs song track
				System.out.println("C Program Change ch=" + channel + " instmt_num: " + instmt_num);				
				break;
			case 'D':
				if (running) {}
				else pointer.next(); //pressure
				System.out.println("D Channel Aftertouch");
				break;
			case 'E': //Pitch Bend - 14 bit value constructed from 2 bytes				
				byte lsb; //least significant bits. take lower 7
				if (running) lsb = midiByte;
				else lsb = pointer.next(); 
				byte msb = pointer.next(); //most significant bits. take lower 7				
				currPbVal = 0; //clear all bits
				currPbVal = (msb & 0xFF) << 7 | (lsb & 0xFF); //shift most signif. 7 bits left & concatenate the lsb's at end						
				for (int i=0; i < chanTracks.size(); i++) {
					if (chanTracks.get(i).channel == channel) {
						found = true;	
						pitchBend(i, currPbVal, total_ticks);
						break;
					}
				}
				if (!found) { //could only happen if somehow there was no instrument assigned to the channel 
					System.out.println("readMidi() PitchBend: no chanTrack found");
					return false; //TODO need to alert user there's a problem with the midi file, trash anything created up to here
				}					
				break; 
			case 'F': //System-wide messages
				//System.out.println("F Sysex or Meta Event");
				
				//Meta Events
				if (hexstr.contentEquals("FF")) { //1st byte of event ID 
					//System.out.println("FF Meta Event");
					
					String meta_hex2 = String.format("%02X", pointer.next()); //2nd byte of event ID
					
					//Sequence Number - FF 00 02 ss ss	 
					if (meta_hex2.contentEquals("00")) {
						//System.out.println("00");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("02")) {
							System.out.println("Sequence Number - FF 00 02 ss ss");						
							pointer.next();
							pointer.next();
						}
					}
					
					//Text Event - FF 01 length text
					if (meta_hex2.contentEquals("01")) {
						System.out.println("Text Event - FF 01 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Copyright Notice - FF 02 length text
					if (meta_hex2.contentEquals("02")) {
						System.out.println("Copyright Notice - FF 02 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Sequence/Track Name - FF 03 length text
					if (meta_hex2.contentEquals("03")) {
						System.out.println("Sequence/Track Name - FF 03 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Instrument Name - FF 04 length text
					if (meta_hex2.contentEquals("04")) {
						System.out.println("Instrument Name - FF 04 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
						//*may be used with the MIDI Channel Prefix Meta event to specify the MIDI channel that this instrument name description applies to
					}
					
					//Lyric - FF 05 length text
					if (meta_hex2.contentEquals("05")) {
						System.out.println("Lyric - FF 05 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Marker - FF 06 length text
					if (meta_hex2.contentEquals("06")) {
						System.out.println("Marker - FF 06 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Cue Point - FF 07 length text
					if (meta_hex2.contentEquals("07")) {
						System.out.println("Cue Point - FF 07 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Program Name - FF 08 length text
					if (meta_hex2.contentEquals("08")) {
						System.out.println("Program Name - FF 08 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//Device Name - FF 09 length text
					if (meta_hex2.contentEquals("09")) {
						System.out.println("Device Name - FF 09 length text");
						length = checkVLQ(pointer);	
						System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); 
						}
					}
					
					//MIDI Channel Prefix - FF 20 01 channel
					if (meta_hex2.contentEquals("20")) {
						//System.out.println("20");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("01")) {
							System.out.println("MIDI Channel Prefix - FF 20 01 channel");
							channel = pointer.next() & 0xFF; //TODO verify in range
						}
					}
					
					//MIDI Port - FF 21 01 MIDIport
					if (meta_hex2.contentEquals("21")) {
						//System.out.println("21");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("01")) {
							System.out.println("MIDI Port - FF 21 01 MIDIport");
							int midiport = pointer.next() & 0xFF; //TODO verify in range
						}
					}
					
					//End of Track - FF 2F 00
					if (meta_hex2.contentEquals("2F")) {
						//System.out.println("2F");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("00")) {
							System.out.println("End of Track - FF 2F 00");
							//TODO could verify here if where I think I should be
						}
					}
					
					//Tempo - FF 51 03 tt tt tt
					if (meta_hex2.contentEquals("51")) {						
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("03")) {
							System.out.println("$$$$$$$$ Tempo Found $$$$$$$$");
							
							byte[] tempobytes = new byte[3]; //advance through data whether using or not
							for (int i=0; i < 3; i++) {
								tempobytes[i] = pointer.next();
							}
							if (!tempofound) {
								//tempofound = true; //use only first tempo found in file. Have not figured out why some format type 1 files contain multiple tempo values in the "tempo map" first track
								/**
								 * 9/18/2015 making this set the tempo to latest/last value found (if more than one in file)								
								 */
								//TODO concat the 24 bits & derive tempo # from
								int tempoint = 0; //zero out the bits to set below
								int atbit = 0; //bit # of integer setting
								for (int i=2; i >= 0; i--) {
									for (int j=0; j < 8; j++) { 
										if ((tempobytes[i] & 1<<j) > 0) { //then bit is set
											tempoint = setBit(atbit, tempoint); //set each bit in the int time_div value
										}
										atbit++;
									}
								}							
								double tempo_dbl = (double) tempoint;
								if (time_type == 0) { //tempo message should only ever occur in time_type 0 files, but who knows
									//derive length of each tick in milliseconds
									double ppqn_dbl = (double) ppqn;
									tickms = (tempo_dbl/ppqn_dbl)/1000;	
									
									//
									
									
									//convert tempo to standard BPM value used in DittyBot dbs file format
									double micspersec = 1000000;
									song.tempo = (micspersec/tempo_dbl) * 60;									
									
									System.out.println("Tempo BPM: " + song.tempo + " tickms " + tickms);
								}
								else {
									System.out.println("found a Tempo meta event in a SMPTE file");
									//not sure what would do here as should already have a tickms value for SMPTE files
								}
							}							
						}
					}
					
					//SMPTE Offset - FF 54 05 hr mn se fr ff
					if (meta_hex2.contentEquals("54")) {
						//System.out.println("54");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("05")) {
							System.out.println("SMPTE Offset - FF 54 05 hr mn se fr ff");
							byte[] smptebytes = new byte[5]; 
							for (int i=0; i < 5; i++) {
								smptebytes[i] = pointer.next();
							}								
						}
					}
					
					//Time Signature - FF 58 04 nn dd cc bb
					if (meta_hex2.contentEquals("58")) {
						//System.out.println("58");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("04")) {
							System.out.println("Time Signature - FF 58 04 nn dd cc bb");
							byte[] tsigbytes = new byte[4]; 
							for (int i=0; i < 4; i++) {
								tsigbytes[i] = pointer.next();
							}								
						}
					}
					
					//Key Signature - FF 59 02 sf mi
					if (meta_hex2.contentEquals("59")) {
						//System.out.println("59");
						String meta_hex3 = String.format("%02X", pointer.next());
						if (meta_hex3.contentEquals("02")) {
							System.out.println("Key Signature - FF 59 02 sf mi");
							int sf = pointer.next() & 0xFF;	
							int mi = pointer.next() & 0xFF;
						}
					}
					
					//Sequencer Specific Event - FF 7F length data
					if (meta_hex2.contentEquals("7F")) {
						//System.out.println("Sequencer Specific Event - FF 7F length data");
						
						length = checkVLQ(pointer);	
						//System.out.println("length = " + length);
						
						for (int i=0; i < length; i++) {
							pointer.next(); //info for specific hardware. not used, step over it
						}
					}
					
				} //end Meta Events
				
				//Sysex Events - these are for specific device hardware. not used
				if (hexstr.contentEquals("F0") || hexstr.contentEquals("F7")) {
					System.out.println("F0/F7 Sysex Event");					
					
					if (hexstr.contentEquals("F0")) { //freshly opened sysex message
						sysex_open = true;
					}
					if (hexstr.contentEquals("F7")) { //continuation of a divided sysex message
						//? seems like same routine. loop over data & see if end-of-message byte
					}
					
					length = checkVLQ(pointer);	
					System.out.println("length = " + length);
					
					for (int i=0; i < length; i++) {
						String sysexhex = String.format("%02X", pointer.next());
						if (sysexhex.contentEquals("F7")) { //check if last byte has the F7 end-of-message byte
							sysex_open = false;
						} 
					}					
					
					/**
					 * these are kinda fuckin annoying
					 * see http://www.sonicspot.com/guide/midifiles.html bottom of page for info
					 * F0 is ID. Next byte gives length of data. If it's a regular undivided Sysex message
					 * there will be a F7 flag byte at end of data (the F7 is included in the data length
					 * value in the sysex header). If the F7 flag isn't there then
					 * it's a divided Sysex message (done if message is lengthy so won't lag playback). In
					 * that case, the next time a F7 byte is encountered signals the continuation of the
					 * message. Continues until hit a F7 byte at end of block's data count. Clear?? dafuq.
					 * So this BS requires a persistent flag to do it right (actually a fucking array list
					 * of 'sysex_open' flags since could have numerous divided sysex messages at any given
					 * moment) ALSO it looks like if the message is sufficiently long, then it will require
					 * multiple VLQ bytes in the sysex header to report how long the data payload of the message is
					 */
				}
				break;
			}
			
			
		} //end Main loop
		
		System.out.println("pointer " + pointer.previousIndex()); //TODO could do some error checking off this
		
		track_bytes.clear(); //might as well free up the memory here		
				
		status = true; //TODO completely glossed for testing. Make more robust
		return status;
		
	}
	
	private int msecToTicks(double msecs) { //returns #ticks equivalent to the msec value		
		int ticks = -1;
		
		if (tickms != -1) {
			double dticks = msecs/tickms + 0.5;
			ticks = (int) dticks;
		}
		else {
			System.out.println("problem in msecToTicks(). tickms has not been set"); //TODO should not be possible, but handle error
		}		
		
		return ticks;
	}
	
	private void pitchBend(int i, int currPbVal, int total_ticks) { //convert midi pitch bend messages into a set of linear inflection point targets
		
		int tickThld = msecToTicks(pbThldMs);
		
		//if this is the 1st bend data point just add it to bend list
		if (chanTracks.get(i).bendList.isEmpty()) {			
			chanTracks.get(i).bendList.add(currPbVal);
			chanTracks.get(i).bendList.add(total_ticks);
			
			chanTracks.get(i).pbPt.value = currPbVal;
			chanTracks.get(i).pbPt.ticks = total_ticks;
		}
		else {
			
			boolean thldFlag = false; //set true if either the time or value threshold exceeded 
			
			int dPb = currPbVal - chanTracks.get(i).pbPt.value; //previous value
			int dT = total_ticks - chanTracks.get(i).pbPt.ticks;
			
			//check whether exceed tick threshold to find when pitch bend value "flatlines" at same value for some time
			if (dT >= tickThld) {
				
				thldFlag = true; //set flag to skip bend direction analysis
				
				//need to add both endpoints of the "flatline" to the bendList for that track
				
				//make sure duplicate inflection points aren't stored
				int lastIndex = chanTracks.get(i).bendList.size() - 2; //stride 2 value|time pairs
				int lastValue = chanTracks.get(i).bendList.get(lastIndex);
				int lastTime = chanTracks.get(i).bendList.get(lastIndex+1);
				if (chanTracks.get(i).pbPt.value == lastValue && chanTracks.get(i).pbPt.ticks == lastTime) { //then the same point was added elsewhere								 
					//System.out.println("flat inflection point already added " + lastIndex + ":" + lastValue + "|" + lastTime);
				}
				else {
					//add the left endpoint of the "flatline"
					chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.value);
					chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.ticks);															
				}
				
				//create the right endpoint of the "flatline" - keep the same value pbPt already has
				chanTracks.get(i).pbPt.ticks = total_ticks; //but walk it to the new time
				chanTracks.get(i).pbPt.direction = 0; //reset any direction info
				
				chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.value); //create new inflection point with same bend value as previous point
				chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.ticks); //at the current time
										
				if (chanTracks.get(i).channel == 1) { //just for testing. remove 								
					int index1 = chanTracks.get(i).bendList.size() - 4; //two points, stride 2
					int index2 = index1 + 2;
					int lval = chanTracks.get(i).bendList.get(index1);
					int lticks = chanTracks.get(i).bendList.get(index1+1);
					int rval = chanTracks.get(i).bendList.get(index2);
					int rticks = chanTracks.get(i).bendList.get(index2+1);
					//System.out.println("FLATLINE inf. pts. L " + index1 + ":" + lval + "|" + lticks + " R " + index2 +":" + rval + "|" + rticks );
				}
										
			}
			
			//check if the new bend value is a "big jump" usually indicating the start of a new set of note-bending						
			if (Math.abs(dPb) >= pbValThld) {
				
				thldFlag = true; //set flag to skip bend direction analysis
				
				//make previous pbPt an inflection point - make sure not a duplicate
				int lastIndex = chanTracks.get(i).bendList.size() - 2; //stride 2 value|time pairs
				int lastValue = chanTracks.get(i).bendList.get(lastIndex);
				int lastTime = chanTracks.get(i).bendList.get(lastIndex+1);
				if (chanTracks.get(i).pbPt.value == lastValue && chanTracks.get(i).pbPt.ticks == lastTime) { //then the same point was added elsewhere								 
					//System.out.println("jump inflection point already added "  + lastIndex + ":" + lastValue + "|" + lastTime);
				}
				else {	
					chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.value); //the bend value just prior to the current bend value message
					chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.ticks); 																	
				}
				
				//and the latest point should also be recorded as an inflection point
				chanTracks.get(i).bendList.add(currPbVal);
				chanTracks.get(i).bendList.add(total_ticks);								
				
				chanTracks.get(i).pbPt.direction = 0; //unset the direction
				
				if (chanTracks.get(i).channel == 1) { //just for testing 								
					int index1 = chanTracks.get(i).bendList.size() - 4;
					int index2 = index1 + 2;
					int aVal = chanTracks.get(i).bendList.get(index1);
					int aTicks = chanTracks.get(i).bendList.get(index1+1);
					int bVal = chanTracks.get(i).bendList.get(index2);
					int bTicks = chanTracks.get(i).bendList.get(index2+1);
					//System.out.println("JUMP  inf. pts. A " + index1 + ":" + aVal + "|" + aTicks + " B " + index2 + ":" + bVal + "|" + bTicks );
				}								
			}
			
			//if neither threshold above was exceeded, check for directional changes of bend values
			if (!thldFlag) {
				
				if (chanTracks.get(i).pbPt.direction == 0) { //not yet set, need to determine direction
					
					if (currPbVal > chanTracks.get(i).pbPt.value) {
						chanTracks.get(i).pbPt.direction = 1; //bending higher
					}
					else if (currPbVal < chanTracks.get(i).pbPt.value) {
						chanTracks.get(i).pbPt.direction = -1; //bending lower
					}								
					
					//System.out.println("initial direction set: " + chanTracks.get(i).pbPt.direction);
				}
				else if (chanTracks.get(i).pbPt.direction == 1) { //bending up
					if (currPbVal < chanTracks.get(i).pbPt.value) { //inflection point - going back down
						chanTracks.get(i).pbPt.direction = -1;
						
						chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.value); 
						chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.ticks);
																
						if (chanTracks.get(i).channel == 1) { //just for testing 								
							int index = chanTracks.get(i).bendList.size() - 2;
							int val = chanTracks.get(i).bendList.get(index);
							int ticks = chanTracks.get(i).bendList.get(index+1);										
							//System.out.println("!!! Up to Down " + index + ":" + val + "|" + ticks);
						}										
					}							
				}
				else if (chanTracks.get(i).pbPt.direction == -1) { //bending down
					if (currPbVal > chanTracks.get(i).pbPt.value) { //inflection point - going back up
						chanTracks.get(i).pbPt.direction = 1;
						
						chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.value); 
						chanTracks.get(i).bendList.add(chanTracks.get(i).pbPt.ticks);
						
						if (chanTracks.get(i).channel == 1) { //just for testing 								
							int index = chanTracks.get(i).bendList.size() - 2;
							int val = chanTracks.get(i).bendList.get(index);
							int ticks = chanTracks.get(i).bendList.get(index+1);										
							//System.out.println("!!! Down to Up " + index + ":" + val + "|" + ticks);
						}	
					}	
				}								
			}							
			
			//set the pbPt to current value to "walk" the bend values
			chanTracks.get(i).pbPt.value = currPbVal;
			chanTracks.get(i).pbPt.ticks = total_ticks;
		}
		
	}
	
	private void volumeBend(int i, int value, int total_ticks) { //midi CC11. works the same as pitchbend(), see it for comments
		
		int tickThld = msecToTicks(volThldMs);		
		
		if (chanTracks.get(i).volList.isEmpty()) { //list is empty, just add the point
			chanTracks.get(i).volList.add(value);
			chanTracks.get(i).volList.add(total_ticks);
			
			chanTracks.get(i).volPt.value = value;
			chanTracks.get(i).volPt.ticks = total_ticks;
		}
		else {
			
			boolean thldFlag = false; //set true if either the time or value threshold exceeded 
			
			int dVol = value - chanTracks.get(i).volPt.value; //previous value
			int dT = total_ticks - chanTracks.get(i).volPt.ticks;			
			
			if (dT >= tickThld) {
				
				thldFlag = true; //set flag to skip direction analysis				
				
				int lastIndex = chanTracks.get(i).volList.size() - 2; //stride 2 value|time pairs
				int lastValue = chanTracks.get(i).volList.get(lastIndex);
				int lastTime = chanTracks.get(i).volList.get(lastIndex+1);
				if (chanTracks.get(i).volPt.value == lastValue && chanTracks.get(i).volPt.ticks == lastTime) { //then the same point was added elsewhere								 
					System.out.println("volumeBend() flat inflection point already added " + lastIndex + ":" + lastValue + "|" + lastTime);
				}
				else {					
					chanTracks.get(i).volList.add(chanTracks.get(i).volPt.value);
					chanTracks.get(i).volList.add(chanTracks.get(i).volPt.ticks);															
				}				
				
				chanTracks.get(i).volPt.ticks = total_ticks; 
				chanTracks.get(i).volPt.direction = 0; 
				
				chanTracks.get(i).volList.add(chanTracks.get(i).volPt.value); //create new inflection point with same bend value as previous point
				chanTracks.get(i).volList.add(chanTracks.get(i).volPt.ticks); //at the current time
										
				if (chanTracks.get(i).channel == 1) { //just for testing. remove 								
					int index1 = chanTracks.get(i).volList.size() - 4; //two points, stride 2
					int index2 = index1 + 2;
					int lval = chanTracks.get(i).volList.get(index1);
					int lticks = chanTracks.get(i).volList.get(index1+1);
					int rval = chanTracks.get(i).volList.get(index2);
					int rticks = chanTracks.get(i).volList.get(index2+1);
					System.out.println("volumeBend() FLATLINE inf. pts. L " + index1 + ":" + lval + "|" + lticks + " R " + index2 +":" + rval + "|" + rticks );
				}
										
			}			
								
			if (Math.abs(dVol) >= volValThld) {
				
				thldFlag = true; 				
				
				int lastIndex = chanTracks.get(i).volList.size() - 2; //stride 2 value|time pairs
				int lastValue = chanTracks.get(i).volList.get(lastIndex);
				int lastTime = chanTracks.get(i).volList.get(lastIndex+1);
				if (chanTracks.get(i).volPt.value == lastValue && chanTracks.get(i).volPt.ticks == lastTime) { //then the same point was added elsewhere								 
					System.out.println("volumeBend() jump inflection point already added "  + lastIndex + ":" + lastValue + "|" + lastTime);
				}
				else {	
					chanTracks.get(i).volList.add(chanTracks.get(i).volPt.value); 
					chanTracks.get(i).volList.add(chanTracks.get(i).volPt.ticks); 																	
				}				
				
				chanTracks.get(i).volList.add(value);
				chanTracks.get(i).volList.add(total_ticks);								
				
				chanTracks.get(i).volPt.direction = 0; //unset the direction
				
				if (chanTracks.get(i).channel == 1) { //just for testing 								
					int index1 = chanTracks.get(i).volList.size() - 4;
					int index2 = index1 + 2;
					int aVal = chanTracks.get(i).volList.get(index1);
					int aTicks = chanTracks.get(i).volList.get(index1+1);
					int bVal = chanTracks.get(i).volList.get(index2);
					int bTicks = chanTracks.get(i).volList.get(index2+1);
					System.out.println("volumeBend() JUMP  inf. pts. A " + index1 + ":" + aVal + "|" + aTicks + " B " + index2 + ":" + bVal + "|" + bTicks );
				}								
			}
			
			//if neither threshold above was exceeded, check for directional changes
			if (!thldFlag) {
				
				if (chanTracks.get(i).volPt.direction == 0) { //not yet set, need to determine direction
					
					if (value > chanTracks.get(i).volPt.value) {
						chanTracks.get(i).volPt.direction = 1; //bending higher
					}
					else if (value < chanTracks.get(i).volPt.value) {
						chanTracks.get(i).volPt.direction = -1; //bending lower
					}								
					
					System.out.println("volumeBend() initial direction set: " + chanTracks.get(i).volPt.direction);
				}
				else if (chanTracks.get(i).volPt.direction == 1) { //bending up
					if (value < chanTracks.get(i).volPt.value) { //inflection point - going back down
						chanTracks.get(i).volPt.direction = -1;
						
						chanTracks.get(i).volList.add(chanTracks.get(i).volPt.value); 
						chanTracks.get(i).volList.add(chanTracks.get(i).volPt.ticks);
																
						if (chanTracks.get(i).channel == 1) { //just for testing 								
							int index = chanTracks.get(i).volList.size() - 2;
							int val = chanTracks.get(i).volList.get(index);
							int ticks = chanTracks.get(i).volList.get(index+1);										
							System.out.println("!!! volumeBend() Up to Down " + index + ":" + val + "|" + ticks);
						}										
					}							
				}
				else if (chanTracks.get(i).volPt.direction == -1) { //bending down
					if (value > chanTracks.get(i).volPt.value) { //inflection point - going back up
						chanTracks.get(i).volPt.direction = 1;
						
						chanTracks.get(i).volList.add(chanTracks.get(i).volPt.value); 
						chanTracks.get(i).volList.add(chanTracks.get(i).volPt.ticks);
						
						if (chanTracks.get(i).channel == 1) { //just for testing 								
							int index = chanTracks.get(i).volList.size() - 2;
							int val = chanTracks.get(i).volList.get(index);
							int ticks = chanTracks.get(i).volList.get(index+1);										
							System.out.println("!!! volumeBend() Down to Up " + index + ":" + val + "|" + ticks);
						}	
					}	
				}								
			}							
			
			//set to current value to "walk" the data
			chanTracks.get(i).volPt.value = value;
			chanTracks.get(i).volPt.ticks = total_ticks;
		}
	}
	
	private void panBend(int i, int value, int total_ticks) { //midi CC8 or CC10 
		System.out.println("panBend(): " + i + " " + value + " " + total_ticks);
		int tickThld = msecToTicks(panThldMs);		
		
		if (chanTracks.get(i).panList.isEmpty()) {
			chanTracks.get(i).panList.add(value);
			chanTracks.get(i).panList.add(total_ticks);
			
			chanTracks.get(i).panPt.value = value;
			chanTracks.get(i).panPt.ticks = total_ticks;
		}
		else {
			
			boolean thldFlag = false; //set true if either the time or value threshold exceeded 
			
			int dPan = value - chanTracks.get(i).panPt.value; //previous value
			int dT = total_ticks - chanTracks.get(i).panPt.ticks;			
			
			if (dT >= tickThld) {
				
				thldFlag = true; //set flag to skip direction analysis				
				
				int lastIndex = chanTracks.get(i).panList.size() - 2; //stride 2 value|time pairs
				int lastValue = chanTracks.get(i).panList.get(lastIndex);
				int lastTime = chanTracks.get(i).panList.get(lastIndex+1);
				if (chanTracks.get(i).panPt.value == lastValue && chanTracks.get(i).panPt.ticks == lastTime) { //then the same point was added elsewhere								 
					System.out.println("panBend() flat inflection point already added " + lastIndex + ":" + lastValue + "|" + lastTime);
				}
				else {					
					chanTracks.get(i).panList.add(chanTracks.get(i).panPt.value);
					chanTracks.get(i).panList.add(chanTracks.get(i).panPt.ticks);															
				}				
				
				chanTracks.get(i).panPt.ticks = total_ticks; 
				chanTracks.get(i).panPt.direction = 0; 
				
				chanTracks.get(i).panList.add(chanTracks.get(i).panPt.value); //create new inflection point with same bend value as previous point
				chanTracks.get(i).panList.add(chanTracks.get(i).panPt.ticks); //at the current time
										
				if (chanTracks.get(i).channel == 1) { //just for testing. remove 								
					int index1 = chanTracks.get(i).panList.size() - 4; //two points, stride 2
					int index2 = index1 + 2;
					int lval = chanTracks.get(i).panList.get(index1);
					int lticks = chanTracks.get(i).panList.get(index1+1);
					int rval = chanTracks.get(i).panList.get(index2);
					int rticks = chanTracks.get(i).panList.get(index2+1);
					System.out.println("panBend() FLATLINE inf. pts. L " + index1 + ":" + lval + "|" + lticks + " R " + index2 +":" + rval + "|" + rticks );
				}
										
			}			
								
			if (Math.abs(dPan) >= panValThld) {
				
				thldFlag = true; 				
				
				int lastIndex = chanTracks.get(i).panList.size() - 2; //stride 2 value|time pairs
				int lastValue = chanTracks.get(i).panList.get(lastIndex);
				int lastTime = chanTracks.get(i).panList.get(lastIndex+1);
				if (chanTracks.get(i).panPt.value == lastValue && chanTracks.get(i).panPt.ticks == lastTime) { //then the same point was added elsewhere								 
					System.out.println("panBend() jump inflection point already added "  + lastIndex + ":" + lastValue + "|" + lastTime);
				}
				else {	
					chanTracks.get(i).panList.add(chanTracks.get(i).panPt.value); 
					chanTracks.get(i).panList.add(chanTracks.get(i).panPt.ticks); 																	
				}				
				
				chanTracks.get(i).panList.add(value);
				chanTracks.get(i).panList.add(total_ticks);								
				
				chanTracks.get(i).panPt.direction = 0; //unset the direction
				
				if (chanTracks.get(i).channel == 1) { //just for testing 								
					int index1 = chanTracks.get(i).panList.size() - 4;
					int index2 = index1 + 2;
					int aVal = chanTracks.get(i).panList.get(index1);
					int aTicks = chanTracks.get(i).panList.get(index1+1);
					int bVal = chanTracks.get(i).panList.get(index2);
					int bTicks = chanTracks.get(i).panList.get(index2+1);
					System.out.println("panBend() JUMP  inf. pts. A " + index1 + ":" + aVal + "|" + aTicks + " B " + index2 + ":" + bVal + "|" + bTicks );
				}								
			}
			
			//if neither threshold above was exceeded, check for directional changes
			if (!thldFlag) {
				
				if (chanTracks.get(i).panPt.direction == 0) { //not yet set, need to determine direction
					
					if (value > chanTracks.get(i).panPt.value) {
						chanTracks.get(i).panPt.direction = 1; //bending higher
					}
					else if (value < chanTracks.get(i).panPt.value) {
						chanTracks.get(i).panPt.direction = -1; //bending lower
					}								
					
					System.out.println("volumeBend() initial direction set: " + chanTracks.get(i).panPt.direction);
				}
				else if (chanTracks.get(i).panPt.direction == 1) { //bending up
					if (value < chanTracks.get(i).panPt.value) { //inflection point - going back down
						chanTracks.get(i).panPt.direction = -1;
						
						chanTracks.get(i).panList.add(chanTracks.get(i).panPt.value); 
						chanTracks.get(i).panList.add(chanTracks.get(i).panPt.ticks);
																
						if (chanTracks.get(i).channel == 1) { //just for testing 								
							int index = chanTracks.get(i).panList.size() - 2;
							int val = chanTracks.get(i).panList.get(index);
							int ticks = chanTracks.get(i).panList.get(index+1);										
							System.out.println("!!! panBend() Up to Down " + index + ":" + val + "|" + ticks);
						}										
					}							
				}
				else if (chanTracks.get(i).panPt.direction == -1) { //bending down
					if (value > chanTracks.get(i).panPt.value) { //inflection point - going back up
						chanTracks.get(i).panPt.direction = 1;
						
						chanTracks.get(i).panList.add(chanTracks.get(i).panPt.value); 
						chanTracks.get(i).panList.add(chanTracks.get(i).panPt.ticks);
						
						if (chanTracks.get(i).channel == 1) { //just for testing 								
							int index = chanTracks.get(i).panList.size() - 2;
							int val = chanTracks.get(i).panList.get(index);
							int ticks = chanTracks.get(i).panList.get(index+1);										
							System.out.println("!!! panBend() Down to Up " + index + ":" + val + "|" + ticks);
						}	
					}	
				}								
			}							
			
			//set to current value to "walk" the data
			chanTracks.get(i).panPt.value = value;
			chanTracks.get(i).panPt.ticks = total_ticks;
		}
	}
	
	private void setupTrack(Song song, int channel, int instmt_num) { //called from readMidi() in 'C' Program Change, tracks added with instrument
		System.out.println("setupTrack() ch=" + channel + " instmt_num " + instmt_num);						
		
		if (channel != 10) { //channel 10 is drums in MIDI			
			
			//int trkSize_px = (int) (gv.TRKSIZE_DP * context.getResources().getDisplayMetrics().density + 0.5f);
			//int ledSize_px = (int) (gv.LEDSIZE_DP * context.getResources().getDisplayMetrics().density + 0.5f);
			Track track = new Track(context);
			
			//first check if have exact instrument match in dB
			boolean match = false;			
			for (int i=0; i < gv.instruments.size(); i++) {				
				if (instmt_num == gv.instruments.get(i).instmtNum) {
					System.out.println("instmt match!");
					match = true;					
					track.instrument = new Instrument(context, instmt_num);								
					break;
				}	
			}
			
			if (!match) { //then try to find a dB instrument from same Timbre Group
				System.out.println("The MIDI file instrument for this track is not available");
				
				List<Object> matches = new ArrayList<Object>();
				
				/**
				 * this long ass stuff is very basic. Could condense into single block & feed numbers from
				 * a list, but leaving for now as makes clear the MIDI GM1 instrument categories
				 */
				
				if (instmt_num <= 7) { //0-7 Piano Timbres. have 0,2,4					
					matches = instmtGroup(0, 7, matches); //utility function to find dB instruments in range
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0)); 						
					}
					for (int i=0; i < matches.size(); i+=3) { //these can be used instead to maybe prompt user to choose one
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 8 && instmt_num <= 15) { //8-15 Chromatic Percussion have 12
					matches = instmtGroup(8, 15, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));						
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 16 && instmt_num <= 23) { //16-23 Organ Timbres have 16,22
					matches = instmtGroup(16, 23, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));						
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 24 && instmt_num <= 31) { //24-31 Guitar Timbres have 25,27
					matches = instmtGroup(24, 31, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 32 && instmt_num <= 39) { //32-39 Bass Timbres have 32,33
					matches = instmtGroup(32, 39, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 40 && instmt_num <= 47) { //40-47 String Timbres have 42,45
					matches = instmtGroup(40, 47, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 48 && instmt_num <= 55) { //48-55 Ensemble Timbres have 48,52
					matches = instmtGroup(48, 55, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 56 && instmt_num <= 63) { //56-63 Brass Timbres have 56,61
					matches = instmtGroup(56, 63, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 64 && instmt_num <= 71) { //64-71 Reed Timbres have 65,71
					matches = instmtGroup(64, 71, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 72 && instmt_num <= 79) { //72-79 Pipe Timbres have 73
					matches = instmtGroup(72, 79, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 80 && instmt_num <= 95) { //80-95 Synth Sounds *don't have any
					matches = instmtGroup(80, 95, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 96 && instmt_num <= 103) { //96-103 Synth Effects *don't have any
					matches = instmtGroup(96, 103, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 104 && instmt_num <= 111) { //104-111 Ethnic Timbres have 104,105
					matches = instmtGroup(104, 111, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
				if (instmt_num >= 112 && instmt_num <= 127) { //112-127 Sound Effects *assigned Didgeridoo 127 arbitrarily
					matches = instmtGroup(112, 127, matches);
					if (matches.size() > 0) { //right now just assigning the 1st instrument found in same timbre group
						track.instrument = new Instrument(context, (Integer) matches.get(0));
					}
					for (int i=0; i < matches.size(); i+=3) {
						System.out.println("found instrument from same group: " 
								+ matches.get(i) +" "+ matches.get(i+1) +" "+ matches.get(i+2));
					}
				}
			}
			
			song.tracks.add(track);
		}
		else { //then it's a ch=10 percussion track
			System.out.println("this is a percussion track");
			DrumTrack dtrack = new DrumTrack(context); //type 2
			
			song.drum_tracks.add(dtrack);
		}		
		
	}
	
	public boolean formatNotes(Song song) {
		System.out.println("formatNotes()");
		
		boolean status = false;
		
		int note;
		float velocity;
		//int volume;
		
		int onticks;
		int offticks;
		int durticks;
		
		int i_idx = -1; //instrument index to match non-drum chanTracks to corresponding song.tracks
		//this is a slightly fudgey thing. midi using channel 10 for drum tracks means my chanTracks
		//will index correlate with song.tracks until a drum track is hit & then are 1 off. this accounts for it
		
		//first populate the song.tracks.notes with basic note data, static note volume, no note bends or pan bends
		for (int i=0; i < chanTracks.size(); i++) {
			System.out.println("initial processing of chanTrack: " + i);
			
			if (chanTracks.get(i).channel != 10) { //midi channel 10 is for drum tracks
				
				i_idx += 1; //an instrument track, so advance the instrument index. will correlate with song.track indices
				
				while (chanTracks.get(i).notes.size() > 0) {
					
					note = chanTracks.get(i).notes.get(0);
					velocity = chanTracks.get(i).notes.get(1);
					onticks = chanTracks.get(i).notes.get(2);
					
					//discard values as write to song.track.notes so don't duplicate data in RAM
					if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(0); //pop off the Note On value
					if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(0); //pop off the velocity value
					if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(0); //pop off the ticks value
					
					System.out.println("Note On: " + note + " " + velocity + " " + onticks);
					
					//seek ahead to find same note value (because midi streams note On/Off pairs)
					for (int j=0; j < chanTracks.get(i).notes.size(); j+=3) {
						if (chanTracks.get(i).notes.get(j) == note) { //then found the Note Off of the note 						
							
							offticks = chanTracks.get(i).notes.get(j+2);
							durticks = offticks - onticks;
							
							System.out.println("Note Off: " + note + " " + chanTracks.get(i).notes.get(j+1) + " " + offticks);
							
							//add the static note fields to the song.track.notes list - start|note|duration
							song.tracks.get(i_idx).notes.add(onticks); //add as ticks now, will change to msecs later
							song.tracks.get(i_idx).notes.add(note); 
							song.tracks.get(i_idx).notes.add(durticks);
							
							if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(j); //pop off the Note Off value
							if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(j); //pop off the velocity value
							if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(j); //pop off the ticks value
							else System.out.println("chanTrack: " + i + " notes.size() is now zero"); //TODO for testing, remove	
							
							//set initial volume using velocity as % of master volume														
							if (chanTracks.get(i).mstrVol == 0) chanTracks.get(i).mstrVol = 63; //make sure master volume isn't zero (some midi files do weird things)
							song.tracks.get(i_idx).notes.add(1); //#vol pts field. always at least 1 volume value|time pair per note. change if dynamic volume
							float mv = chanTracks.get(i).mstrVol;
							float pv = velocity/127.0f * mv + 0.5f; //midi volume is a 0-127 scale
							song.tracks.get(i_idx).notes.add((int) pv); //initial volume value
							song.tracks.get(i_idx).notes.add(0); //initial volume is always at time=0
							
							//set initial pan to center
							song.tracks.get(i_idx).notes.add(1); //#pan pts field. always at least 1 pan value|time pair per note
							song.tracks.get(i_idx).notes.add(50); //pan 0=hard left -> 100=hard right
							song.tracks.get(i_idx).notes.add(0); //initial pan is always at time=0
														
							//set initial bends to none
							song.tracks.get(i_idx).notes.add(0); //unlike volume and pan, no initial bend value is required
						}
					}
				}
			}
			else { //it's a drum track 
				System.out.println("...it's a drum track");
				//might out this to a formatDrum() to keep this readable. OR loop over the chanTracks from convert()			
			}
			
			System.out.println("end initial processing pass chanTrack: " + i);
			//add more traces here to see song.tracks.notes details
			
		} //end for loop initial processing pass
		
		
		//OK now have the basic notes lists set up, Now need to insert any dynamic volume/pan/bend data	
		
		i_idx = -1; //reset the instrument index
		for (int i=0; i < chanTracks.size(); i++) {
			if (chanTracks.get(i).channel != 10) {
				i_idx += 1;
				
				//process each note in song.track.notes added in step above
				for (int j=0; j < song.tracks.get(i_idx).notes.size(); j+=10) { //start|note|dur|1|v|0|1|p|0|0 - how song.track.notes currently set up. stride 10
					//dynamic volume first
					if (chanTracks.get(i).volList.size() > 0) {
						
						//get a volume value at note start, either exact or interpolated between 2 values
						
						//first find closest dynamic volume pt. on or right before note start time
						boolean found = false;
						
						//check for simplest case of a vol pt at same time as note start 
						
						
						//then add all vol pts that fall within duration of note
						
						//
						
					}
					
					//then pan, using the #vol pts field to keep reading frame right
					if (chanTracks.get(i).panList.size() > 0) {
						
					}
					
					//then bends, using both the #vol pts and #pan pts fields to keep frame
					if (chanTracks.get(i).bendList.size() > 0) {
						
					}
				}			
				
			} //else it's a drum track			
		}
		
		
		
		
		status = true;
		return status;
	}
	
	public boolean formatNotesOLD2(Song song) { //deeply nested
		System.out.println("formatNotes()");	
		
		boolean status = false;
		
		int note;
		float velocity;
		int volume;
		
		int onticks;
		int offticks;
		int durticks;			
		
		for (int i=0; i < chanTracks.size(); i++) { //Process each track. the chanTracks indices match the Song.tracks indices
			System.out.println("processing chanTrack: " + i);
			
			int volPtr = 1; //pointer to walk along the chanTrack.volList. set to 1 to first get the time of the value|time pairs
					
			if (chanTracks.get(i).channel != 10) { //midi channel 10 is for drum tracks
				
				while (chanTracks.get(i).notes.size() > 0) { 
					note = chanTracks.get(i).notes.get(0);
					velocity = chanTracks.get(i).notes.get(1);
					onticks = chanTracks.get(i).notes.get(2);
					
					//discard values as write to song.track.notes so don't duplicate data in RAM
					if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(0); //pop off the Note On value
					if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(0); //pop off the velocity value
					if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(0); //pop off the ticks value
					
					//seek ahead to find same note value (because midi streams note On/Off pairs)
					for (int j=0; j < chanTracks.get(i).notes.size(); j+=3) {
						if (chanTracks.get(i).notes.get(j) == note) { //then found the Note Off of the note 
							
							offticks = chanTracks.get(i).notes.get(j+2);
							durticks = offticks - onticks;
							
							//add the static note fields to the song.track.notes list - start|note|duration
							song.tracks.get(i).notes.add(onticks); //add as ticks now, will change to msecs later
							song.tracks.get(i).notes.add(note); 
							song.tracks.get(i).notes.add(durticks);
							
							if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(j); //pop off the Note Off value
							if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(j); //pop off the velocity value
							if (chanTracks.get(i).notes.size() > 0) chanTracks.get(i).notes.remove(j); //pop off the ticks value
							else System.out.println("chanTrack: " + i + " notes.size() is now zero"); //TODO for testing, remove
							
							//set initial volume using velocity as % of master volume														
							if (chanTracks.get(i).mstrVol == 0) chanTracks.get(i).mstrVol = 63; //make sure master volume isn't zero (some midi files do weird things)
							song.tracks.get(i).notes.add(1); //#vol pts field. always at least 1 volume value|time pair per note. change if dynamic volume
							float mv = chanTracks.get(i).mstrVol;
							float pv = velocity/127.0f * mv + 0.5f; //midi volume is a 0-127 scale
							song.tracks.get(i).notes.add((int) pv); //initial volume value
							song.tracks.get(i).notes.add(0); //initial volume is always at time=0
							
							//add any dynamic volume points - these supersede initial values set above
							if (chanTracks.get(i).volList.size() > 0) { //skip it if empty
								
								//first find closest dynamic volume pt. on or right before note start time
								boolean found = false;
								while (chanTracks.get(i).volList.get(volPtr) <= onticks) {
									
									if (chanTracks.get(i).volList.get(volPtr) == onticks) { //check for easiest case of dyn vol pt at same time as note start
										//replace the velocity volume above with the new volume
										pv = chanTracks.get(i).volList.get(volPtr-1)/127.0f * mv + 0.5f; //as % of master volume
										int vidx = song.tracks.get(i).notes.size()-2; //first volume value index
										song.tracks.get(i).notes.set(vidx, (int)pv);
										found = true;
										break;
									}
									else volPtr+=2; //walking the time values of the volList
									//!!!! need bounds checking so don't overrun array. Also
								}
								if (!found) {
									//OK at this point no exact time match was found, and the dyn vol pt time is now greater than 
									//the note start time. need to interpolate between last dyn vol pt that occurs before note start 
									//and nearest dyn vol pt after note start
									
									//first check if the two vol pts have equal value. If so, no calculation needed
									if (chanTracks.get(i).volList.get(volPtr-3) == chanTracks.get(i).volList.get(volPtr-1)) {
										
									}
									else { //the two vol pts that straddle the note start time
										
									}
								}
								
								//OK at this point need to find all possible dyn vol pts that fall within note duration
								//the volPtr should be at first vol pt within note when hit this point
								
								//then need to find either an exact time match volume value at end of note, or do that same
								//interpolate between straddled values as did for note start time
								
							}
							
							
							//add any dynamic pan points
							
							//add any pitch bend points
							
							break; //breaks the loop seeking the Note Off when found
						}
						//if no Note Off match found, then the Note On data is just discarded. Shouldn't happen, but won't hang the while loop if does 
					} //end inner for loop
				} // end while loop
				

			}
			else { //it's a midi drum track. Note values here equate to drum MIDI instrument numbers
				System.out.println("formatNotes() processing a percussion track");
			}
			
			
			
			//process every chanTracks.get(i).notes into dbs format
			//all have to do is scan ahead for same note now as already sorted by channel
		} //end for loop over chanTracks
		
		status = true;
		return status;
	}
	
	public boolean formatNotesOLD(Song song, int trackIndex, List<Integer> notes_ar) { //turns MIDI On/Off mote msgs into dbs start/note/dur format
		System.out.println("formatNotes() " + notes_ar.size());
		if (notes_ar.size() == 0) return true; //case when midi format=1 & 1st track is a "tempo map"
		
		boolean status = false;
		
		//int trackIndex = song.tracks.size() - 1;
		//System.out.println("trackIndex " + trackIndex);
		
		int channel;
		int note;
		int volume;
		int pan = 50;
		int nDynBytes = 0; //field for parsing dbs file to know if/how far to read dynamics info/instructions
		
		double onticks;
		double ontime;
		double durticks;
		double duration;
		int istart; //integer start time rounded to whole number
		int idur; //integer note duration rounded to whole number
		
		int matched_notes = 0;
		
		for (int i=0; i < notes_ar.size(); i += 5) { //stride 5 ***TODO this will be 6 or more with pan & bends
			if (notes_ar.get(i+1) == 1) { // 1 = Note On
				onticks = (double) notes_ar.get(i);
				channel = notes_ar.get(i+2);
				note = notes_ar.get(i+3);
				volume = notes_ar.get(i+4);
				
				//seek ahead to find same channel, same note because if On next one must be off (a Note Off message is often not sent) 
				for (int j = i+5; j < notes_ar.size(); j += 5) {
					if (notes_ar.get(j+2) == channel && notes_ar.get(j+3) == note) { 
						
						ontime = onticks * tickms + 0.5; //for rounding
						istart = (int) ontime;
						durticks = (double) notes_ar.get(j) - onticks;
						duration = durticks * tickms + 0.5; //for rounding
						idur = (int) duration;
						
						if (channel != 10) { //ch=10 is drums 
							if (idur >= 50) { //skip notes shorter than 50 msecs								
								song.tracks.get(trackIndex).notes.add(istart);
								song.tracks.get(trackIndex).notes.add(note);
								song.tracks.get(trackIndex).notes.add(idur);
								song.tracks.get(trackIndex).notes.add(volume);
								song.tracks.get(trackIndex).notes.add(nDynBytes); //TODO implement dynamics system ; )
							}
							else {
								System.out.println("short note skipped. ms = " + idur);
							}
						}
						else { //then it's a percussion file. Note values here equate to drum MIDI numbers
							//check if already a drum for that note code
							int drumIndex = -1;
							for (int k=0; k < song.drum_tracks.get(0).drums.size(); k++) { //.get(0) as should only be one drum track in a MIDI file
								if (song.drum_tracks.get(0).drums.get(k).drumNum == note) { //then already a drum object created for it, add data to its score
									song.drum_tracks.get(0).drums.get(k).score.add(istart);
									song.drum_tracks.get(0).drums.get(k).score.add(volume);
									song.drum_tracks.get(0).drums.get(k).score.add(pan);
									drumIndex = k;
									break;
								}
							}
							if (drumIndex == -1) { //note calls for a drum that needs to be set up
								Drum drum = new Drum(context, note);
								
								drum.score.add(istart);
								drum.score.add(volume);
								drum.score.add(pan);
								song.drum_tracks.get(0).drums.add(drum);
							}						
						}						
						
						break; //breaks the j loop since the Off note/pair found
					}
				}
			}
		}		
				
		status = true;
		return status;
	}
	
	private List<Object> instmtGroup(int low, int high, List<Object> matches) { //finds dB instruments in same Midi GM1 Timbre Group 
		
		matches.clear();
		
		for (int i=0; i < gv.instruments.size(); i++) {
			int num = gv.instruments.get(i).instmtNum;
			String displayName = gv.instruments.get(i).name;
			if (num >= low && num <= high) {
				matches.add(num);
				matches.add(displayName);
				String dirName = num + "." + displayName;
				matches.add(dirName);
			}
		}
		
		return matches;
	}
	
	private int checkVLQ(ListIterator<Byte> pointer) {
		List<Byte> vlq_bytes = new ArrayList<Byte>();
		vlq_bytes.add(pointer.next());//add first delta-time byte to list
		int value = vlq_bytes.get(0) & 0xFF; //cast as unsigned byte into delta-time value int (0-255)
		
		if ((vlq_bytes.get(0) & 1 << 7) > 0) { //if top bit set, then in a VLQ situation 					
			vlq_bytes.add(pointer.next()); //get 2nd VLQ byte													
			if ((vlq_bytes.get(1) & 1 << 7) > 0) {					
				vlq_bytes.add(pointer.next()); //get 3rd VLQ byte															
				if ((vlq_bytes.get(2) & 1 << 7) > 0) {							
					vlq_bytes.add(pointer.next()); //get 4th VLQ byte. should be enough
				}
			}
		}
		
		//check if need to reconstruct length from multiple VLQ bytes
		if (vlq_bytes.size() > 1) {
			//System.out.println("VLQ length " + vlq_bytes.size());
			
			value = 0; //clear out all bits of delta_time so can set them next
			
			//OK in here need to grab those low 7 bits of each VLQ byte & concatenate into single value			
			int atbit = 0; //bit # of integer that may be set
			for (int i=vlq_bytes.size()-1; i >= 0; i--) { //start from byte at highest index						
				for (int j=0; j < 7; j++) { //run through low 7 bits of each byte
					if ((vlq_bytes.get(i) & 1<<j) > 0) { //then bit is set
						value = setBit(atbit, value); //little utility function at bottom of class
					}
					atbit++;
				}
			}
		}		
		
		return value;
	}

	private int setBit(int bit, int target) { //sets specified bit in target integer
        // Create mask
        int mask = 1 << bit;
        // Set bit
        return target | mask;
     }
}
