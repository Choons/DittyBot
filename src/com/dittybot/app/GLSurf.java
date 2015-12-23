package com.dittybot.app;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class GLSurf extends GLSurfaceView {
	
	//private final GLRenderer mRenderer;	
	public MixerGL renderer;

	public GLSurf(Context context, AttributeSet attrs) { //using Attribute attrs allows class to inflate from xml layout
		super(context, attrs);	
		System.out.println("GLSurf created");
		
		// Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);
        
        renderer = new MixerGL(context);        
        setRenderer(renderer);        
        
        //set type of render mode
        //setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
	}	

}
