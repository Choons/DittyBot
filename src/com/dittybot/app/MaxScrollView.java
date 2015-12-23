package com.dittybot.app;

import android.content.Context;
import android.util.AttributeSet;

public class MaxScrollView extends TwoDScrollView {
	
	private MaxScrollListener scrollViewListener = null;
	
	public MaxScrollView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public MaxScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}
	
	public MaxScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setScrollViewListener(MaxScrollListener scrollViewListener) {
        this.scrollViewListener = scrollViewListener;
    }
    
    public void removeScrollViewListener(MaxScrollListener scrollViewListener) {
        this.scrollViewListener = null;
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldx, int oldy) {
        super.onScrollChanged(x, y, oldx, oldy);
        if(scrollViewListener != null) {
            scrollViewListener.onScrollChanged(this, x, y, oldx, oldy);
        }
    }
}
