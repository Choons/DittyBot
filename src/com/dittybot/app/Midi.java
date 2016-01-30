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
 */

public class Midi {	
	
	private Context context;
	private GlobalVars gv;
	
	private String filepath; //maybe want separate public fileName as well?	
	public long fileSize;
	
	public short format_type; //0=contains just one track, 1=contains multiple tracks to play simultaneously. 1st track is a tempo track w/ timing info, 2=multiple independent tracks with own play times
	public short num_tracks = -1; //number of tracks in song	
	public int time_type = -1; //0=PPQN, 1=SMPTE .. ?need this? going to have tempo in BPM in dbs format
	public int ppqn = -1; //pulses per quarter note. only used in time_type = 0 files
	public double fps = -1; //frames per second. only used in time_type = 1 SMPTE files
	public double subframes = -1; //"ticks" per frame. only used in time_type = 1 SMPTE files	
	public double tickms = -1; //fundamental time division that can be derived for both time_type files
	
	private List<Long> track_ptrs; //track start & end byte pointers. so start at an index, the end value at index+1
	private List<Byte> track_bytes; //array to hold raw track data in memory for faster processing
			
	public String error_message = "error"; //use this so can access error info from main UI
	
	
	public Midi(Context incontext, String fpath) {	//constructor
		context = incontext;
		gv = ((GlobalVars)context.getApplicationContext());
		filepath = fpath;		
		track_ptrs = new ArrayList<Long>();			
		track_bytes = new ArrayList<Byte>();		
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
			
			format_type = in.readShort(); //0, 1, or 2. see the MIDI File Format. not used in DittyBot 
			if (format_type != 0 && format_type != 1 && format_type != 2) {
				error_message = "Incorrect format type. File may be corrupted"; 
				return status; //kick out of processing the file
			}
			
			if (format_type == 2) {
				//TODO these are very rare, and I'm not implementing it right now
				error_message = "This is a format type 2 MIDI file. This format is not supported"; 
				return status; //kick out of processing the file
			}
			
			num_tracks = in.readShort(); //# tracks in song. 1 - 65,535. TODO verify empirically
			
			
			//---------determine timing scheme used in midi file--------------					
			byte[] time_divs = new byte[2];
			in.read(time_divs); 			
			
			//check whether 1st time div byte is a positive or negative value
			int timeval1 = time_divs[0]; //convert directly to int as signed byte so get possible values -127 to 127
			String timehex1 = String.format("%02X", time_divs[0]); //not used but might if go to a check top bit approach
			String timehex2 = String.format("%02X", time_divs[1]);
						
			if (timeval1 < 0) { //negative first time div byte means SMPTE frames per second timing scheme				
				time_type = 1;
				//FPS value should be -24, -25, -29, or -30. possible -97 for 29.97
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
			
			if (num_tracks == track_ptrs.size()/2) {
				System.out.println("tracks found: " + num_tracks); 
			}
			else {
				num_tracks = (short) (track_ptrs.size()/2); //go with own evidence
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
	
	public boolean getTrackData(int tracknum) { //loads a track's raw midi data into a RAM array for faster processing
		System.out.println("-------getTrackData " + tracknum + " -------");
		
		boolean status = false;
		
		track_bytes.clear(); //clear out any previous track data in bytes array
		
		int start_index = tracknum * 2; //start/end ptrs are stored as consecutive pairs in list
		int end_index = start_index + 1;
		long start_ptr = track_ptrs.get(start_index);
		long end_ptr = track_ptrs.get(end_index);
		System.out.println("start_ptr " + start_ptr + " end_ptr " + end_ptr);
		
		int size_field = -1; //track data size field stored in the file. reputed to be often wrong
		
		File midi_file = new File(filepath);
		
		try {
			RandomAccessFile in = new RandomAccessFile(midi_file, "r");
			
			in.seek(start_ptr);	
			
			size_field = in.readInt(); //get the supposed data size stored in file
			
			while (in.getFilePointer() < end_ptr) {
				track_bytes.add(in.readByte()); //put the track data in RAM
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
		
		status = true; //TODO this is totally glossed over. Set more rigorously
		return status;
	}	
	
	ArrayList<List<Integer>> bendLists = new ArrayList<List<Integer>>(); //list of track's pitch bend lists
	
	public boolean readMidi(Song song, List<Integer> notes_ar) { //translate midi data into my dbs format & add to a new track object
		System.out.println("readMidi() ");
		
		boolean status = false;		
		boolean sysex_open = false; //meh annoying fucking flag for possible divided sysex messages
		boolean tempofound = false; 		
		ListIterator<Byte> pointer = track_bytes.listIterator();		
		int delta_ticks; //# of ticks since last message. a variable length quantity (VLQ)
		int total_ticks = 0; //total elapsed ticks from beginning of track
		int length; //data bytes length field in midi files. a variable length quantity (VLQ)
		String chan_hex; //low nibble used for channel in some events 
		int channel;
		int note; 
		int vel; //volume in Note On	
		int pan; //pan value 0=hard left, 127=hard right		
		
		int prevInfPt = -1; //value of last inflection point TODO could just look at last value in the 
		int pb_curr; //the value of the latest pitch bend message
		
		List<PbPoint> pbPts = new ArrayList<PbPoint>(); //running list of bend points for each track
		
		
		while (pointer.hasNext()) {
			
			//--------- get Delta-Time ----------------------------------------------------------
			
			delta_ticks = checkVLQ(pointer); //actually pass the pointer object itself. nifty trick
			//System.out.println("delta_time " + delta_time);
			
			total_ticks += delta_ticks; //running total of ticks			

			//-------- get MIDI Messages -----------------------------------------------------------------				
			
			String hexstr = String.format("%02X", pointer.next()); //2 hex string codes in one byte
			Character char1 = hexstr.charAt(0);	//get hex char at top "nibble" = message type
			Character char2 = hexstr.charAt(1);	//get hex char at low "nibble" = midi channel message goes with			
			
			switch (char1.charValue()) { //char1 is the MIDI message type
			case '8': //Note Off
				chan_hex = String.valueOf(char2);				
				channel = Integer.parseInt(chan_hex, 16) + 1; //convert hex string to int & add 1 so channels run 1-16				
				note = pointer.next() & 0xFF;
				vel = pointer.next() & 0xFF;
				notes_ar.add(total_ticks);
				notes_ar.add(0); //add 0 as code for Note Off
				notes_ar.add(channel); 
				notes_ar.add(note);
				notes_ar.add(vel);
				if (channel == 1) {
					System.out.println("-- Note Off n=" + note + " ch=" + channel + " t=" + total_ticks); 
				}				
				break;
			case '9': //Note On
				chan_hex = String.valueOf(char2);					
				channel = Integer.parseInt(chan_hex, 16) + 1;				
				note = pointer.next() & 0xFF;
				vel = pointer.next() & 0xFF; //volume
				notes_ar.add(total_ticks);
				notes_ar.add(1); //add 1 as code for Note On
				notes_ar.add(channel); 
				notes_ar.add(note);
				notes_ar.add(vel);
				if (channel == 1) {
					
				}
				System.out.println("++ Note On n=" + note + " ch=" + channel + " t=" + total_ticks); 
				break;
			case 'A': //Note Aftertouch
				pointer.next(); //note
				pointer.next(); //pressure
				//System.out.println("A Note Aftertouch");
				break;
			case 'B': //Controller. for things like main volume 7 (0x07), balance 8 (0x08), pan 10 (0x0A)
				int ctrl = pointer.next() & 0xFF; //controller type				
				int value = pointer.next() & 0xFF; //value 0-127
				if (ctrl == 10) { //a panning message
					pan = value;
					System.out.println("******PAN MESSAGE****** " + pan);
				}
				//System.out.println("B Controller");
				break;
			case 'C': //Program Change ***** Instrument info ******
				chan_hex = String.valueOf(char2);				
				channel = Integer.parseInt(chan_hex, 16) + 1;
				int instmt_num = pointer.next() & 0xFF; //MIDI instrument code				
				System.out.println("C Program Change ch=" + channel + " instmt_num: " + instmt_num);				
				setupTrack(song, channel, instmt_num);				
				break;
			case 'D':
				pointer.next(); //pressure
				//System.out.println("D Channel Aftertouch");
				break;
			case 'E': //Pitch Bend - 14 bit value constructed from 2 bytes
				chan_hex = String.valueOf(char2);				
				channel = Integer.parseInt(chan_hex, 16) + 1;
				byte lsb = pointer.next(); //least significant bits. take lower 7
				byte msb = pointer.next(); //most significant bits. take lower 7
				pb_curr = 0; //clear all bits
				pb_curr = (msb & 0xFF) << 7 | (lsb & 0xFF); //shift most signif. 7 bits left & concatenate the lsb's at end
				
				if (channel == 1) {
					System.out.println("pb " + pb_curr + " t=" + total_ticks);
				}
				
				//find and store the inflection points of the pitch bend data
				boolean found = false; //flag for whether a pbPt already exists for the channel
				for (int i=0; i < pbPts.size(); i++) {
					if (pbPts.get(i).channel == channel) { //make sure looking at correct channel
						
						//i will run 0 -> 15 for the channels 1 -> 16
						//check if the just previous pbPt has the same value as the last inflection point
						//this is the case when bend value hasn't changed, maybe for quite some time
						//there needs to be another inflection point recorded if 
						
						int endIndex = bendLists.get(i).size() - 2; //index of last inflection point value
						prevInfPt = bendLists.get(i).get(endIndex);
						
						
						if (pbPts.get(i).direction == 0) { //then new, need to determine direction
							
							if (pb_curr > pbPts.get(i).value) {
								pbPts.get(i).direction = 1;
							}
							else if (pb_curr < pbPts.get(i).value) {
								pbPts.get(i).direction = -1;
							}
							
							pbPts.get(i).value = pb_curr; //"walk" the points
							System.out.println("initial direction set: " + pbPts.get(i).direction);
						}
						else if (pbPts.get(i).direction == 1) { //bending up
							if (pb_curr < pbPts.get(i).value) { //inflection point - going back down
								pbPts.get(i).direction = -1;
								pbPts.get(i).value = pb_curr;
								System.out.println("inflection up to down");
							}							
						}
						else if (pbPts.get(i).direction == -1) { //bending down
							if (pb_curr > pbPts.get(i).value) { //inflection point - going back down
								pbPts.get(i).direction = 1;
								pbPts.get(i).value = pb_curr;
								System.out.println("inflection down to up");
							}	
						}
						
						found = true;
						break;
					}
				}
				
				if (!found) { //first time channel has come up, create a bendList and PbPoint object & add to list
					PbPoint pbPt = new PbPoint();
					pbPt.channel = channel;
					pbPt.value = pb_curr;
					pbPt.ticks = total_ticks;
					pbPts.add(pbPt);
										
					List<Integer> bendList = new ArrayList<Integer>(); //holds each bend target point in value|time pairs
					bendList.add(pbPt.value); //the initial pitch bend value for the track
					bendList.add(pbPt.ticks);					
					bendLists.add(bendList);
					
					System.out.println("created new pbPt " + channel);
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
	
	public class PbPoint { //convenience class for a pitch bend
		
		int channel;
		int value;		
		int ticks;
		//boolean isNew = true; //
		int direction = 0; //0 when created, 1 when bending higher, -1 when bending lower
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
	
	public boolean formatNotes(Song song, List<Integer> notes_ar) { //turns MIDI On/Off mote msgs into dbs start/note/dur format
		System.out.println("formatNotes() " + notes_ar.size());
		if (notes_ar.size() == 0) return true; //case when midi format=1 & 1st track is a "tempo map"
		
		boolean status = false;
		
		int trackIndex = song.tracks.size() - 1;
		System.out.println("trackIndex " + trackIndex);
		
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
		
		for (int i=0; i < notes_ar.size(); i += 5) { //stride 5 ***TODO this will be 6 or more with pan
			if (notes_ar.get(i+1) == 1) { // 1 = Note On
				onticks = (double) notes_ar.get(i);
				channel = notes_ar.get(i+2);
				note = notes_ar.get(i+3);
				volume = notes_ar.get(i+4);
				
				//seek ahead to find same channel, same note, with v=0. (can't be sure a Note Off is sent in every file) 
				for (int j = i+5; j < notes_ar.size(); j += 5) {
					if (notes_ar.get(j+2) == channel && notes_ar.get(j+3) == note) { //thinking sufficient to just find same channel & same note
						
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
