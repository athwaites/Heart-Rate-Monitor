package com.hrmon.heartratemonitor;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class HeartRateMonitorActivity extends Activity implements SessionManager.Callbacks {
	
	/** The Log Tag. */
	public static final String TAG = "HRMon - App";

	/** Update interval */
	public static final long UPDATE_INTERVAL_MILLISEC = 500;
	
	/** Boolean flag to indicate if Heart Rate Monitor Service is bound to this activity. */
	private boolean mBound;
	
	/** Data handler for the monitor session. */
	private SessionData mSessionData;
	
	/** Manager for the session. */
	private SessionManager mSessionManager;

	/** Handler for timer */
	private Handler mTimer = new Handler();
	
	/** Bind the service. */
	private final ServiceConnection mService = new ServiceConnection()
	{
	    @Override
	    public void onServiceDisconnected(ComponentName name)
	    {
	    	mSessionData = null;
	    	mSessionManager = null;
	    	
	    	mTimer.removeCallbacks(updateTime);
	    }
	        
	    @Override
	    public void onServiceConnected(ComponentName name, IBinder service)
	    {
	    	mSessionData = ((HeartRateMonitorService.LocalBinder)service).getSession();
	    	mSessionManager = ((HeartRateMonitorService.LocalBinder)service).getManager();
	    	
	    	mSessionManager.setCallbacks(HeartRateMonitorActivity.this);
	    	mSessionManager.loadConfiguration(HeartRateMonitorActivity.this);
	        
	        mTimer.postDelayed(updateTime, UPDATE_INTERVAL_MILLISEC);
	    }
	};
	
    /** Called when the activity is created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        setListenerMethods();

        displayStatus();
        displayData();
    }
    
    /** Called when the activity is started. */
    @Override
    protected void onStart()
    {
        mBound = bindService(new Intent(this, HeartRateMonitorService.class), mService, BIND_AUTO_CREATE);
        super.onStart();
    }
    
    /** Called when the activity is stopped. */
    @Override
    protected void onStop()
    {
        if(mSessionManager != null) {
        	mSessionManager.saveConfiguration(HeartRateMonitorActivity.this);
            mSessionManager.setCallbacks(null);
        }
        
        if(mBound) {
            unbindService(mService);
        }
        
        super.onStop();
    }
    
    // SessionManager callback implementations

    /** Called when the service reports a state change. */
    @Override
    public void notifyStateChanged()
    {
    	displayStatus();
    }
    
    /** Called when the service reports new incoming data. */
    @Override
    public void notifyNewData()
    {
    	displayData();
    }
    
    /**
     * Set the listener methods for the UI elements
     */
    private void setListenerMethods()
    {
    	// Heart Button
    	findViewById(R.id.button_heart).setOnClickListener(mClickListener);
    	
    	// Signal Button
    	findViewById(R.id.button_signal).setOnClickListener(mClickListener);
    	
    	// Time Button
    	findViewById(R.id.button_time).setOnClickListener(mClickListener);
    	
    	// Connection Button    	
    	findViewById(R.id.button_connection).setOnClickListener(mClickListener);
    	findViewById(R.id.button_connection).setOnLongClickListener(mLongClickListener);
    }
    
    /**
     * Confirm the user wants to reset the ANT+ connection pairing. 
     */
    private void confirmPairingReset()
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(getString(R.string.Confirm_Pairing_Reset))
    	       .setCancelable(false)
    	       .setPositiveButton(getString(R.string.Positive_Response), new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   mSessionManager.resetPairing();
    	           }
    	       })
    	       .setNegativeButton(getString(R.string.Negative_Response), new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                dialog.cancel();
    	           }
    	       });
    	AlertDialog alert = builder.create();
    	alert.show();
    }
    
    /**
     * Update time elapsed.
     */
    private Runnable updateTime = new Runnable() {
    	public void run() {
    		
    		// Update time every second, if enabled
	    	
        	mTimer.postDelayed(this, UPDATE_INTERVAL_MILLISEC);
	    	
	    	displayTime(mSessionData.getElapsedTime());

    	}
    };
    
    /**
     * Display the Heart Rate
     * @param valHR     value representing the heart rate in the default units
     */
    private void displayHR(int valHR)
    {
    	displayHR(valHR, getString(R.string.Heart_Rate_Units));
    }
    
    /**
     * Display the Heart Rate
     * @param valHR     value representing the heart rate in the units specified
     * @param units     string describing the heart rate units (e.g. "bpm")
     */
    private void displayHR(int valHR, String units)
    {
    	TextView t = ((TextView)findViewById(R.id.text_heart));
    	
   		t.setText(valHR + " " + units);
    }
    
    /**
     * Display the Signal Quality
     * @param RSSI          value representing the Received Signal Strength Indication in the default units
     * @param throughput    value representing the packet throughput in the default units
     */
    private void displaySignal(int RSSI, int throughput)
    {
    	displaySignal(RSSI, getString(R.string.Signal_RSSI_Units), throughput, getString(R.string.Signal_Throughput_Units));
    }
    
    /**
     * Display the Signal Quality
     * @param RSSI          value representing the Received Signal Strength Indication in the units specified
     * @param throughput    value representing the packet throughput in the units specified
     * @param units         string describing the signal quality units (e.g. "%")
     */
    private void displaySignal(int RSSI, String unitsRSSI, int throughput, String unitsThroughput)
    {
    	TextView t = ((TextView)findViewById(R.id.text_signal));
    	
		t.setText(
			RSSI + " " + unitsRSSI + " " +
		    getString(R.string.Signal_Delimiter) + " " +
		    throughput + " " + unitsThroughput);
    }
    
    /**
     * Display the Elapsed Time
     * @param time   value representing the elapsed time in milliseconds
     */
    private void displayTime(long time)
    {
    	int hours;
    	int minutes;
    	int seconds;
    	int milliseconds;

    	milliseconds = (int)(time % 1000);
    	time = (time - milliseconds) / 1000;
		seconds = (int)(time % 60);
		time = (time - seconds) / 60;
		minutes = (int)(time % 60);
		time = (time - minutes) / 60;
		hours = (int)time;
		
		displayTime(hours, minutes, seconds);
    }
    
    /**
     * Display the Elapsed Time
     * @param hours     value representing the hours component of the elapsed time
     * @param minutes   value representing the minutes component of the elapsed time
     * @param seconds   value representing the seconds component of the elapsed time
     */
    private void displayTime(int hours, int minutes, int seconds)
    {
    	TextView t = ((TextView)findViewById(R.id.text_time));
    	NumberFormat time = new DecimalFormat("#00");
    	
		t.setText(
			time.format(hours) +
        	getString(R.string.Time_Delimiter) +
        	time.format(minutes) +
        	getString(R.string.Time_Delimiter) +
        	time.format(seconds));
    }
    
    /**
     * Display the session data
     */
    private void displayData()
    {
    	int curHR = 0;
    	long curElapsedTime = 0;
    	int curRSSI = 0;
    	int curThroughput = 0;
    	
    	if (mSessionData != null) {
    		curHR = mSessionData.getLastHR();
        	curElapsedTime = mSessionData.getElapsedTime();
        	curRSSI = mSessionData.getLastRSSI();
        	curThroughput = mSessionData.getPacketThroughput();
    	}
    	
    	displayHR(curHR);
    	displayTime(curElapsedTime);
    	displaySignal(curRSSI, curThroughput);
    }
    
    /**
     * Display the Connection Status
     */
    private void displayStatus()
    {
    	String str = null;
    	String connState = null;
    	int imgRes = 0;
    	
    	if (mSessionManager != null) {
   			connState = mSessionManager.getConnStateText();
    		
	    	switch (mSessionManager.getState()) {
	    		case CLOSED:
	    			// Disconnected
	    			imgRes = R.drawable.ic_disconnected;
	    			str = getString(R.string.Status_Disconnected);
	    			str = str + "\n\n";
	    			if (connState == null) {
	    				str = str + getString(R.string.Status_Closed);
	    			} else {
	    				str = str + connState;
	    			}
	    			break;
	    		case OFFLINE:
	    			// Disconnected (No sensor)
	    			imgRes = R.drawable.ic_disconnected;
	    			str = getString(R.string.Status_Disconnected);
	    			str = str + "\n\n";
	    			if (connState == null) {
	    			    str = str + getString(R.string.Status_Offline);
	    			} else {
	    				str = str + connState;
	    			}
	    			break;
	    		case PENDING_OPEN:
	    			// Connecting (Enabling ANT+)
	    			imgRes = R.drawable.ic_connecting;
	    			str = getString(R.string.Status_Connecting);
	    			str = str + "\n\n";
	    			str = str + getString(R.string.Status_Opening);
	    			break;
	    		case SEARCHING:
	    			// Connecting (Looking for sensor)
	    			imgRes = R.drawable.ic_connecting;
	    			str = getString(R.string.Status_Connecting);
	    			str = str + "\n\n";
	    			str = str + getString(R.string.Status_Searching);
	    			break;
	    		case TRACKING_STATUS:
	    			// Connected (New data has arrived)
	    		case TRACKING_DATA:
	    			// Connected (Sensor connected)
	    			imgRes = R.drawable.ic_connected;
	    			str = getString(R.string.Status_Connected);
	    			str = str + "\n\n";
	    			str = str + getString(R.string.Status_Opened);
	    			break;
	    		default:
	    			// Unhandled state (Error)
	    			imgRes = R.drawable.ic_disconnected;
	    			str = getString(R.string.Status_Disconnected);
	    			str = str + "\n\n";
	    			if (connState == null) {
	    			    str = str + getString(R.string.Status_Error);
	    			} else {
	    				str = str + connState;
	    			}
	    			break;
	    	}
    	} else {
    		// Disconnected
			imgRes = R.drawable.ic_disconnected;
			str = getString(R.string.Status_Disconnected);
			str = str + "\n\n";
			str = str + getString(R.string.Status_Closed);
    	}
	    	
    	// Update the UI elements with the latest status
    	((ImageView)findViewById(R.id.button_connection)).setImageResource(imgRes);
    	((TextView)findViewById(R.id.status)).setText(str);
    }
    
    /**
     * Called when a view is clicked.
     */
    private OnClickListener mClickListener = new OnClickListener()
    {
		
		@Override
		public void onClick(View v)
		{
			if (mSessionManager == null) {
	    		return;
	    	}
	    	
	    	switch (v.getId()) {
	    	    case R.id.button_connection:
	    	    	// Toggle the ANT+ connection
	    	    	mSessionManager.toggleConnection();
	    		    break;
	    	    case R.id.button_heart:
	    	    	// Show heart rate graph for current session.
	    	    	// Not implemented yet
	    		    break;
	    	    case R.id.button_signal:
	    	    	// Show signal quality graph for current session.
	    	    	// Not implemented yet
	    		    break;
	    	    case R.id.button_time:
	    	    	// Show time option activity (e.g. restart, set time limit, etc.)
	    	    	mSessionManager.toggleSession();
	    		    break;
	    	}
		}
		
	};
	
    /**
     * Called when a view is long-clicked.
     */
	private OnLongClickListener mLongClickListener = new OnLongClickListener()
	{
		@Override
		public boolean onLongClick(View v)
		{
			if (mSessionManager == null) {
	    		return false;
	    	}
	    	
	    	switch (v.getId()) {
	    	case R.id.button_connection:
	    		// Reset the ANT+ pairing
	    		confirmPairingReset();
	    		break;
	    	}
	    	
	    	return true;
		}
	};
    
}

