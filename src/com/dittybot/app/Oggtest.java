package com.dittybot.app;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.ideaheap.io.VorbisFileOutputStream;
import com.ideaheap.io.VorbisInfo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.WindowManager;



public class Oggtest extends Activity {
	
	WavIO wav;
	
	private DataInputStream inFile = null;
	private short[] pcm_data;
	private VorbisInfo vinfo;
	private VorbisFileOutputStream vos;
	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	System.out.println("onCreate() NWoggtest");
        super.onCreate(savedInstanceState);        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //TODO make settable
        
        oggIt();
		         
	}
	
	private void oggIt() {
		
		//read past the wav header		
		long chunkSize; //* see the WAVE format pdf in Audio Tech folder
		long subChunk1Size; //16 for PCM = size of the rest of the Subchunk which follows this number
		int audioFormat; //PCM = 1. Values other than 1 indicate some form of compression.
		long channels;
		long sampleRate;
		long byteRate;
		int blockAlign;
		int bitsPerSample;
		long dataSize;	
		int length_ms;
		
		byte[] read4 = new byte[4]; 
		byte[] read2 = new byte[2];
		int bytesRead = 0;
		
		String path = Environment.getExternalStorageDirectory().toString() 
				+ "/DittyBot/Audio/04-The Clash-Rock The Casbah.wav";
		
		try {
			inFile = new DataInputStream(new FileInputStream(path));
			
			String chunkID = "" + (char)inFile.readByte() + (char)inFile.readByte() + (char)inFile.readByte() 
					+ (char)inFile.readByte(); //magic seq "RIFF" 		 

			inFile.read(read4); 
			chunkSize = byteArrayToLong(read4); 		

			String format = "" + (char)inFile.readByte() + (char)inFile.readByte() + (char)inFile.readByte() 
					+ (char)inFile.readByte(); //magic seq "WAVE" 12 bytes
						
			String subChunk1ID = "" + (char)inFile.readByte() + (char)inFile.readByte() + (char)inFile.readByte() 
					+ (char)inFile.readByte(); //magic seq "fmt" 1 blank byte?
			
			inFile.read(read4); //SubChunk1Size- gives #bytes remaining after this read before the Data Chunk			
			subChunk1Size = byteArrayToLong(read4); 
			
			inFile.read(read2); // read the audio format.  This should be 1 for PCM
			audioFormat = byteArrayToInt(read2); 
			bytesRead += 2;
			
			inFile.read(read2); // read the # of channels (1 or 2)
			channels = byteArrayToInt(read2); 
			bytesRead += 2;
						
			inFile.read(read4); // read the samplerate
			sampleRate = byteArrayToLong(read4); 
			bytesRead += 4;
			
			inFile.read(read4); // read the byterate
			byteRate = byteArrayToLong(read4); 
			bytesRead += 4;
			
			inFile.read(read2); // read the blockalign
			blockAlign = byteArrayToInt(read2); 
			bytesRead += 2;
			
			inFile.read(read2); // read the bitspersample
			bitsPerSample = byteArrayToInt(read2); 
			bytesRead += 2;
			
			if (bytesRead < subChunk1Size) {
				int diff = (int)subChunk1Size - bytesRead;
				System.out.println("chunk diff " + diff);
				for (int i=0; i < diff; i++) {
					byte jumpbyte = inFile.readByte(); //jump over extra format bytes
				}				
			}			
			
			// read the data chunk header - reading this IS necessary, because not all wav files will have the data chunk here - for now, we're just assuming that the data chunk is here
			String dataChunkID = "" + (char)inFile.readByte() + (char)inFile.readByte() + (char)inFile.readByte() 
					+ (char)inFile.readByte(); //magic seq "data" 40 bytes
			System.out.println("dataChunkID " + dataChunkID); //TODO verify these magic seqs
			
			inFile.read(read4); // read the size of the data
			dataSize = byteArrayToLong(read4); // Subchunk2Size
			System.out.println("PCM data size " + dataSize);				
						
			long len_ms = (1000 * dataSize)/byteRate;			
			length_ms = (int)len_ms;
			System.out.println("length_ms " + length_ms);
			//----------------------------------------------------------------
			
			
			//TODO need to do verif on above, but assuming get here, then create a VorbisFileOutputStream
			vinfo = new VorbisInfo();
			vinfo.channels = 2;
			vinfo.sampleRate = 44100;
			vinfo.length = 9838080; //got from wavosaur for Casbah. don't think ever used
			vinfo.quality = 0.4f;
			
			String oggPath = Environment.getExternalStorageDirectory().toString() 
					+ "/DittyBot/Audio/04-The Clash-Rock The Casbah.ogg"; //name for new ogg file
			
			float startTime = System.nanoTime()/1000000; //to get in milliseconds
			
			vos = new VorbisFileOutputStream(oggPath, vinfo);
						
			
			//loop over the PCM data sending chunks to be ogg-encoded
			int numBytes = 1048576; //1 MB
			byte[] bytes = new byte[numBytes];
			int loops = 0;
			while (dataSize != 0) {
				if (dataSize < numBytes) {
					numBytes = (int) dataSize;
					bytes = new byte[numBytes];
					System.out.println("last bytes: " + numBytes);
				}			
				inFile.read(bytes);
				dataSize -= numBytes;
				
				//convert bytes to short values
				short[] pcm_buf = new short[numBytes/2];
				int pcm_idx = 0;
				for (int i=0; i < bytes.length; i+=2) {
					if (i+1 <= bytes.length-1) { //look out for array with odd number of bytes
						short val = getShorty(bytes[i], bytes[i+1]);
						pcm_buf[pcm_idx] = val;
						pcm_idx++;
					}
				}
				vos.write(pcm_buf, 0, pcm_buf.length);
				loops++;
				System.out.println("loop " + loops);
			}		
			
			inFile.close();
			vos.close();
			
			float endTime = System.nanoTime()/1000000;
			float totTime = endTime - startTime;
			System.out.println(totTime + "ms to encode ogg file");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private short getShorty(byte firstByte, byte secondByte) { //packs the 2-byte pcm value into a single short
		
		ByteBuffer bb = ByteBuffer.allocate(2);
	    bb.order(ByteOrder.LITTLE_ENDIAN);
	    bb.put(firstByte);
	    bb.put(secondByte);
	    short shortVal = bb.getShort(0);
	    
	    return shortVal;
	}
	
	public static int byteArrayToInt(byte[] b) {
		int start = 0;
		int low = b[start] & 0xff;
		int high = b[start+1] & 0xff;
		return ( high << 8 | low );
	}
	
	public static long byteArrayToLong(byte[] b) {
		int start = 0;
		int i = 0;
		int len = 4;
		int cnt = 0;
		byte[] tmp = new byte[len];
		for (i = start; i < (start + len); i++)
		{
			tmp[cnt] = b[i];
			cnt++;
		}
		long accum = 0;
		i = 0;
		for ( int shiftBy = 0; shiftBy < 32; shiftBy += 8 )
		{
			accum |= ( (long)( tmp[i] & 0xff ) ) << shiftBy;
			i++;
		}
		return accum;
	}

}
