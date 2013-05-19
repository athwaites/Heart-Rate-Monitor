package com.hrmon.heartratemonitor;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class HeartRateMonitorActivity extends Activity implements ConnectionManager.Callbacks {
	
	/** The Log Tag. */
	public static final String TAG = "HRMon - App";

	/** Update interval */
	public static final long UPDATE_INTERVAL_MILLISEC = 500;
	
	/** Boolean flag to indicate if ANT+ service is bound to this activity. */
	private boolean mBound;
	
	/** Manager for the ANT+ connection. */
	private ConnectionManager mConnectionManager;
	
	/** Manager for the ANT+ connection. */
	private MonitorSession mMonitorSession;
	
	/** Boolean flag to indicate if ANT+ pairing was recently reset. */
	private boolean mPairingReset = false;
	
	/** Pair to any device. */
	static final short WILDCARD = 0;
	   
	/** The default proximity search bin. */
	private static final byte DEFAULT_BIN = 7;
	
	/** The default event buffering buffer threshold. */
	private static final short DEFAULT_BUFFER_THRESHOLD = 0;
	   
	/** Shared preferences data filename. */
	public static final String PREFS_NAME = "HRMonPrefs";
	
	/** Heart Rate Monitor state enumeration */
	private enum eHRMState {
		CS_RESET,
		CS_CLOSED,
		CS_OFFLINE,
		CS_OPENING,
		CS_SEARCHING,
		CS_OPENED,
		CS_ERROR
	}
	
	/** Heart Rate Monitor FSM */
	private eHRMState mHRMState = eHRMState.CS_CLOSED;
	
	/** Handler for timer */
	private Handler mTimer = new Handler();
	
	/** Bind the service. */
	private final ServiceConnection mConnection = new ServiceConnection()
	{
	    @Override
	    public void onServiceDisconnected(ComponentName name)
	    {
	    	mConnectionManager.setCallbacks(null);
	    	mConnectionManager = null;
	    	mMonitorSession = null;
	    }
	        
	    @Override
	    public void onServiceConnected(ComponentName name, IBinder service)
	    {
	    	mConnectionManager = ((HeartRateMonitorService.LocalBinder)service).getManager();
	    	mConnectionManager.setCallbacks(HeartRateMonitorActivity.this);
	    	mMonitorSession = ((HeartRateMonitorService.LocalBinder)service).getSession();
	        loadConfiguration();
	        notifyAntStateChanged();
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
        mBound = bindService(new Intent(this, HeartRateMonitorService.class), mConnection, BIND_AUTO_CREATE);
        super.onStart();
    }
    
    /** Called when the activity is stopped. */
    @Override
    protected void onStop()
    {
        if(mConnectionManager != null) {
            saveState();
            mConnectionManager.setCallbacks(null);
        }
        if(mBound) {
            unbindService(mConnection);
        }
        super.onStop();
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
    	
    	if (mMonitorSession != null) {
    		curHR = mMonitorSession.getLastHR();
        	curElapsedTime = mMonitorSession.getElapsedTime();
        	curRSSI = mMonitorSession.getLastRSSI();
        	curThroughput = mMonitorSession.getPacketThroughput();
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
    	String antState = null;
    	int imgRes = 0;
    	
    	if (mConnectionManager != null) {
    		if (mConnectionManager.checkAntState()) {
    			antState = mConnectionManager.getAntStateText();
    		}
    	}
    	
    	switch (mHRMState) {
    		case CS_RESET:
    			imgRes = R.drawable.ic_disconnected;
    			str = getString(R.string.Status_Disconnected);
    			str = str + "\n\n";
    			if (antState == null) {
    				str = str + getString(R.string.Status_Pairing_Reset);
    			} else {
    				str = str + antState;
    			}
    			break;
    		case CS_CLOSED:
    			imgRes = R.drawable.ic_disconnected;
    			str = getString(R.string.Status_Disconnected);
    			str = str + "\n\n";
    			if (antState == null) {
    				str = str + getString(R.string.Status_Closed);
    			} else {
    				str = str + antState;
    			}
    			break;
    		case CS_OFFLINE:
    			imgRes = R.drawable.ic_disconnected;
    			str = getString(R.string.Status_Disconnected);
    			str = str + "\n\n";
    			if (antState == null) {
    			    str = str + getString(R.string.Status_Offline);
    			} else {
    				str = str + antState;
    			}
    			break;
    		case CS_OPENING:
    			imgRes = R.drawable.ic_connecting;
    			str = getString(R.string.Status_Connecting);
    			str = str + "\n\n";
    			str = str + getString(R.string.Status_Opening);
    			break;
    		case CS_SEARCHING:
    			imgRes = R.drawable.ic_connecting;
    			str = getString(R.string.Status_Connecting);
    			str = str + "\n\n";
    			str = str + getString(R.string.Status_Searching);
    			break;
    		case CS_OPENED:
    			imgRes = R.drawable.ic_connected;
    			str = getString(R.string.Status_Connected);
    			str = str + "\n\n";
    			str = str + getString(R.string.Status_Opened);
    			break;
    		case CS_ERROR:
    		default:
    			imgRes = R.drawable.ic_disconnected;
    			str = getString(R.string.Status_Disconnected);
    			str = str + "\n\n";
    			if (antState == null) {
    			    str = str + getString(R.string.Status_Error);
    			} else {
    				str = str + antState;
    			}
    			break;
    	}
    	
    	// Update the UI elements with the latest status
    	((ImageView)findViewById(R.id.button_connection)).setImageResource(imgRes);
    	((TextView)findViewById(R.id.status)).setText(str);
    }
    
    /**
     * Store application persistent data.
     */
    private void saveState()
    {
       // Save current Channel Id in preferences
       // We need an Editor object to make changes
       SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
       SharedPreferences.Editor editor = settings.edit();
       editor.putInt("DeviceNumberHRM", mConnectionManager.getDeviceNumberHRM());
       editor.putInt("ProximityThreshold", mConnectionManager.getProximityThreshold());
       editor.putInt("BufferThreshold", mConnectionManager.getBufferThreshold());
       editor.commit();
    }
    
    /**
     * Retrieve application persistent data.
     */
    private void loadConfiguration()
    {
       // Restore preferences
       SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
       mConnectionManager.setDeviceNumberHRM((short) settings.getInt("DeviceNumberHRM", WILDCARD));
       mConnectionManager.setProximityThreshold((byte) settings.getInt("ProximityThreshold", DEFAULT_BIN));
       mConnectionManager.setBufferThreshold((short) settings.getInt("BufferThreshold", DEFAULT_BUFFER_THRESHOLD));       
    }
    
    /**
     * Delete application persistent data.
     */
    private void deleteConfiguration()
    {
    	// Clear application persistent data
    	SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
    	editor.clear();
    	editor.commit();
    }
    
    /**
     * Reset application persistent data to default.
     */
    private void resetConfiguration()
    {
    	deleteConfiguration();
    	loadConfiguration();
    }
    
    /**
     * Reset the ANT+ connection pairing.
     */
    private void resetPairing()
    {
    	mPairingReset = true;
    	
    	disconnectSensor();
    	
    	mConnectionManager.setDeviceNumberHRM(WILDCARD);
        mConnectionManager.setProximityThreshold(DEFAULT_BIN);
        mConnectionManager.setBufferThreshold(DEFAULT_BUFFER_THRESHOLD);
        
        updateState();
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
    	                HeartRateMonitorActivity.this.resetPairing();
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
     * Toggle the ANT+ connection [connect/disconnect]
     */
    private void toggleConnection()
    {
    	if ((mHRMState == eHRMState.CS_CLOSED) || (mHRMState == eHRMState.CS_RESET)) {
    		// Connect
    		connectSensor();
    	} else {
    		// Disconnect
    		disconnectSensor();
    	}
    }
    
    /**
     * Establish ANT+ connection with HR Sensor.
     */
    private void connectSensor()
    {
		// Enable ANT+
    	if (!mConnectionManager.isEnabled()) {
    	    mConnectionManager.doEnable();
    	}
    	
    	// Open HRM channel
    	// (reset on all attempts; otherwise would call 
    	// "openChannel(HRM_CHANNEL, false)" and exclude "requestReset()" 
        if (!mConnectionManager.isChannelOpen(ConnectionManager.HRM_CHANNEL)) {
            Log.d(TAG, "onClick (HRM): Open channel");
            // Defer opening the channel until an ANT_RESET has been received
            mConnectionManager.openChannel(ConnectionManager.HRM_CHANNEL, true);
            mConnectionManager.requestReset();
        }
    }
    
    /**
     * Terminate ANT+ connection with HR Sensor.
     */
    private void disconnectSensor()
    {
    	
		// Close HRM channel
    	if (mConnectionManager.isChannelOpen(ConnectionManager.HRM_CHANNEL)) {
            // Close channel
            Log.d(TAG, "onClick (HRM): Close channel");
            mConnectionManager.closeChannel(ConnectionManager.HRM_CHANNEL);
        }
    	
    	// Disable ANT+
    	if (mConnectionManager.isEnabled()) {
    	    mConnectionManager.doDisable();
    	}
    }
    
    /**
     * Toggles the session.
     */
    private void toggleSession()
    {
    	if (mMonitorSession.isStarted()) {
    		stopSession();
    	} else {
    		if (mHRMState == eHRMState.CS_OPENED) {
    			startSession();
    		}
    	}
    }
    
    /**
     * Starts the session.
     */
    private void startSession()
    {
    	mTimer.postDelayed(updateTime, UPDATE_INTERVAL_MILLISEC);
    	mMonitorSession.clear();
    	mMonitorSession.start();
    }
    
    /**
     * Stops the session.
     */
    private void stopSession()
    {
    	mTimer.removeCallbacks(updateTime);
    	mMonitorSession.stop();
    }
    
    /**
     * Update HR data.
     */
    private void updateHR()
    {
    	mMonitorSession.addHR(mConnectionManager.getBPM());
    }
    
    /**
     * Update time elapsed.
     */
    private Runnable updateTime = new Runnable() {
    	public void run() {
    		
    		// Update time every second, if enabled
	    	
        	mTimer.postDelayed(this, UPDATE_INTERVAL_MILLISEC);
	    	
	    	displayTime(mMonitorSession.getElapsedTime());

    	}
    };
    
    /**
     * Update signal data.
     */
    private void updateSignal()
    {
    	mMonitorSession.addPacketsReceived(mConnectionManager.getPacketsReceived());
    	mMonitorSession.addPacketsDropped(mConnectionManager.getPacketsDropped());
    	
    	Log.d(TAG, "PacketsReceived: " + mMonitorSession.getPacketsReceived());
    	Log.d(TAG, "PacketsDropped: " + mMonitorSession.getPacketsDropped());
    	
    	mMonitorSession.addRSSI(mConnectionManager.getRSSI());
    }
    
    /**
     * Update data for displaying on the UI.
     */
    private void updateData()
    {

    	if (mConnectionManager == null) {
    		mMonitorSession.clear();
    	} else {
    		if (mHRMState == eHRMState.CS_OPENED) {
    	        updateHR();
    	        updateSignal();
    		}
    	}
    	
    	displayData();
    }
    
    /**
     * Update FSM state based on connection manager response.
     */
    private void updateState()
    {
    	if (mConnectionManager == null) {
    		mHRMState = eHRMState.CS_CLOSED;
    	} else {
	    	switch (mConnectionManager.getHrmState()) {
	        case CLOSED:
	        	// Disconnected
	        	if (mPairingReset) {
	        		mHRMState = eHRMState.CS_RESET;
	        	} else {
	        		mHRMState = eHRMState.CS_CLOSED;
	        	}
	            break;
	        case OFFLINE:
	        	// Disconnected (No sensor)
	        	mHRMState = eHRMState.CS_OFFLINE;
	            break;
	        case SEARCHING:
	        	// Connecting (Looking for sensor)
	        	mHRMState = eHRMState.CS_SEARCHING;
	            break;
	        case PENDING_OPEN:
	        	// Connecting (Enabling ANT+)
	        	mPairingReset = false;
	        	mHRMState = eHRMState.CS_OPENING;
	            break;
	        case TRACKING_STATUS:
	            // Connected (New data has arrived)
	        	mHRMState = eHRMState.CS_OPENED;
	        case TRACKING_DATA:
	        	// Connected (Sensor connected)
	        	mHRMState = eHRMState.CS_OPENED;
	            break;
	        default:
	        	// Unhandled state (Error)
	        	// Should we force the disconnect here anyway? (call "disconnectSession()")
	        	mHRMState = eHRMState.CS_ERROR;
	            break;
	    	}
    	}
    	
    	displayStatus();
    }
        
    // ConnectionManager callback implementations

    @Override
    public void errorCallback()
    {
    	// Update state with error
    	updateState();
    }

    @Override
    public void notifyAntStateChanged()
    {
    	// Update state based on new ANT+ status
    	updateState();
    }
    
    @Override
    public void notifyChannelStateChanged(byte channel)
    {
    	// Update state based on new channel status
    	// Don't need to worry about channel; only using HRM
    	updateState();
    }
    
    @Override
    public void notifyChannelDataChanged(byte channel)
    {
    	// Update data with new stream from channel
    	// Don't need to worry about channel; only using HRM
    	updateData();
    }
    
    /**
     * Called when a view is clicked.
     */
    private OnClickListener mClickListener = new OnClickListener()
    {
		
		@Override
		public void onClick(View v)
		{
			if (mConnectionManager == null) {
	    		return;
	    	}
	    	
	    	switch (v.getId()) {
	    	    case R.id.button_connection:
	    	    	// Toggle the ANT+ connection
	    	    	toggleConnection();
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
	    	    	toggleSession();
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
			if (mConnectionManager == null) {
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

