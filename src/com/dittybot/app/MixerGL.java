package com.dittybot.app;

/**
 * this is the class that handles drawing all the OpenGL 
 * graphics for SongMixer
 */

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.android.texample2.GLText;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLUtils;
import android.opengl.Matrix;


public class MixerGL implements Renderer {	
	
	Context context;
	private GlobalVars gv;	
	float density; //screen density
	float screenWidth;
	
	private float[] cntrlsMtx = new float[16]; //track controls model matrix. xyz @ indices 12, 13, 14
	private float[] bodyMtx = new float[16]; //mixer body that contains all the tblocks
	private float[] viewMtx = new float[16]; //aka camera view
	private float[] projectionMtx = new float[16];
	private float[] mvpMtx = new float[16]; //all the matrices multiplied

	
	public TrackBlocks tBlocks; //colored bars that represent notes of the song	
	
	private List<TrackControl> tCtrls = new ArrayList<TrackControl>(); //list to loop over to draw tracks
	
	private int colorShaderID;
	private int textureShaderID;
	private int circleShaderID;
	private int rrShaderID; //round rectangle shader
	
	private boolean drawScene = false;
	
	public boolean play = false;	
	
	private float dx; //delta values received from swiping on touch screen
	private float dy;
	public int moving = 0; //0 = not moving, 1 = finger is down, actively moving, 2 = finger has been lifted, object is coasting to a stop
	private boolean moved; //makes sure the translation happens just once
	
	private GLText glText;
	
	private RoundRect2 rrect;
	private boolean rectReady = false;
	 
	
	public MixerGL(Context context) { //constructor
		System.out.println("MixerGL created");	
		
		this.context = context;	
		
		density = context.getResources().getDisplayMetrics().density;				
		
        gv = ((GlobalVars)context.getApplicationContext());
        screenWidth = gv.screenWidth;        
        
	}
	
	public int hitTest(float x, float y) { //when user taps an item on screen determine which one by position data
		System.out.println("hitTest() " + x +" "+ y);
		int code = -1;
		
		return code;
	}
	
	public void init_tBlocks(float[] vertexData) {
		//System.out.println("init_tBlocks()");		
		
		tBlocks = new TrackBlocks(vertexData);
		
		drawScene = true;
	}
	
	public void init_tCtrl(String imgPath, float size, float ledWidth) {
		//System.out.println("init_tCtrl()");
		
		int position = tCtrls.size();
		TrackControl tCtrl = new TrackControl(imgPath, size, ledWidth, position);
		
		//add new tCtrl object to list
		tCtrls.add(tCtrl); 
	}
	
	public void init_rndRect() {  
		System.out.println("init_rndRect()");
		float[] color = {0.1f, 0.4f, 0.4f, 1.0f};
		
		rrect = new RoundRect2(60, 250, 8, color);
		
		rectReady = true;
	}
	
	public void set_xy(float dx, float dy) {
		this.dx = dx;
		this.dy = dy;
		moved = false;
		//System.out.println("set_xy() dx:" + dx + " dy:" + dy);
	}
	
	@Override
	public void onSurfaceCreated(GL10 unused, EGLConfig config) {	
		System.out.println("onSurfaceCreated()");
		
		
		
		//==============================================================================
		//compile the shaders 
        if (makeCshader()) { //compile color shader program
        	System.out.println("color shader compiled successfully");
        } 
        
        if (makeTshader()) { //compile texture shader program
        	System.out.println("texture shader compiled successfully");
        }
        
        if (makeCirShader()) { //compile circle shader program
        	System.out.println("circle shader compiled successfully");
        }        
        
        if (makeRRShader()) { //compile circle shader program
        	System.out.println("RR shader compiled successfully");
        }
        
        //===============================================================================      
        //set up the matrices
	    
		final float eyeX = 0.0f;
		final float eyeY = 0.0f;
		final float eyeZ = 1.5f;	
		
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = -5.0f;	
		
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;
		
        Matrix.setLookAtM(viewMtx, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);
        
        //set matrices to identity so can use for translation
        Matrix.setIdentityM(bodyMtx, 0);
        Matrix.setIdentityM(cntrlsMtx, 0);
        
 
        //====================================================================================
        //text test
        
        glText = new GLText(context.getAssets());
        glText.load("telegrafico.ttf", 14, 0, 2);  // Create Font (Height: 14 Pixels / X+Y Padding 2 Pixels)
        
        // enable texture + alpha blending
     	GLES20.glEnable(GLES20.GL_BLEND);
     	GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
     	
     	//====================================================================================
     	
     	init_rndRect();
        
        // Set the background color        
        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f); //RGBA 0 -> 1 range for each
		
	}
	//---------------------------------------------------------------------------------------------

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) { //system reports these when the glsurface is inflated to screen
		System.out.println("onSurfaceChanged() width " + width + " height " + height);
		GLES20.glViewport(0, 0, width, height);			 
		
		//sets coordinate layout. puts origin in top left corner. y goes negative as go down		
		Matrix.orthoM(projectionMtx, 0, //matrix, offset
				0, width,   //left, right
				-height, 0, //bottom, top
				0.0f, 50.0f); //near, far		
	}	
	//-----------------------------------------------------------------------------------------------
	
	float pos = 0.1f; //just using to test translation
	
	//float[] vpMtx = new float[16]; //GLText uses
	
	@Override
	public void onDrawFrame(GL10 unused) {
		GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT); //clear screen		
		
		//getFPS();
		
		if (moving == 1) { //user is dragging finger on touch screen		
			Matrix.translateM(cntrlsMtx, 0, 0, dy, 0);	
			Matrix.translateM(rrect.modelMtx, 0, dx, dy, 0);
			moved = true; //only move the delta once			
		}
		else if (moving == 2) { //finger has been lifted from touch screen
			dx *= .95; //reduce distance to move on each frame for fling deceleration effect
			dy *= .95;
			if (Math.abs(dx) < 0.05 && Math.abs(dy) < 0.05) {
				moving = 0; //no longer moving
				dx = 0;
				dy = 0;
			}
			else {
				Matrix.translateM(cntrlsMtx, 0, 0, dy, 0);
				Matrix.translateM(rrect.modelMtx, 0, dx, dy, 0);
			}
			
		}
		
		/*
		Matrix.multiplyMM(mvpMtx, 0, viewMtx, 0, iTracksHdr.modelMtx, 0);         
        Matrix.multiplyMM(mvpMtx, 0, projectionMtx, 0, mvpMtx, 0);
        iTracksHdr.draw(mvpMtx);
		*/
		//TODO move these to the draw() and just pass the model matrix? I wonder though if
		//that wouldn't cause some weird issues-- stuff changing before it gets handled in function 
		Matrix.multiplyMM(mvpMtx, 0, viewMtx, 0, cntrlsMtx, 0); //the m & v of mvp        
        Matrix.multiplyMM(mvpMtx, 0, projectionMtx, 0, mvpMtx, 0);          
        
        for (int i=0; i < tCtrls.size(); i++) {
        	tCtrls.get(i).draw(mvpMtx);
        }
        
        /*
        Matrix.multiplyMM(vpMtx, 0, projectionMtx, 0, viewMtx, 0);
		glText.begin(0.0f, 0.0f, 0.0f, 1.0f, vpMtx); //Begin Text Rendering	
		glText.draw("YO DO EET", 40, -100);	
		glText.end();
		*/
        
        if (rectReady) {
        	Matrix.multiplyMM(mvpMtx, 0, viewMtx, 0, rrect.modelMtx, 0); //the m & v of mvp        
            Matrix.multiplyMM(mvpMtx, 0, projectionMtx, 0, mvpMtx, 0);
            rrect.draw(mvpMtx);
        }
        
	}
	//------------------------------------------------------------------------------------------------

	private boolean makeCshader() { //compile shader program for per vertex color
		
		boolean success = true;
		
		final String vertexShader =
					"uniform mat4 u_MVPMatrix;      \n"		// A constant representing the combined model/view/projection matrix.
    			
	    		  + "attribute vec4 a_Position;     \n"		// Per-vertex position information we will pass in.
	    		  + "attribute vec4 a_Color;        \n"		// Per-vertex color information we will pass in.			  
	    		  
	    		  + "varying vec4 v_Color;          \n"		// This will be passed into the fragment shader.
	    		  
	    		  + "void main()                    \n"		// The entry point for our vertex shader.
	    		  + "{                              \n"
	    		  + "   v_Color = a_Color;          \n"		// Pass the color through to the fragment shader. 
	    		  											// It will be interpolated across the triangle.
	    		  + "   gl_Position = u_MVPMatrix   \n" 	// gl_Position is a special variable used to store the final position.
	    		  + "               * a_Position;   \n"     // Multiply the vertex by the matrix to get the final point in 			                                            			 
	    		  + "}                              \n";    // normalized screen coordinates.
		
		final String fragmentShader =
    			"precision mediump float;       \n"		// Set the default precision to medium. We don't need as high of a 
														// precision in the fragment shader.				
				+ "varying vec4 v_Color;          \n"	// This is the color from the vertex shader interpolated across the 
														// triangle per fragment.			  
				+ "void main()                    \n"	// The entry point for our fragment shader.
				+ "{                              \n"
				+ "   gl_FragColor = v_Color;     \n"	// Pass the color directly through the pipeline.		  
				+ "} 							  \n";
		
		// Load in the vertex shader.
     	int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
     	GLES20.glShaderSource(vertexShaderHandle, vertexShader);
     	GLES20.glCompileShader(vertexShaderHandle);	
     	
		// Load in the fragment shader shader.
		int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
		GLES20.glCompileShader(fragmentShaderHandle);			
		
		// Create shader program 
		colorShaderID = GLES20.glCreateProgram();
		GLES20.glAttachShader(colorShaderID, vertexShaderHandle);
		GLES20.glAttachShader(colorShaderID, fragmentShaderHandle);			
		GLES20.glBindAttribLocation(colorShaderID, 0, "a_Position");
		GLES20.glBindAttribLocation(colorShaderID, 1, "a_Color");
		GLES20.glLinkProgram(colorShaderID);
		
		// Get the link status.
		final int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(colorShaderID, GLES20.GL_LINK_STATUS, linkStatus, 0);

		// If the link failed, delete the program.
		if (linkStatus[0] == 0) 
		{				
			GLES20.glDeleteProgram(colorShaderID);
			colorShaderID = -1;
			success = false;
		}
		
		return success;
	}
	
	private boolean makeTshader() { //compile shader for bitmap texture
		
		boolean success = true;
		
		final String vertexShader =
			    "uniform mat4 uMVPMatrix;" +
			    "attribute vec4 vPosition;" +
			    "attribute vec2 a_texCoord;" +
			    "varying vec2 v_texCoord;" +
			    "void main() {" +
			    "  gl_Position = uMVPMatrix * vPosition;" +
			    "  v_texCoord = a_texCoord;" +
			    "}";		
		
		final String fragmentShader =
			    "precision mediump float;" +
			    "varying vec2 v_texCoord;" +
			    "uniform sampler2D s_texture;" +
			    "void main() {" +
			    "  gl_FragColor = texture2D(s_texture, v_texCoord);" +
			    "}";
				
		
		// Load in the vertex shader.
     	int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
     	GLES20.glShaderSource(vertexShaderHandle, vertexShader);
     	GLES20.glCompileShader(vertexShaderHandle);     	
     	
		// Load in the fragment shader shader.
		int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
		GLES20.glCompileShader(fragmentShaderHandle);			
		
		// Create shader program 
		textureShaderID = GLES20.glCreateProgram();
		GLES20.glAttachShader(textureShaderID, vertexShaderHandle);   
		GLES20.glAttachShader(textureShaderID, fragmentShaderHandle);		
		GLES20.glLinkProgram(textureShaderID);		
		
		// Get the link status.
		final int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(textureShaderID, GLES20.GL_LINK_STATUS, linkStatus, 0);

		// If the link failed, delete the program.
		if (linkStatus[0] == 0) 
		{				
			GLES20.glDeleteProgram(textureShaderID);
			textureShaderID = -1;
			success = false;
		}
		
		return success;
	}
	
	private boolean makeCirShader() { //shader to draw rounded corners of rounded rectangles
		
		boolean success = true;
		
		final String vertexShader = 
				"uniform mat4 u_MVPMatrix;" +
				"attribute vec4 a_position;" +
				"attribute vec2 a_texCoord;" +
			    "varying vec2 v_texCoord;" +
				"void main() {" +				
				"  gl_Position = u_MVPMatrix * a_position;" +
				"  v_texCoord = a_texCoord;" +
				"}";                          
		
		
		final String fragmentShader = 
				"precision mediump float;" +
				"varying vec2 v_texCoord;" +				
				"uniform vec4 circle_color;" +
				"uniform vec2 circle_center;" +
				"void main() {" +
				"  vec2 uv = v_texCoord.xy;" +
				"  uv -= circle_center;" +
				"  float dist = sqrt(dot(uv, uv));" +
				"  if (dist <= 1.0) gl_FragColor = circle_color;" +
				"  else gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);" +
			    "}";		

		
		// Load in the vertex shader.
     	int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
     	GLES20.glShaderSource(vertexShaderHandle, vertexShader);
     	GLES20.glCompileShader(vertexShaderHandle);     	
     	
		// Load in the fragment shader shader.
		int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
		GLES20.glCompileShader(fragmentShaderHandle);			
		
		// Create shader program 
		circleShaderID = GLES20.glCreateProgram();
		GLES20.glAttachShader(circleShaderID, vertexShaderHandle);   
		GLES20.glAttachShader(circleShaderID, fragmentShaderHandle);		
		GLES20.glLinkProgram(circleShaderID);		
		
		// Get the link status.
		final int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(circleShaderID, GLES20.GL_LINK_STATUS, linkStatus, 0);

		// If the link failed, delete the program.
		if (linkStatus[0] == 0) 
		{				
			GLES20.glDeleteProgram(circleShaderID);
			circleShaderID = -1;
			success = false;
		}
		
		return success;
	}
	
	private boolean makeRRShader() {
		boolean success = true;
		
		final String vertexShader = 
				"uniform mat4 u_MVPMatrix;" +
				"attribute vec4 a_position;" +
				"attribute vec2 a_texCoord;" +
			    "varying vec2 v_texCoord;" +
				"void main() {" +				
				"  gl_Position = u_MVPMatrix * a_position;" +
				"  v_texCoord = a_texCoord;" +
				"}"; 
		
		final String fragmentShader = 
				"precision mediump float;" +
				"varying vec2 v_texCoord;" +				
				"uniform vec4 color;" +
				"uniform vec2 center1;" +
				"uniform vec2 center2;" +
				"uniform vec2 center3;" +
				"uniform vec2 center4;" +
				"uniform float midline;" +
				"float dist;" +
				"float delta = 1.5;" + //arbitrary value. boundary to start antialiasing. TODO maybe implement code that does what fwidth does (not in ES 2.0)
				"float alpha;" +				
				"void main() {" +
					"if (v_texCoord.x <= center1.x) {" + //left side
						"if (v_texCoord.y <= center1.y) {" + //upper left corner
							"dist = distance(v_texCoord, center1);" +		
						"}" +
						"else if (v_texCoord.y >= center3.y) {" + //lower left corner
							"dist = distance(v_texCoord, center3);" +		
						"}" +
						"else {" + //left middle 
							"dist = center1.x - v_texCoord.x;" +		
						"}" +
						"alpha = smoothstep(center1.x-delta, center1.x, dist);" +
					"}" +
					"else if (v_texCoord.x > center1.x && v_texCoord.x < center2.x) {" + //middle part of rectangle
						"dist = abs(v_texCoord.y - midline);" +
						"alpha = smoothstep(midline-delta, midline, dist);" +
					"}" +
					"else {" + //right side
						"if (v_texCoord.y <= center2.y) {" + //upper right corner
							"dist = distance(v_texCoord, center2);" +		
						"}" +
						"else if (v_texCoord.y >= center4.y) {" + //lower right corner
							"dist = distance(v_texCoord, center4);" +		
						"}" +
						"else {" + //right middle
							"dist = v_texCoord.x - center2.x;" +		
						"}" +
						"alpha = smoothstep(center1.x-delta, center1.x, dist);" +
					"}" +
					"gl_FragColor = mix(color, vec4(0.0, 0.0, 0.0, 0.0), alpha);" +
			    "}";			

		
		// Load in the vertex shader.
     	int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
     	GLES20.glShaderSource(vertexShaderHandle, vertexShader);
     	GLES20.glCompileShader(vertexShaderHandle);     	
     	
		// Load in the fragment shader shader.
		int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
		GLES20.glCompileShader(fragmentShaderHandle);			
		
		// Create shader program 
		rrShaderID = GLES20.glCreateProgram();
		GLES20.glAttachShader(rrShaderID, vertexShaderHandle);   
		GLES20.glAttachShader(rrShaderID, fragmentShaderHandle);		
		GLES20.glLinkProgram(rrShaderID);		
		
		// Get the link status.
		final int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(rrShaderID, GLES20.GL_LINK_STATUS, linkStatus, 0);

		// If the link failed, delete the program.
		if (linkStatus[0] == 0) 
		{				
			GLES20.glDeleteProgram(rrShaderID);
			rrShaderID = -1;
			success = false;
		}
		
		return success;
	}
	
	//========================================================================
	long lastTime = 0;
	long currTime = 0;
	long eTime = 0;
	int frameCount = 0;
	private void getFPS() {
		frameCount++;
		currTime = System.nanoTime();
		if (lastTime != 0) {
			eTime += (currTime - lastTime)/1000000;
			if (eTime >= 1000) {
				eTime = 0;
				System.out.println(frameCount + " fps");
				frameCount = 0;
			}
		}
		lastTime = currTime;
	}
	
	public static void destroyBuffer(Buffer buffer) {
	    if(buffer.isDirect()) {
	        try {
	            if(!buffer.getClass().getName().equals("java.nio.DirectByteBuffer")) {
	                Field attField = buffer.getClass().getDeclaredField("att");
	                attField.setAccessible(true);
	                buffer = (Buffer) attField.get(buffer);
	            }

	            Method cleanerMethod = buffer.getClass().getMethod("cleaner");
	            cleanerMethod.setAccessible(true);
	            Object cleaner = cleanerMethod.invoke(buffer);
	            Method cleanMethod = cleaner.getClass().getMethod("clean");
	            cleanMethod.setAccessible(true);
	            cleanMethod.invoke(cleaner);
	        } catch(Exception e) {
	        	//System.out.println("Could not destroy direct buffer " + buffer);	        	
	        }
	    }
	}
	
    
	//===============================================================================================
	
	public class Header {
		
		final float[] vertexData = {
				 
				//triangle 1
	            0.0f, 0.0f, 0.0f, // X, Y, Z,  upper left
	            0.498f, 0.0f, 0.0f, 1.0f,	// R, G, B, A            
	            0.0f, -40f, 0.0f, //lower left
	            0.133f, 0.133f, 0.133f, 1.0f,	            
	            480, -40f, 0.0f, //lower right
	            0.133f, 0.133f, 0.133f, 1.0f,
	            
	            //triangle 2	            
	            0.0f, 0.0f, 0.0f, //upper left
	            0.498f, 0.0f, 0.0f, 1.0f,
	            480, -40f, 0.0f, //lower right
	            0.133f, 0.133f, 0.133f, 1.0f,
	            480f, 0.0f, 0.0f, //upper right
	            0.498f, 0.0f, 0.0f, 1.0f}; 
		
		private final int bytesPerFloat = 4;
		private final int stride = 7; //xyz rgba for each vertex
		private final int strideBytes = stride * bytesPerFloat;
		private final int positionOffset = 0;
		private final int positionDataSize = 3; //xyz
		private final int colorOffset = 3;
		private final int colorDataSize = 4;
		private int numVerts; //total number of vertices in object. used in gl draw call
		
		private final FloatBuffer vertsBuffer;
		private int positionHandle;
		private int colorHandle;
		private int mvpMatrixHandle;
		
		public float[] modelMtx = new float[16];
		
		
		public Header() {
			
			numVerts = vertexData.length/stride;			
			
			vertsBuffer = ByteBuffer.allocateDirect(vertexData.length * bytesPerFloat)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			vertsBuffer.put(vertexData).position(0);
			
	        mvpMatrixHandle = GLES20.glGetUniformLocation(colorShaderID, "u_MVPMatrix");        
	        positionHandle = GLES20.glGetAttribLocation(colorShaderID, "a_Position");
	        colorHandle = GLES20.glGetAttribLocation(colorShaderID, "a_Color"); 
	        
	        Matrix.setIdentityM(this.modelMtx, 0);
		}
		
		public void draw(float[] mvpMatrix) {	
			
			//shader program to use when rendering.
	        GLES20.glUseProgram(colorShaderID);  
			
			//Pass in the vertex position info
			vertsBuffer.position(positionOffset);
	        GLES20.glVertexAttribPointer(positionHandle, positionDataSize, GLES20.GL_FLOAT, false,
	        		strideBytes, vertsBuffer); 	                
	        GLES20.glEnableVertexAttribArray(positionHandle);        
	        
	        //Pass in the vertex color info
	        vertsBuffer.position(colorOffset);
	        GLES20.glVertexAttribPointer(colorHandle, colorDataSize, GLES20.GL_FLOAT, false,
	        		strideBytes, vertsBuffer); 	        
	        GLES20.glEnableVertexAttribArray(colorHandle);		

	        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
	        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, numVerts); 
	        
	        GLES20.glDisableVertexAttribArray(positionHandle);
	        GLES20.glDisableVertexAttribArray(colorHandle);
		}	
		
	}
	
	public class TrackControl {	
		
		private final short[] indices =  {0, 1, 2, 0, 2, 3}; // The order of vertex rendering.
		
		private final float[] uvs = {
			      0.0f, 0.0f,
			      0.0f, 1.0f,
			      1.0f, 1.0f,
			      1.0f, 0.0f};
		
		private final int bytesPerFloat = 4;		
		private final int positionDataSize = 3; //xyz
		private int numVerts = 6; //2 triangles to make a square
		
		private final FloatBuffer imgVertsBfr;
		private final FloatBuffer grnLEDVBfr;
		private final FloatBuffer redLEDVBfr;
		private final ShortBuffer drawListBfr;
		private final FloatBuffer uvBfr;
		
		//for LED
		private int colorHandle;
		private int colorPos;
		private int colmvphandle;
		
		//for image texture
		private int positionHandle;
		private int texCoordHandle;
		private int sampleHandle;
		public int textureHandle;
		private int mvpMatrixHandle;
		
		public boolean isOn = true; //toggles green/red of track "LED" indicator. On by default
		
		public TrackControl(String imgPath, float size, float ledWidth, int position) {	
			
			float[] grnLEDVerts = {
					0,-size*position,0, //position
					0.0f, 1.0f, 0.0f, 1.0f, //color
					0,-size*(position+1),0,
					0.0f, 1.0f, 0.0f, 1.0f,
					ledWidth,-size*(position+1),0,
					0.0f, 1.0f, 0.0f, 1.0f,
					ledWidth,-size*position,0,
					0.0f, 1.0f, 0.0f, 1.0f,
			};
			
			float[] redLEDVerts = { //a bit clunky to do this way, but actually saves on not needing another shader
					0,-size*position,0,
					1.0f, 0.0f, 0.0f, 1.0f,
					0,-size*(position+1),0,
					1.0f, 0.0f, 0.0f, 1.0f,
					ledWidth,-size*(position+1),0,
					1.0f, 0.0f, 0.0f, 1.0f,
					ledWidth,-size*position,0,
					1.0f, 0.0f, 0.0f, 1.0f,
			};			
			
			float[] imgSqVerts = { //square to display instrument image
					ledWidth+1, -size*position, 0, 		//upper left
					ledWidth+1, -size*(position+1), 0,   //lower left
					size+ledWidth+1, -size*(position+1), 0,//lower right
					size+ledWidth+1, -size*position, 0		//upper right
			};
			
			//LEDs-------------------------------------------------------------			
			grnLEDVBfr = ByteBuffer.allocateDirect(grnLEDVerts.length * bytesPerFloat)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			grnLEDVBfr.put(grnLEDVerts).position(0);
			
			redLEDVBfr = ByteBuffer.allocateDirect(redLEDVerts.length * bytesPerFloat)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			redLEDVBfr.put(redLEDVerts).position(0);
			
			colorHandle = GLES20.glGetAttribLocation(colorShaderID, "a_Color");
			colorPos = GLES20.glGetAttribLocation(colorShaderID, "a_Position");
			colmvphandle = GLES20.glGetUniformLocation(colorShaderID, "u_MVPMatrix");
			
			//-----------------------------------------------------------------			
			
			int[] textureID = new int[1];
			GLES20.glGenTextures(1, textureID, 0);
			textureHandle = textureID[0];		
							
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inScaled = false;   //prevent the pre-scaling Android does
			Bitmap img = BitmapFactory.decodeFile(imgPath, options);					
			
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle); //must be bound to initialize below
			
			// Set filtering
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
	        
	        // Set wrapping mode	        
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, 
	        		GLES20.GL_CLAMP_TO_EDGE);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, 
	        		GLES20.GL_CLAMP_TO_EDGE);
	        
	        // Load the bitmap into the bound texture.
	        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0); //texImage2D stores img data on GPU      
	        
			img.recycle(); //bitmap data has been copied so delete it	
			
			
			imgVertsBfr = ByteBuffer.allocateDirect(imgSqVerts.length * bytesPerFloat)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			imgVertsBfr.put(imgSqVerts).position(0);			
			
			drawListBfr = ByteBuffer.allocateDirect(indices.length * 2)
			        .order(ByteOrder.nativeOrder()).asShortBuffer();
			drawListBfr.put(indices).position(0);
			
			uvBfr = ByteBuffer.allocateDirect(uvs.length * bytesPerFloat)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			uvBfr.put(uvs).position(0);
			
			mvpMatrixHandle = GLES20.glGetUniformLocation(textureShaderID, "uMVPMatrix");        
	        positionHandle = GLES20.glGetAttribLocation(textureShaderID, "vPosition");
	        texCoordHandle = GLES20.glGetAttribLocation(textureShaderID, "a_texCoord");
	        sampleHandle = GLES20.glGetAttribLocation(textureShaderID, "s_texture");	        
	        
		}
		
		public void draw(float[] mvpMatrix) {
			
			//draw LED bar first ------------------------------------------------------------------------		
			GLES20.glUseProgram(colorShaderID);
			
			grnLEDVBfr.position(0);			
	        if (isOn) GLES20.glVertexAttribPointer(colorPos, 3, GLES20.GL_FLOAT, false,
	        		28, grnLEDVBfr);
	        else GLES20.glVertexAttribPointer(colorPos, 3, GLES20.GL_FLOAT, false,
	        		28, redLEDVBfr);			
			 	                
	        GLES20.glEnableVertexAttribArray(colorPos); 
	        
	        //Pass in the vertex color info	        
	        grnLEDVBfr.position(3); //jump over 1st vertex data to interleaved
	        if (isOn) GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false,
	        		28, grnLEDVBfr); 
	        else GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false,
	        		28, redLEDVBfr); 	        	
	        
	        GLES20.glEnableVertexAttribArray(colorHandle);	        
	        
	        GLES20.glUniformMatrix4fv(colmvphandle, 1, false, mvpMatrix, 0);
	        
	        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
	                GLES20.GL_UNSIGNED_SHORT, drawListBfr);
	        
	        GLES20.glDisableVertexAttribArray(colorPos);
	        GLES20.glDisableVertexAttribArray(colorHandle);			
			
			
			//then draw the image panel ------------------------------------------------------------------
	        GLES20.glUseProgram(textureShaderID); 
	        
	        int circle_color = GLES20.glGetUniformLocation(textureShaderID, "circle_color");
	        GLES20.glUniform4f(circle_color, 1.0f, 0.0f, 0.0f, 1.0f);
	        
	        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);
	        
	        GLES20.glVertexAttribPointer(positionHandle, positionDataSize, GLES20.GL_FLOAT, false,
	        		0, imgVertsBfr); 	        
	        GLES20.glEnableVertexAttribArray(positionHandle); 
	        
	        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBfr);
	        GLES20.glEnableVertexAttribArray(texCoordHandle);
	        
	        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);	        
	        
	        GLES20.glUniform1i(sampleHandle, 0);	        
	       
	        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
	                GLES20.GL_UNSIGNED_SHORT, drawListBfr);
	        
	        GLES20.glDisableVertexAttribArray(positionHandle);
	        GLES20.glDisableVertexAttribArray(texCoordHandle);
		}
	}
	
	public class TrackBlocks { //all the track-level note blocks in one object
				
		private final int bytesPerFloat = 4;
		private final int stride = 7; //xyz rgba for each vertex
		private final int strideBytes = stride * bytesPerFloat;
		private final int positionOffset = 0;
		private final int positionDataSize = 3; //xyz
		private final int colorOffset = 3;
		private final int colorDataSize = 4;
		private int numVerts; //total number of vertices in object. used in gl draw call
		
		private final FloatBuffer blocksBuffer;
		private int positionHandle;
		private int colorHandle;
		private int mvpMatrixHandle;
		
		//public int moving = 0; //0 = not moving, 1 = finger is down, actively moving, 2 = finger has been lifted, object is coasting to a stop
		
		
		public TrackBlocks(float[] vertexData) { //constructor
			System.out.println("TrackBlocks constructor");
			numVerts = vertexData.length/stride;			
			
			blocksBuffer = ByteBuffer.allocateDirect(vertexData.length * bytesPerFloat)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			blocksBuffer.put(vertexData).position(0);
			
	        mvpMatrixHandle = GLES20.glGetUniformLocation(colorShaderID, "u_MVPMatrix");        
	        positionHandle = GLES20.glGetAttribLocation(colorShaderID, "a_Position");
	        colorHandle = GLES20.glGetAttribLocation(colorShaderID, "a_Color");  
	        
	        //drawIt = true;	        
		}
		
		public void draw(float[] mvpMatrix) {	
			
			//shader program to use when rendering.
	        GLES20.glUseProgram(colorShaderID);  
			
			//Pass in the vertex position info
			blocksBuffer.position(positionOffset);
	        GLES20.glVertexAttribPointer(positionHandle, positionDataSize, GLES20.GL_FLOAT, false,
	        		strideBytes, blocksBuffer); 	                
	        GLES20.glEnableVertexAttribArray(positionHandle);        
	        
	        //Pass in the vertex color info
	        blocksBuffer.position(colorOffset);
	        GLES20.glVertexAttribPointer(colorHandle, colorDataSize, GLES20.GL_FLOAT, false,
	        		strideBytes, blocksBuffer); 	        
	        GLES20.glEnableVertexAttribArray(colorHandle);		

	        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
	        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, numVerts); 
	        
	        GLES20.glDisableVertexAttribArray(positionHandle);
	        GLES20.glDisableVertexAttribArray(colorHandle);
		}	
	}
	
	public class RoundRect {
		
		public float[] modelMtx = new float[16];
		
		private RoundCorner[] corners = new RoundCorner[4];
		private float[] color;
		
		private final short[] indices =  {0, 1, 2, 0, 2, 3}; // The order of vertex rendering.		
				
		private final int positionDataSize = 3; //xyz	
		
		private final FloatBuffer vertsBfr;
		private final ShortBuffer drawListBfr;
		
		
		private int positionHandle;		
		private int mvpMatrixHandle;	
		
		
		public RoundRect(PointF pos, float height, float width, float radius, float[] color) {
			
			Matrix.setIdentityM(this.modelMtx, 0);
			
			this.color = color;
			
			
			//create the 4 rounded corners ---------------------------------------
			
			//UV values used to draw the right circle quadrant for each corner
			PointF[] centers = new PointF[4];
			centers[0] = new PointF(1.0f, 1.0f); //upper left quadrant
			centers[1] = new PointF(1.0f, 0.0f); //lower left quadrant
			centers[2] = new PointF(0.0f, 0.0f); //lower right quadrant
			centers[3] = new PointF(0.0f, 1.0f); //upper right quadrant			
			
			
			float[] verts = { //radius square
					0, 0, 0,
					0, -radius, 0,
					radius, -radius, 0,
					radius, 0, 0
			};  
			
			vertsBfr = ByteBuffer.allocateDirect(verts.length * 4) //4 bytes per float
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			vertsBfr.put(verts).position(0);			
			
			drawListBfr = ByteBuffer.allocateDirect(indices.length * 2)
			        .order(ByteOrder.nativeOrder()).asShortBuffer();
			drawListBfr.put(indices).position(0);			
			
			mvpMatrixHandle = GLES20.glGetUniformLocation(circleShaderID, "u_MVPMatrix");        
	        positionHandle = GLES20.glGetAttribLocation(circleShaderID, "a_position");
	        
	        
		}
		
		public void draw(float[] mvpMatrix) {
			
			GLES20.glUseProgram(colorShaderID);		       
			
			GLES20.glVertexAttribPointer(positionHandle, positionDataSize, GLES20.GL_FLOAT, false,
	        		0, vertsBfr); 	        
	        GLES20.glEnableVertexAttribArray(positionHandle); 	        
	        
	        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);	
	        
	        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
	                GLES20.GL_UNSIGNED_SHORT, drawListBfr);
	        
	        GLES20.glDisableVertexAttribArray(positionHandle);      
			
		}
		
	}
	
	public class RoundRect2 {
		
		public float[] modelMtx = new float[16];
		
		private float[] color;
		
		private final short[] indices =  {0, 1, 2, 0, 2, 3}; //order to connect vertices into triangles		
		
		private float[] uvs = new float[8];
		
		private final FloatBuffer vertsBfr;
		private final ShortBuffer drawListBfr;
		private final FloatBuffer uvBfr;		
		
		private int positionHandle;	
		private int texCoordHandle;
		private int colorHandle;
		private int mvpMatrixHandle;
		private int center1loc;
		private PointF center1 = new PointF();
		private int center2loc;
		private PointF center2 = new PointF();
		private int center3loc;
		private PointF center3 = new PointF();
		private int center4loc;
		private PointF center4 = new PointF();
		private int midlloc;
		private float midline; //y value that bisects the rectangle lengthwise
		
		public RoundRect2(float height, float width, float radius, float[] color) {
			
			Matrix.setIdentityM(this.modelMtx, 0);
			
			this.color = color;
			
			//quad for rectangle shape
			float[] verts = { 
					0, 0, 0,			//upper left
					0, -height, 0,		//lower left
					width, -height, 0, 	//lower right
					width, 0, 0			//upper right
			};			
			
			uvs[0] = 0;	uvs[1] = 0;
			uvs[2] = 0;	uvs[3] = height;
			uvs[4] = width;	uvs[5] = height;
			uvs[6] = width;	uvs[7] = 0;			
			
			center1.x = radius; center1.y = radius;
			center2.x = width-radius; center2.y = radius;
			center3.x = radius; center3.y = height-radius;
			center4.x = width-radius; center4.y = height-radius;
			
			midline = height/2;
			
			vertsBfr = ByteBuffer.allocateDirect(verts.length * 4) //4 bytes per float
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			vertsBfr.put(verts).position(0);			
			
			drawListBfr = ByteBuffer.allocateDirect(indices.length * 2)
			        .order(ByteOrder.nativeOrder()).asShortBuffer();
			drawListBfr.put(indices).position(0);	
			
			uvBfr = ByteBuffer.allocateDirect(uvs.length * 4)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			uvBfr.put(uvs).position(0);
			
			mvpMatrixHandle = GLES20.glGetUniformLocation(rrShaderID, "u_MVPMatrix");        
	        positionHandle = GLES20.glGetAttribLocation(rrShaderID, "a_position");
	        texCoordHandle = GLES20.glGetAttribLocation(rrShaderID, "a_texCoord");
	        colorHandle = GLES20.glGetUniformLocation(rrShaderID, "color");
	        center1loc = GLES20.glGetUniformLocation(rrShaderID, "center1");
	        center2loc = GLES20.glGetUniformLocation(rrShaderID, "center2");
	        center3loc = GLES20.glGetUniformLocation(rrShaderID, "center3");
	        center4loc = GLES20.glGetUniformLocation(rrShaderID, "center4");
	        midlloc = GLES20.glGetUniformLocation(rrShaderID, "midline");
			
		}
		
		public void draw(float[] mvpMatrix) {
			
			GLES20.glUseProgram(rrShaderID);			
			
	        GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3]);
	        
	        GLES20.glUniform2f(center1loc, center1.x, center1.y); 
	        GLES20.glUniform2f(center2loc, center2.x, center2.y); 
	        GLES20.glUniform2f(center3loc, center3.x, center3.y); 
	        GLES20.glUniform2f(center4loc, center4.x, center4.y); 
	        GLES20.glUniform1f(midlloc, midline);
			
			GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false,
	        		0, vertsBfr); 	        
	        GLES20.glEnableVertexAttribArray(positionHandle); 
	        
	        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBfr);
	        GLES20.glEnableVertexAttribArray(texCoordHandle);
	        
	        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);	
	        
	        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
	                GLES20.GL_UNSIGNED_SHORT, drawListBfr);
	        
	        GLES20.glDisableVertexAttribArray(positionHandle);
	        GLES20.glDisableVertexAttribArray(texCoordHandle);
		}
	}
	
	public class RoundCorner {
		
		private PointF center;
		private float[] color;
		
		private final short[] indices =  {0, 1, 2, 0, 2, 3}; //order to connect vertices into triangles
		
		private final float[] uvs = {
			      0.0f, 0.0f,
			      0.0f, 1.0f,
			      1.0f, 1.0f,
			      1.0f, 0.0f		};
		
		private final int posDataSize = 3; //xyz	
		
		private final FloatBuffer crnrVertsBfr;
		private final ShortBuffer drawListBfr;
		private final FloatBuffer uvBfr;
		
		private int positionHandle;
		private int texCoordHandle;
		private int mvpMatrixHandle;
		private int colorHandle; //handle to the uniform in shader
		private int centerHandle; //handle to the uniform in shader		
		
		
		public RoundCorner(PointF pos, PointF center, float radius, float[] color) {
			
			this.color = color;
			
			float[] crnrVerts = { //radius square
					pos.x, pos.y, 0,				//upper left
					pos.x, pos.y-radius, 0,			//lower left
					pos.x+radius, pos.y-radius, 0, 	//lower right
					pos.x+radius, pos.y, 0			//upper right
			};  
			
			crnrVertsBfr = ByteBuffer.allocateDirect(crnrVerts.length * 4) //4 bytes per float
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			crnrVertsBfr.put(crnrVerts).position(0);			
			
			drawListBfr = ByteBuffer.allocateDirect(indices.length * 2)
			        .order(ByteOrder.nativeOrder()).asShortBuffer();
			drawListBfr.put(indices).position(0);
			
			uvBfr = ByteBuffer.allocateDirect(uvs.length * 4)
			        .order(ByteOrder.nativeOrder()).asFloatBuffer();
			uvBfr.put(uvs).position(0);
			
			mvpMatrixHandle = GLES20.glGetUniformLocation(circleShaderID, "u_MVPMatrix");        
	        positionHandle = GLES20.glGetAttribLocation(circleShaderID, "a_position");
	        texCoordHandle = GLES20.glGetAttribLocation(circleShaderID, "a_texCoord");	
	        
	        //get handles for the uniforms in the fragment shader	       
	        colorHandle = GLES20.glGetUniformLocation(circleShaderID, "circle_color");	        
	        centerHandle = GLES20.glGetUniformLocation(circleShaderID, "circle_center");
			
		}
		
		public void draw(float[] mvpMatrix) {
			
			GLES20.glUseProgram(circleShaderID);			
			
	        GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3]);	        
	        GLES20.glUniform2f(centerHandle, center.x, center.y); 
			
			GLES20.glVertexAttribPointer(positionHandle, posDataSize, GLES20.GL_FLOAT, false,
	        		0, crnrVertsBfr); 	        
	        GLES20.glEnableVertexAttribArray(positionHandle); 
	        
	        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBfr);
	        GLES20.glEnableVertexAttribArray(texCoordHandle);
	        
	        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);	
	        
	        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
	                GLES20.GL_UNSIGNED_SHORT, drawListBfr);
	        
	        GLES20.glDisableVertexAttribArray(positionHandle);
	        GLES20.glDisableVertexAttribArray(texCoordHandle);
			
		}
	}
	
}
