package com.hrmon.heartratemonitor;

import com.hrmon.heartratemonitor.ConnectionManager.ChannelStates;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SessionManager implements ConnectionManager.Callbacks {

	private static final String TAG = "HRMon - Manager";
	
	/** Manager for the ANT+ connection. */
	private ConnectionManager mConnection;
    
	/** Data handler for the monitor session. */
    private SessionData mSession;
    	
	/** Pair to any device. */
	static final short WILDCARD = 0;
	   
	/** The default proximity search bin. */
	private static final byte DEFAULT_BIN = 7;
	
	/** The default event buffering buffer threshold. */
	private static final short DEFAULT_BUFFER_THRESHOLD = 0;
	   
	/** Shared preferences data filename. */
	public static final String PREFS_NAME = "HRMonPrefs";
	
    /**
     * Defines the interface needed to work with all call backs this class makes
     */
    public interface Callbacks
    {
        public void notifyNewData();
        public void notifyStateChanged();
    }
    
    /** Callback sink. */
    private Callbacks mCallbackSink;
    
    public void setCallbacks(Callbacks callbacks)
    {
        mCallbackSink = callbacks;
    }
    
    /**
     * Constructor
     */
    public SessionManager(ConnectionManager connection, SessionData session)
    {
    	mConnection = connection;
    	mSession = session;
    	
        mConnection.setCallbacks(SessionManager.this);
    }
    
    /**
     * Get state.
     */
    public ChannelStates getState()
    {
    	return mConnection.getHrmState();
    }
    
    /**
     * Get connection state text.
     */
    public String getConnStateText()
    {
    	if (mConnection.checkAntState()) {
    		return mConnection.getAntStateText();
    	}
    	
    	return null;
    }
    
    /**
     * Store application persistent data.
     */
    public void saveConfiguration(Context C)
    {
       // Save current Channel Id in preferences
       // We need an Editor object to make changes
       SharedPreferences settings = C.getSharedPreferences(PREFS_NAME, 0);
       SharedPreferences.Editor editor = settings.edit();
       editor.putInt("DeviceNumberHRM", mConnection.getDeviceNumberHRM());
       editor.putInt("ProximityThreshold", mConnection.getProximityThreshold());
       editor.putInt("BufferThreshold", mConnection.getBufferThreshold());
       editor.commit();
    }
    
    /**
     * Retrieve application persistent data.
     */
    public void loadConfiguration(Context C)
    {
       // Restore preferences
       SharedPreferences settings = C.getSharedPreferences(PREFS_NAME, 0);
       mConnection.setDeviceNumberHRM((short) settings.getInt("DeviceNumberHRM", WILDCARD));
       mConnection.setProximityThreshold((byte) settings.getInt("ProximityThreshold", DEFAULT_BIN));
       mConnection.setBufferThreshold((short) settings.getInt("BufferThreshold", DEFAULT_BUFFER_THRESHOLD));       
    }
    
    /**
     * Reset the ANT+ connection pairing.
     */
    public void resetPairing()
    {
    	disconnectSensor();
    	
    	mConnection.setDeviceNumberHRM(WILDCARD);
    	mConnection.setProximityThreshold(DEFAULT_BIN);
    	mConnection.setBufferThreshold(DEFAULT_BUFFER_THRESHOLD);
    }
    
    /**
     * Toggle the ANT+ connection [connect/disconnect]
     */
    public void toggleConnection()
    {
    	switch (mConnection.getHrmState()) {
		case CLOSED:
    		// Connect
    		connectSensor();
    		break;
		default:
			// Disconnect
			disconnectSensor();
			break;
    	}
    }
    
    /**
     * Establish ANT+ connection with HR Sensor.
     */
    public void connectSensor()
    {
		// Enable ANT+
    	if (!mConnection.isEnabled()) {
    		mConnection.doEnable();
    	}
    	
    	// Open HRM channel
    	// (reset on all attempts; otherwise would call 
    	// "openChannel(HRM_CHANNEL, false)" and exclude "requestReset()" 
        if (!mConnection.isChannelOpen(ConnectionManager.HRM_CHANNEL)) {
            Log.d(TAG, "onClick (HRM): Open channel");
            // Defer opening the channel until an ANT_RESET has been received
            mConnection.openChannel(ConnectionManager.HRM_CHANNEL, true);
            mConnection.requestReset();
        }
    }
    
    /**
     * Terminate ANT+ connection with HR Sensor.
     */
    public void disconnectSensor()
    {
    	
		// Close HRM channel
    	if (mConnection.isChannelOpen(ConnectionManager.HRM_CHANNEL)) {
            // Close channel
            Log.d(TAG, "onClick (HRM): Close channel");
            mConnection.closeChannel(ConnectionManager.HRM_CHANNEL);
        }
    	
    	// Disable ANT+
    	if (mConnection.isEnabled()) {
    		mConnection.doDisable();
    	}
    }
    
    /**
     * Toggles the session.
     */
    public void toggleSession()
    {
    	if (mSession.isStarted()) {
    		stopSession();
    	} else {
        	switch (mConnection.getHrmState()) {
        	case TRACKING_STATUS:
                // Connected (New data has arrived)
            case TRACKING_DATA:
            	// Connected (Sensor connected)
    			startSession();
    			break;
    		}
    	}
    }
    
    /**
     * Starts the session.
     */
    public void startSession()
    {
    	mSession.clear();
    	mSession.start();
    }
    
    /**
     * Stops the session.
     */
    public void stopSession()
    {
    	mSession.stop();
    }
    
    /**
     * Update HR data.
     */
    private void updateRR()
    {
    	mSession.addRR(mConnection.getRR());
    }
    
    /**
     * Update HR data.
     */
    private void updateBPM()
    {
    	mSession.addBPM(mConnection.getBPM());
    }
    
    /**
     * Update signal data.
     */
    private void updateSignal()
    {
    	mSession.addPacketsReceived(mConnection.getPacketsReceived());
    	mSession.addPacketsDropped(mConnection.getPacketsDropped());
    	
    	mSession.addRSSI(mConnection.getRSSI());
    }

 // ConnectionManager callback implementations

 	@Override
 	public void errorCallback()
 	{
 		// Update state with error
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyStateChanged();
 		}
 	}

 	@Override
 	public void notifyAntStateChanged()
 	{
 		// Update state based on new ANT+ status
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyStateChanged();
 		}
 	}
 	
 	@Override
 	public void notifyNewRRData()
 	{
 		// Update data with new stream from channel
 	 	updateRR();
 	 	
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyNewData();
 		}
 	}
 	
 	@Override
 	public void notifyNewBPMData()
 	{
 		// Update data with new stream from channel
 	 	updateBPM();
 	 	
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyNewData();
 		}
 	}
 	
 	@Override
 	public void notifyNewRSSIData(byte channel)
 	{
 		// Update data with new stream from channel
 	 	// Don't need to worry about channel; only using HRM
 	 	updateSignal();
 	 	
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyNewData();
 		}
 	}
 	
 	@Override
 	public void notifyChannelStateChanged(byte channel)
 	{
 		// Update state based on new channel status
 		// Don't need to worry about channel; only using HRM
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyStateChanged();
 		}
 	}
     
 	@Override
 	public void notifyChannelDataChanged(byte channel)
 	{
 		// Update data with new stream from channel
 		// Don't need to worry about channel; only using HRM
 		updateRR();
 		updateBPM();
 		updateSignal();
     	
 		if(mCallbackSink != null) {
 			mCallbackSink.notifyNewData();
 		}
 	}
	
}
