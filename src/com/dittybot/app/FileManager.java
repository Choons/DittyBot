package com.dittybot.app;

/**
 * Full-on file manager that gives user access to phone SD card storage.
 * Set up so launched when user downloads a file attachment from email
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FileManager extends Activity {
	
	GlobalVars gv;
	
	private Dialog splashScreen;
	
	private GridView gridView;
	private List<FileItem> fileItems; //list of files in folder user is viewing
	
	private int extStorIndex = -1; //to display single breadcrumb for external storage. # steps to external storage in path returned by getExternalStorageDirectory()
	private String currDirPath = null; //path of which folder have open/viewing contents of at moment
	private String clipbrdPath = null; //path of file in "clipboard" to be downloaded, moved, copied etc.
	private String action = null; //stores either the "move" or "copy" state user selected
	
	private LinearLayout mainMenu;
	private LinearLayout dirUp;
	private LinearLayout newDir;
	private LinearLayout saveBtn;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	System.out.println("FileManager onCreate()");
    	super.onCreate(savedInstanceState);
    	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	setContentView(R.layout.filemgr);
    	
    	gv = ((GlobalVars)getApplicationContext());                
        
        gridView = (GridView) findViewById(R.id.filemgrGV);     
        fileItems = new ArrayList<FileItem>();
        
        //side menu "buttons"
        mainMenu = (LinearLayout) findViewById(R.id.fmgrmainmenuLL);
        mainMenu.setOnClickListener(new OnClickListener(){ 
			@Override
			public void onClick(View v) {				
				mainMenu();				
			}    		
    	});		
        dirUp = (LinearLayout) findViewById(R.id.fmgrdirupLL);
        dirUp.setOnClickListener(new OnClickListener(){ 
			@Override
			public void onClick(View v) {				
				dirUp();
			}    		
    	});	
        newDir = (LinearLayout) findViewById(R.id.fmgrnewfLL);
        newDir.setOnClickListener(new OnClickListener(){ 
			@Override
			public void onClick(View v) {				
				action = "new";
				fileSave();
			}    		
    	});	
        
        saveBtn = (LinearLayout) findViewById(R.id.fmgrsaveLL); //listener set in FileExplorer 
        
        //figure out how many levels of directory hierarchy are included in returned external storage path
        String[] path_steps = gv.extStorPath.split("/"); 
    	extStorIndex = path_steps.length - 1; //the index value of ext. storage name in split path
    	
    	Intent intent = getIntent();    	
    	String scheme = intent.getScheme();
    	
    	if (scheme == null) { //true when this activity is launched from within app             	
        	saveBtn.setVisibility(View.GONE); //should be already but just in case       	
        	fileExplorer(gv.extStorPath); //load file explorer at top of external storage 
    	}
    	else if (scheme.contentEquals("file")) { //true when user selects 'Download' for an email attachment        	
    		
        	clipbrdPath = intent.getData().getPath(); //Android puts file in /download folder in background already        	
        	action = "move"; //because actually moving from download folder
        	saveBtn.setVisibility(View.VISIBLE);        	
        	splash(); //app launched from outside so show File Manager splash screen with logo
        	// *splash() runs splashDelay() which then starts fileExplorer()
        	        	
    	}
    	else { //then something other than a file ref is being passed in
    		     		 
    		 badTypeExit();
    	}     	   	
	}
	
	private void badTypeExit() { //alerts user when launched by a non-file intent.scheme and exits
		final Dialog dialog = new Dialog(this);     	
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	dialog.setContentView(R.layout.alertdlg);
    	
    	TextView title = (TextView) dialog.findViewById(R.id.alertTitleTV);
    	title.setText("File Manager Error");
    	
    	TextView msg = (TextView) dialog.findViewById(R.id.alertMsgTV);    	
    	msg.setText("File Manager cannot complete this operation.\nIf you are trying to save an attachment" +
    			" from email, be sure to choose the Download option instead of the Preview option.");
    	
    	Button okBtn = (Button) dialog.findViewById(R.id.alertOKbtn);
    	
    	okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
				System.exit(0); //should close the app completely as this is only activity that should be on stack
			}
		});    	
    	
    	dialog.show();
	}
	
	private void mainMenu() { //called when user clicks Main in side menu
		Intent intent = new Intent(this, MainMenu.class); 
		startActivity(intent);
	}
	
	private void dirUp() { //called when user clicks Up in side menu
		if (currDirPath.contentEquals(gv.extStorPath)) return; //already at top of dir tree
		File currDir = new File(currDirPath);
		String upPath = currDir.getParent();		
		fileExplorer(upPath);
	}
	
	//TODO these file ops should be in an AsyncTask 
	private void fileExplorer(String dirpath) { //path to directory to be drilled into
		System.out.println("fileExplorer() " + dirpath);		
		
		fileItems.clear(); //remove any prior items	
		
		TextView emptydirTV = (TextView) findViewById(R.id.filemgrTV);
		emptydirTV.setVisibility(View.GONE); //hide any previous notifications
		
		//get list of what is in directory. Create FileItems with file type icons
		final File fdir = new File(dirpath);		
		if(fdir.exists() && fdir.isDirectory()) {	

		currDirPath = dirpath;
			
		saveBtn.setOnClickListener(new OnClickListener(){ //wonky to add on each call? convenient to get which folder currently in
			@Override
			public void onClick(View v) {				
				System.out.println("saveBtn click: " + clipbrdPath + " to " + fdir.getPath());
				fileSave();
			}    		
    	});		
			
		//fill out the breadcrumb line dynamically
		LinearLayout breadcrumbLL = (LinearLayout) findViewById(R.id.fmgrBreadCrLL);
		if (breadcrumbLL.getChildCount() > 0) { //make sure any previous crumbs removed
			breadcrumbLL.removeAllViews();
		}
			
		int baseColor = getResources().getColor(R.color.deepred); //initial color of crumbs
		
		String[] dirnames = dirpath.split("/");	
		int numCrumbs = dirnames.length - extStorIndex; //total # of crumbs. used to size them 
		int w_px = (gv.screenWidth-56)/numCrumbs; //TODO that 56 value needs to be calcd off the dp values in the layout
		String runningPath = "";
		int counter = 0; //counts crumbs. used to shift hue of each
		for (int i=0; i < dirnames.length; i++) { 				
			//rebuild path to each crumb level
			//first build the path up to the sd card (or whatever external storage name)
			if (i > 0) runningPath += "/";  
			runningPath += dirnames[i];						
			
			if (i >= extStorIndex) { //at ext. storage path, create crumbs
				final Breadcrumb bc = new Breadcrumb(this, makeColor(baseColor, counter), 30, w_px, dirnames[i], runningPath);
				counter++;
				bc.setOnClickListener(new OnClickListener(){
					@Override
					public void onClick(View v) {				
						System.out.println("click: " + bc.dirPath);
						fileExplorer(bc.dirPath); //load the grid for that crumb directory
					}    		
		    	});				
				
				breadcrumbLL.addView(bc);	
			}							
		}			
			
		//set up the files gridview
		File[] files = fdir.listFiles();			
		if (files.length > 0) {				
			for (int i=0; i < files.length; i++) {
				
				Drawable icon;
				boolean isDir = false; //is it a directory?
				String path = files[i].getPath();					
				String filename = files[i].getName();										
				
				//check if a directory or a file & assign icon
				if (files[i].isDirectory()) {
					icon = this.getResources().getDrawable(R.drawable.folder256);
					isDir = true;
				}
				else if (files[i].isFile()) {
					icon = this.getResources().getDrawable(R.drawable.file256);
					//TODO expand for different file types
				}
				else {
					System.out.println("fileExplorer() error: object is neither directory nor file");
					//TODO not sure how could happen? handle error						
					
					return;
				}
				
				FileItem file = new FileItem(icon, isDir, path, filename);				
				fileItems.add(file);
			}				
			
			gridView.setAdapter(new GridViewAdapter(this, fileItems));
			
			gridView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {				
					
					LinearLayout grid_itemLL = (LinearLayout) view;
					TextView grid_itemTV = (TextView) grid_itemLL.getChildAt(1);
					String filename = (String) grid_itemTV.getText();						
					
					//use filename to cross ref in the fileItems list						
					int index = -1;
					for (int i=0; i < fileItems.size(); i++) {
						if(fileItems.get(i).filename.contentEquals(filename)) {
							index = i;
							break;
						}
					}
					
					if (fileItems.get(index).isDir) { //it's a directory, call this recursively again with the path
						String drillpath = fileItems.get(index).path;
						fileExplorer(drillpath); //drill down in directory. recursive call back 
					}
					else { //it's a file present the move, copy, rename, delete options						
						clipbrdPath = fileItems.get(index).path;
						fileOptions();
					}						
				}					
			});
			
			gridView.setOnItemLongClickListener(new OnItemLongClickListener() { //allows way to select folders
	            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
	                
	            	LinearLayout grid_itemLL = (LinearLayout) view;
					TextView grid_itemTV = (TextView) grid_itemLL.getChildAt(1);
					String filename = (String) grid_itemTV.getText();						
					
					//use filename to cross ref in the fileItems list						
					int index = -1;
					for (int i=0; i < fileItems.size(); i++) {
						if(fileItems.get(i).filename.contentEquals(filename)) {
							index = i;
							break;
						}
					}
					
					clipbrdPath = fileItems.get(index).path;
					fileOptions();

	                return true;
	            }
	        }); 
		}
		else {
				System.out.println("dir is empty");
				//TODO skip all the adapter stuff and inform user dir is empty
				
				emptydirTV.setText("Empty Folder");
				emptydirTV.setVisibility(View.VISIBLE);
				//TODO make GONE again when nav out
			}
		}				
	}
	
	private void fileOptions() { //lets user select to move, copy, rename, or delete a file
		
		final Dialog dialog = new Dialog(this); 
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.fileops);
		
		Button cancelBtn = (Button) dialog.findViewById(R.id.fileopsCncl);
		
		final LinearLayout moveLL = (LinearLayout) dialog.findViewById(R.id.filemoveLL);
		moveLL.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {					
				switch (event.getAction()){					
				case MotionEvent.ACTION_DOWN:						
					moveLL.setBackgroundColor(0xFF00FF00); //flash green as feedback					
				}					
				return false;
			}			
		});	
		
		moveLL.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
				saveBtn.setVisibility(View.VISIBLE);
				action = "move";
				saveInfo();
			}			
		});	
		//=============================================
		
		final LinearLayout copyLL = (LinearLayout) dialog.findViewById(R.id.filecopyLL);
		copyLL.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {		
				switch (event.getAction()){					
				case MotionEvent.ACTION_DOWN:						
					copyLL.setBackgroundColor(0xFF00FF00);						
				}					
				return false;
			}			
		});	
		
		copyLL.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				saveBtn.setVisibility(View.VISIBLE);
				action = "copy";
				saveInfo();
			}			
		});	
		//=============================================
		
		final LinearLayout renLL = (LinearLayout) dialog.findViewById(R.id.filerenLL);
		renLL.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {		
				switch (event.getAction()){					
				case MotionEvent.ACTION_DOWN:						
					renLL.setBackgroundColor(0xFF00FF00);						
				}					
				return false;
			}			
		});	
		
		renLL.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {			
				dialog.dismiss();
				action = "rename"; //rename is just like a move, but to same folder
				fileSave();
			}			
		});	
		//=============================================
		
		final LinearLayout delLL = (LinearLayout) dialog.findViewById(R.id.filedelLL);
		delLL.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {		
				switch (event.getAction()){					
				case MotionEvent.ACTION_DOWN:						
					delLL.setBackgroundColor(0xFF00FF00);						
				}					
				return false;
			}			
		});	
		
		delLL.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				fileDelete();
				dialog.dismiss();
			}			
		});	
		//=============================================
		
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
			}			
		});	
				
		dialog.show();
	}
	
	private void fileSave() { //ops on file at clipbrdPath to currDirPath according to action var
		final Dialog dialog = new Dialog(this); 
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.savefile);
		
		ImageView icon = (ImageView) dialog.findViewById(R.id.saveFileIV);
		TextView title = (TextView) dialog.findViewById(R.id.saveFileTitle);
		TextView text = (TextView) dialog.findViewById(R.id.saveFileTV1);
		if (action.contentEquals("new")) {
			icon.setImageResource(R.drawable.foldermove256);
			title.setText("Create New Folder In:");
			text.setText(currDirPath);
		}
		else if (action.contentEquals("move")) {
			icon.setImageResource(R.drawable.foldermove256);
			title.setText("Move File To:");
			text.setText(currDirPath);
		}
		else if (action.contentEquals("copy")) {
			icon.setImageResource(R.drawable.foldercopy256);
			title.setText("Copy File To:");
			text.setText(currDirPath);
		}
		else if (action.contentEquals("rename")) {
			icon.setImageResource(R.drawable.folderren256);
			title.setText("Rename File");
			text.setText("");
		}		
		
		final EditText nameBox = (EditText) dialog.findViewById(R.id.saveFileET);
		if (action.contentEquals("new")) { //for a new folder
			nameBox.setText("New Folder");
		}
		else {
			String[] elements = clipbrdPath.split("/");
			String origFname = elements[elements.length-1];
			nameBox.setText(origFname);	//put the original file name in the edittext
		}			
		
		Button okBtn = (Button) dialog.findViewById(R.id.saveFileOK); //says Save actually
		Button cancelBtn = (Button) dialog.findViewById(R.id.saveFileCancel);
		
		okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				//first check if can write a file to selected folder
				File currDir = new File(currDirPath);				
				if (currDir.canWrite()) {					
					//build full path for new file
					String newPath = currDirPath + "/" + nameBox.getText().toString();
					final File newFile = new File(newPath);
					
					//check not duplicating file name
					if (newFile.exists()) { //then a file already has that name in this folder
						ovrwrtAlert(newPath, new PromptRunnable() { //pop up alerting user, ask if overwrite								
							public void run() {
								
								String response = this.getValue(); //get user input 
												
								if(response.contentEquals("yes")) { //user wants to overwrite existing file									
									if (action.contentEquals("new")) { //action is to create new directory
										if (!newFile.mkdir()) {
											System.out.println("fileSave() mkdir failed");
											//TODO alert user something went wrong
										}										
									}
									else { //action is move, copy, or rename
										File origFile = new File(clipbrdPath);
										fileOps(origFile, newFile);	
									}							
																		
									saveBtn.setVisibility(View.GONE); //not necessary on rename or delete, but not a problem
									dialog.dismiss();
									fileExplorer(currDirPath); //reload file explorer at same spot so new file is shown
								}
								if(response.contentEquals("no")) {
									System.out.println("user selected NO don't overwrite");
									//just exits back to the Save As dialog so user can change name or cancel out
								}
							}			
						});
					}
					else { //file name is unique in this folder so proceed 
						if (action.contentEquals("new")) { //action is to create new directory
							if (!newFile.mkdir()) {
								System.out.println("fileSave() mkdir failed");
								//TODO alert user something went wrong
							}
						}
						else { //action is move, copy, or rename
							File origFile = new File(clipbrdPath);
							fileOps(origFile, newFile);	
						}
						
						saveBtn.setVisibility(View.GONE); //not necessary on rename or delete, but not a problem
						dialog.dismiss();
						fileExplorer(currDirPath); //reload file explorer at same spot so new file is shown
					}					 
				}
				else { //destination folder is write-protected					
					dialog.dismiss();
					//let user know the directory is write-protected
					writeProtected();
				}				
			}
		});
		
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		
		dialog.show();
	}
	
	private void fileOps(File origFile, File newFile) {
		if (action.contentEquals("move") || action.contentEquals("rename")) { //rename is just a file move to same folder
			if (origFile.renameTo(newFile)) { //create new file & check for success at same time
				System.out.println("file renameTo was successful");										
			}
			else { //the renameTo operation failed
				System.out.println("file renameTo failed");	
				//TODO alert user could not perform file operation
				//may be a system file or some such
			}
		}
		else if (action.contentEquals("copy")) {
			try {
				FileUtilities.copy(origFile, newFile);
			} catch (IOException e) {									
				e.printStackTrace();
				System.out.println("FileUtilities.copy failed");
				//TODO alert user could not perform file operation
				//may be a system file or some such
			}
		}
		else {
			System.out.println("fileSave() unrecognized action");
			//TODO this should never happen
		}
	}
	
	private void fileDelete() {
		
		final File file = new File(clipbrdPath);		
		
		final Dialog dialog = new Dialog(this); 
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.confirm);
		
		ImageView iv = (ImageView) dialog.findViewById(R.id.confmIV);
		iv.setImageResource(R.drawable.warning256);
		
		TextView title = (TextView) dialog.findViewById(R.id.confmTitle);
		title.setText("Confirm File Delete");		
		
		TextView msg = (TextView) dialog.findViewById(R.id.confmTV);
		msg.setText("Are you sure you want to delete this file?\n\n" + file.getName());
		
		Button yesBtn = (Button) dialog.findViewById(R.id.confmYesBtn);
		Button noBtn = (Button) dialog.findViewById(R.id.confmNoBtn);
		
		yesBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
				
				//File file = new File(fileItems.get(index).path);
				if (file.exists()) {
					if (file.delete()) { //try to delete the file, catch if fails
						fileExplorer(file.getParent());
					}
					else {
						System.out.println("confirmDel() could not delete the file");
						//TODO may need to check permissions first
					}
					
				}
				else {
					System.out.println("confirmDel() file does not exist");
					//TODO not sure how, maybe bad SD card. inform user
				}
			}			
		});	
		
		noBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();
			}			
		});	
		
		dialog.show();
	}

	private void ovrwrtAlert(final String filePath, final PromptRunnable postrun) {
		//filePath isn't used, but could be for a more detailed alert message
		final Dialog dialog = new Dialog(this);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.confirm);
		
		ImageView iv = (ImageView) dialog.findViewById(R.id.confmIV);
		iv.setImageResource(R.drawable.warning256);
		TextView title = (TextView) dialog.findViewById(R.id.confmTitle);
		title.setText("Overwrite Alert");
		
		TextView msg = (TextView) dialog.findViewById(R.id.confmTV);
		msg.setText("A file already exsists with that name. Press Yes below if you" +
				" would like to overwrite the existing file. Press No to go back and change the file name.");
		
		Button yesBtn = (Button) dialog.findViewById(R.id.confmYesBtn);
		Button noBtn = (Button) dialog.findViewById(R.id.confmNoBtn);
		
		yesBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				postrun.setValue("yes");
				postrun.run();
			}
		
		});
		
		noBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				postrun.setValue("no");
				postrun.run();
			}
		
		});
		
		dialog.show();
	}
	
	private void writeProtected() { //popup to let user know file is write-protected
		
	}
	
	private void splash() {
		splashScreen = new Dialog(this, android.R.style.Theme); //theme in constructor makes full screen
		splashScreen.getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		splashScreen.requestWindowFeature(Window.FEATURE_NO_TITLE);
		splashScreen.setContentView(R.layout.filemgrsplash);
		
		splashScreen.show();		
		
		splashDelay();
	}
	
	private void splashClose() {
    	splashScreen.dismiss();
    }
	
	private void splashDelay() { //show a splash briefly when launched by a file download from outside app		
		final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            splashClose();
            saveInfo();
            fileExplorer(Environment.getExternalStorageDirectory().toString()); //load at top of external storage
          }
        }, 2500); //show splash 2.5 seconds
	}
	
	private void saveInfo() { //dialog to tell user to navigate & save file to there
			
		final Dialog dialog = new Dialog(this);     	
    	dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    	dialog.setContentView(R.layout.alertdlg);
    	
    	ImageView iv = (ImageView) dialog.findViewById(R.id.alertIV);
    	iv.setImageResource(R.drawable.info_red256);
    	
    	TextView title = (TextView) dialog.findViewById(R.id.alertTitleTV);
    	title.setText("File Save Info");
    	
    	TextView msg = (TextView) dialog.findViewById(R.id.alertMsgTV);    	
    	msg.setText("Navigate to desired folder, then tap the Save icon at lower right" +
    			" to save the file.");
    	
    	Button okBtn = (Button) dialog.findViewById(R.id.alertOKbtn);
    	
    	okBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {				
				dialog.dismiss();										
			}
		});
    	
    	dialog.show();
	}
	
	private int makeColor(int color, int index) { //little utility function to assign colors to crumb graphics
		int newColor = -1;
		
		float[] hsv = new float[3];
		Color.colorToHSV(color, hsv);		
		hsv[0] += 40*index; //shift the hue on each successive crumb
		
		newColor = Color.HSVToColor(hsv);
		
		return newColor;
	}
	

	
	//===============================================================================================
	
	public class GridViewAdapter extends BaseAdapter {
	    private Context mContext;
	    private List<FileItem> fItems;
	    
	    public GridViewAdapter(Context context, List<FileItem> items) {
	        mContext = context;
	        fItems = items;
	    }
	 
	    @Override
	    public int getCount() {
	        return fItems.size();
	    }
	 
	    @Override
	    public Object getItem(int position) {
	        return fItems.get(position);
	    }
	 
	    @Override
	    public long getItemId(int position) {
	        return position;
	    }
	 
	    @Override
	    public View getView(int position, View convertView, ViewGroup parent) {
	        ViewHolder viewHolder; 
	        
	        if(convertView == null) {
	            // inflate the GridView item layout
	            LayoutInflater inflater = LayoutInflater.from(mContext);
	            convertView = inflater.inflate(R.layout.grid_item, parent, false);
	            
	            // initialize the view holder
	            viewHolder = new ViewHolder();
	            viewHolder.iconIV = (ImageView) convertView.findViewById(R.id.grid_itemIV);
	            viewHolder.fnameTV = (TextView) convertView.findViewById(R.id.grid_itemTV);
	            convertView.setTag(viewHolder);
	        } else {
	            // recycle the already inflated view 
	            viewHolder = (ViewHolder) convertView.getTag();
	        }
	        
	        // update the item view
	        FileItem item = fItems.get(position);
	        viewHolder.iconIV.setImageDrawable(item.icon);
	        viewHolder.fnameTV.setText(item.filename);
	        viewHolder.fnameTV.setLines(3); //added due to gridview bug where it gets wonky after scrolling a bit
	              
	        return convertView;
	    }
	    
	    /**
	     * The view holder design pattern prevents using findViewById()
	     * repeatedly in the getView() method of the adapter to give smoother scrolling
	     * 
	     * @see http://developer.android.com/training/improving-layouts/smooth-scrolling.html#ViewHolder
	     */
	    private class ViewHolder {
	        ImageView iconIV;
	        TextView fnameTV;	       
	    }
	}
	
	public class FileItem { //little class to keep a running list of files in the open folder user is viewing
	    public final Drawable icon; 
	    public final boolean isDir;
	    public final String path;
	    public final String filename;        
	    
	    public FileItem(Drawable icon, boolean isDir, String path, String fname) {
	        this.icon = icon;
	        this.isDir = isDir;
	        this.path = path;
	        this.filename = fname;
	    }
	}
	
    class PromptRunnable implements Runnable { //nifty way of getting a return value from a dialog
    	private String v;
    	void setValue(String inV) {
    		this.v = inV;
    	}
    	String getValue() {
    		return this.v;
    	}
    	public void run() {
    		this.run();
    	}
    	
    	/**
    	 * cool little hack that makes it seems like in a function you can say getValue() from a dialog
    	 * and resume right on to next lines of code in the function. What really happens is the whole block
    	 * of code is sent into the dialog & gets executed when user provides input, but from a code readability
    	 * standpoint all the code is defined in the function that calls the dialog. Much more intuitive setup. 
    	 * 
    	 */
    }
	
	//===============================================================================================
    @Override
	public void onResume() {
		System.out.println("FileManager onResume()");		
		super.onResume();			
		
	}
    
    @Override
    public void onPause() {
		System.out.println("FileManager onPause()");
		super.onPause();
		
		//closeSplash();
    }
	
    @Override
    public void onRestart() {
    	System.out.println("FileManager onRestart()");    	
    	super.onRestart();  
    	
    	//closeSplash();
    }
    
    @Override
    public void onStop() {
    	System.out.println("FileManager onStop()");    	
    	super.onStop();  
    	
    	//closeSplash();    	
    }	
    
    @Override
	public void onDestroy() {
    	System.out.println("FileManager onDestroy()"); //may not be called		
    	super.onDestroy();   
    	
    	//closeSplash();    	
    }

}
