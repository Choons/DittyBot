package com.dittybot.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

public class ObservableScrollView extends ScrollView {
	
	private ScrollViewListener scrollViewListener = null;

	public ObservableScrollView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public ObservableScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}
	
	public ObservableScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setScrollViewListener(ScrollViewListener scrollViewListener) {
        this.scrollViewListener = scrollViewListener;
    }
    
    public void removeScrollViewListener(ScrollViewListener scrollViewListener) {
        this.scrollViewListener = null;
    }
    
    /*
    @Override
    public void fling (int velocityY)
    {
        //this disables fling
    }
	*/

    @Override
    protected void onScrollChanged(int x, int y, int oldx, int oldy) {
        super.onScrollChanged(x, y, oldx, oldy);
        if(scrollViewListener != null) {
            scrollViewListener.onScrollChanged(this, x, y, oldx, oldy);
        }
    }

}
