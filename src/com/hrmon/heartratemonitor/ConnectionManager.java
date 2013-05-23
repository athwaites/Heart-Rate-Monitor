/*
 * Copyright 2010 Dynastream Innovations Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.hrmon.heartratemonitor;

import java.lang.reflect.Field;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import com.dsi.ant.exception.*;
import com.dsi.ant.AntInterface;
import com.dsi.ant.AntInterfaceIntent;
import com.dsi.ant.AntMesg;
import com.dsi.ant.AntDefine;

/**
 * Connection Manager for an ANT+ Heart Rate Sensor.
 */
public class ConnectionManager {

    /**
     * Defines the interface needed to work with all call backs this class makes
     */
    public interface Callbacks
    {
        public void errorCallback();
        public void notifyAntStateChanged();
        public void notifyNewRRData();
        public void notifyNewBPMData();
        public void notifyNewRSSIData(byte channel);
        public void notifyChannelStateChanged(byte channel);
        public void notifyChannelDataChanged(byte channel);
    }
	
	/** The Log Tag. */
    public static final String TAG = "HRMon - CM";
	
	/** Interface to the ANT+ radio. */
	private AntInterface mAntReceiver;
	
	/** Is the ANT background service connected. */
    private boolean mServiceConnected = false;
	
	/** Container for the types of ANT status Intents to receive. */
    private IntentFilter mStatusIntentFilter;
    
    /** Flag to know if an ANT Reset was triggered by this application. */
    private boolean mAntResetSent = false;
    
    /** Flag if waiting for ANT_ENABLED. Default is now false, We assume ANT is disabled until told otherwise.*/
    private boolean mEnabling = false;
    
    /** Flag if waiting for ANT_DISABLED. Default is false, will be set to true when a disable is attempted. */
    private boolean mDisabling = false;
    
    /** Pair to any device. */
    static final short WILDCARD = 0;
    
    /** ANT+ channel for the HRM. */
    public static final byte HRM_CHANNEL = (byte) 0;
    
    /** ANT+ device type for an HRM */
    private static final byte HRM_DEVICE_TYPE = 0x78;
    
    /** ANT+ channel period for an HRM */
    private static final short HRM_PERIOD = 8070;
    
    /** ANT+ lib config flag for extended data with the Channel ID. */
    public static final byte LC_CHANID = (byte) 0x80;
    
    /** ANT+ lib config flag for extended data with the RSSI. */
    public static final byte LC_RSSI = (byte) 0x40;
    
    /** ANT+ lib config flag for extended data with the Timestamp. */
    public static final byte LC_TIMESTAMP = (byte) 0x20;
    
    /** ANT+ packet toggle bit mask */
    public static final byte PKT_TOGGLE_MASK = (byte) 0x80;
    
    /** ANT+ packet offset for the packet length */
    public static final int PKT_LENGTH = 0;
    
    /** ANT+ packet offset for the start of the payload */
    public static final int PKT_PAYLOAD = 3;
    
    /** ANT+ packet offset for the data page number */
    public static final int PKT_PAGE = PKT_PAYLOAD;
    
    /** ANT+ packet offset for the extended information */
    public static final int PKT_EXT = 11;
    
    //TODO: This string will eventually be provided by the system or by AntLib
    /** String used to represent ant in the radios list. */
    private static final String RADIO_ANT = "ant";
    
    /**
     * The possible HRM page toggle bit states.
     */
    public enum HRMPageState
    {
       /** Initialising (bit value not checked). */
       PAGE_INIT,
       
       /** Standard page. */
       PAGE_STD,
       
       /** Extended pages are valid. */
       PAGE_EXT
    }
    
    /** The current HRM page/toggle bit state. */
    private HRMPageState mStateHRM = HRMPageState.PAGE_INIT;
    
    /** The last HRM packet page. */
    private static byte mLastPage;
    
    /** Description of ANT's current state */
    private String mAntStateText = "";
    
    /** Possible states of a device channel */
    public enum ChannelStates
    {
       /** Channel was explicitly closed or has not been opened */
       CLOSED,
       
       /** User has requested we open the channel, but we are waiting for a reset */
       PENDING_OPEN,
       
       /** Channel is opened, but we have not received any data yet */
       SEARCHING,
       
       /** Channel is opened and has received status data from the device most recently */
       TRACKING_STATUS,
       
       /** Channel is opened and has received measurement data most recently */
       TRACKING_DATA,
       
       /** Channel is closed as the result of a search timeout */
       OFFLINE
    }

    /** Current state of the HRM channel */
    private ChannelStates mHrmState = ChannelStates.CLOSED;

    /** HRM Sensor Data Class */
    private class HRMData {
    	public short DeviceNumber;
    	// Paged Data
    	public int OperatingTime;
    	public int ManufacturerID;
    	public int SerialNumber;
    	public int HardwareVersion;
    	public int SoftwareVersion;
    	public int ModelNumber;
    	// Default Data
        public int CurrentBeatCount;
        public int PreviousBeatCount;
        public int CurrentBeatTime;
        public int PreviousBeatTime;
        public int RR;
        public int BPM;
        public int RSSI;
        // Stats
        public int PacketsReceived;
        public int PacketsDropped;
    }
    
    private HRMData mHRMData;
    
    /** Flag indicating that opening of the HRM channel was deferred */
    private boolean mDeferredHrmStart = false;
    
    /** Devices must be within this bin to be found during (proximity) search. */
    private byte mProximityThreshold;
    
    /** Channel Configuration Class */
    private class ChannelConfiguration {
        public short deviceNumber;
        public byte deviceType;
        public byte TransmissionType;
        public short period;
        public byte freq;
        public byte proxSearch;

        public boolean isInitializing = false;
        public boolean isDeinitializing = false;
    }
    
    private ChannelConfiguration mChannelConfig;
    
    //TODO You will want to set a separate threshold for screen off and (if desired) screen on.
    /** Data buffered for event buffering before flush. */
    private short mBufferThreshold;
    
	/** Indicates if the application controls the ANT+ interface. */
	private boolean mClaimedAntInterface;
	
    /** Application context. */
    private Context mContext;
    
    /** Callback sink. */
    private Callbacks mCallbackSink;
    
	/**
	 * Default constructor.
	 */
	ConnectionManager() {
		Log.d(TAG, "ConnectionManager: entering Constructor");
		
		// Initial states
		mDeferredHrmStart = false;
        mHrmState = ChannelStates.CLOSED;
        mHRMData = new HRMData();
        mChannelConfig = new ChannelConfiguration();
		
		// Initialise ANT+ control flag to false
		mClaimedAntInterface = false;
		
		// Define the intent filter for the ANT+ status
		mStatusIntentFilter = new IntentFilter();
		mStatusIntentFilter.addAction(AntInterfaceIntent.ANT_ENABLED_ACTION);
		mStatusIntentFilter.addAction(AntInterfaceIntent.ANT_ENABLING_ACTION);
		mStatusIntentFilter.addAction(AntInterfaceIntent.ANT_DISABLED_ACTION);
		mStatusIntentFilter.addAction(AntInterfaceIntent.ANT_DISABLING_ACTION);
		mStatusIntentFilter.addAction(AntInterfaceIntent.ANT_RESET_ACTION);
		mStatusIntentFilter.addAction(AntInterfaceIntent.ANT_INTERFACE_CLAIMED_ACTION);
		mStatusIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
		
		// Instantiate ANT Interface
		mAntReceiver = new AntInterface();
		
		Log.d(TAG, "ConnectionManager: leaving Constructor");
	}
	
	 /**
     * Creates the connection to the ANT service back-end.
     */
    public boolean start(Context context)
    {
        boolean initialised = false;
        
        mContext = context;
        
        if(AntInterface.hasAntSupport(mContext))
        {
            mContext.registerReceiver(mAntStatusReceiver, mStatusIntentFilter);
            
            if(!mAntReceiver.initService(mContext, mAntServiceListener))
            {
                // Need the ANT Radio Service installed.
            	Log.e(TAG, "AntChannelManager Constructor: No ANT Service.");
                requestServiceInstall();
            }
            else
            {
                mServiceConnected = mAntReceiver.isServiceConnected();

                if(mServiceConnected)
                {
                    try
                    {
                        mClaimedAntInterface = mAntReceiver.hasClaimedInterface();
                        if(mClaimedAntInterface)
                        {
                            receiveAntRxMessages(true);
                        }
                    }
                    catch (AntInterfaceException e)
                    {
                        antError();
                    }
                }
                
                initialised = true;
            }
        }
        
        return initialised;
    }
    
    /**
     * Requests that the user install the needed service for ant
     */
    private void requestServiceInstall()
    {
//        Toast installNotification = Toast.makeText(mContext, mContext.getResources().getString(R.string.ANT_Radio_Service_Required), Toast.LENGTH_LONG);
//        installNotification.show();

        AntInterface.goToMarket(mContext);
    }
    
    public void setCallbacks(Callbacks callbacks)
    {
        mCallbackSink = callbacks;
    }
    
    //Getters and setters
    
    public boolean isServiceConnected()
    {
        return mServiceConnected;
    }

    public short getDeviceNumberHRM()
    {
        return mHRMData.DeviceNumber;
    }

    public void setDeviceNumberHRM(short deviceNumberHRM)
    {
        this.mHRMData.DeviceNumber = deviceNumberHRM;
    }

    public byte getProximityThreshold()
    {
        return mProximityThreshold;
    }

    public void setProximityThreshold(byte proximityThreshold)
    {
        this.mProximityThreshold = proximityThreshold;
    }

    public short getBufferThreshold()
    {
        return mBufferThreshold;
    }

    public void setBufferThreshold(short bufferThreshold)
    {
        this.mBufferThreshold = bufferThreshold;
    }
    
    public HRMPageState getStateHRM()
    {
        return mStateHRM;
    }

    public ChannelStates getHrmState()
    {
        return mHrmState;
    }

    public int getBPM()
    {
        return mHRMData.BPM;
    }
    
    public int getRR()
    {
        return mHRMData.RR;
    }
    
    public int getRSSI()
    {
    	return mHRMData.RSSI;
    }
    
    public int getPacketsReceived()
    {
    	int result = mHRMData.PacketsReceived;
    	mHRMData.PacketsReceived = 0;
    	return result;
    }
    
    public int getPacketsDropped()
    {
    	int result = mHRMData.PacketsDropped;
    	mHRMData.PacketsDropped = 0;
    	return result;
    }

    public String getAntStateText()
    {
        return mAntStateText;
    }
    
    /**
     * Checks if ANT can be used by this application
     * Sets the AntState string to reflect current status.
     * @return true if this application can use the ANT chip, false otherwise.
     */
    public boolean checkAntState()
    {
        try
        {
            if(!AntInterface.hasAntSupport(mContext))
            {
                Log.w(TAG, "updateDisplay: ANT not supported");

                mAntStateText = mContext.getString(R.string.ANT_Not_Supported);
                return false;
            }
            else if(isAirPlaneModeOn())
            {
                mAntStateText = mContext.getString(R.string.ANT_Airplane_Mode);
                return false;
            }
            else if(mEnabling)
            {
                mAntStateText = mContext.getString(R.string.ANT_Enabling);
                return false;
            }
            else if(mDisabling)
            {
                mAntStateText = mContext.getString(R.string.ANT_Disabling);
                return false;
            }
            else if(mServiceConnected)
            {
                if(!mAntReceiver.isEnabled())
                {
                    mAntStateText = mContext.getString(R.string.ANT_Disabled);
                    return false;
                }
                if(mAntReceiver.hasClaimedInterface() || mAntReceiver.claimInterface())
                {
                    return true;
                }
                else
                {
                    mAntStateText = mContext.getString(R.string.ANT_In_Use);
                    return false;
                }
            }
            else
            {
                Log.w(TAG, "updateDisplay: Service not connected");

                mAntStateText = mContext.getString(R.string.ANT_Disabled);
                return false;
            }
        }
        catch(AntInterfaceException e)
        {
            antError();
            return false;
        }
    }

    /**
     * Attempts to claim the Ant interface
     */
    public void tryClaimAnt()
    {
        try
        {
            mAntReceiver.requestForceClaimInterface(mContext.getResources().getString(R.string.app_name));
        }
        catch(AntInterfaceException e)
        {
            antError();
        }
    }

    /**
     * Unregisters all our receivers in preparation for application shutdown
     */
    public void shutDown()
    {
        try
        {
            mContext.unregisterReceiver(mAntStatusReceiver);
        }
        catch(IllegalArgumentException e)
        {
            // Receiver wasn't registered, ignore as that's what we wanted anyway
        }
        
        receiveAntRxMessages(false);
        
        if(mServiceConnected)
        {
            try
            {
                if(mClaimedAntInterface)
                {
                    Log.d(TAG, "AntChannelManager.shutDown: Releasing interface");

                    mAntReceiver.releaseInterface();
                }

                mAntReceiver.stopRequestForceClaimInterface();
            }
            catch(AntServiceNotConnectedException e)
            {
                // Ignore as we are disconnecting the service/closing the app anyway
            }
            catch(AntInterfaceException e)
            {
               Log.w(TAG, "Exception in AntChannelManager.shutDown", e);
            }
            
            mAntReceiver.releaseService();
        }
    }

    /**
     * Class for receiving notifications about ANT service state.
     */
    private AntInterface.ServiceListener mAntServiceListener = new AntInterface.ServiceListener()
    {
        public void onServiceConnected()
        {
            Log.d(TAG, "mAntServiceListener onServiceConnected()");

            mServiceConnected = true;

            try
            {

                mClaimedAntInterface = mAntReceiver.hasClaimedInterface();

                if (mClaimedAntInterface)
                {
                    // mAntMessageReceiver should be registered any time we have
                    // control of the ANT Interface
                    receiveAntRxMessages(true);
                } else
                {
                    // Need to claim the ANT Interface if it is available, now
                    // the service is connected
                    mClaimedAntInterface = mAntReceiver.claimInterface();
                }
            } catch (AntInterfaceException e)
            {
                antError();
            }

            Log.d(TAG, "mAntServiceListener Displaying icons only if radio enabled");
            if(mCallbackSink != null)
                mCallbackSink.notifyAntStateChanged();
        }

        public void onServiceDisconnected()
        {
            Log.d(TAG, "mAntServiceListener onServiceDisconnected()");

            mServiceConnected = false;
            mEnabling = false;
            mDisabling = false;

            if (mClaimedAntInterface)
            {
                receiveAntRxMessages(false);
            }

            if(mCallbackSink != null)
                mCallbackSink.notifyAntStateChanged();
        }
    };
    
    /**
     * Configure the ANT radio to the user settings.
     */
    public void setAntConfiguration()
    {
        try
        {
            if(mServiceConnected && mClaimedAntInterface && mAntReceiver.isEnabled())
            {
                try
                {
                    // Event Buffering Configuration
                    if(mBufferThreshold > 0)
                    {
                        //TODO For easy demonstration will set screen on and screen off thresholds to the same value.
                        // No buffering by interval here.
                        mAntReceiver.ANTConfigEventBuffering((short)0xFFFF, mBufferThreshold, (short)0xFFFF, mBufferThreshold);
                    }
                    else
                    {
                        mAntReceiver.ANTDisableEventBuffering();
                    }
                }
                catch(AntInterfaceException e)
                {
                    Log.e(TAG, "Could not configure event buffering", e);
                }
            }
            else
            {
                Log.i(TAG, "Can't set event buffering right now.");
            }
        } catch (AntInterfaceException e)
        {
            Log.e(TAG, "Problem checking enabled state.");
        }
    }
    
    /**
     * Display to user that an error has occurred communicating with ANT Radio.
     */
    private void antError()
    {
        mAntStateText = mContext.getString(R.string.ANT_Error);
        if(mCallbackSink != null)
            mCallbackSink.errorCallback();
    }
    
    /**
     * Opens a given channel using the proper configuration for the channel's sensor type.
     * @param channel The channel to Open.
     * @param deferToNextReset If true, channel will not open until the next reset.
     */
    public void openChannel(byte channel, boolean deferToNextReset)
    {
        Log.i(TAG, "Starting service.");
        mContext.startService(new Intent(mContext, HeartRateMonitorService.class));
        if (!deferToNextReset)
        {
        	mChannelConfig.deviceNumber = mHRMData.DeviceNumber;
        	mChannelConfig.deviceType = HRM_DEVICE_TYPE;
        	mChannelConfig.TransmissionType = 0; // Set to 0 for wild card search
        	mChannelConfig.period = HRM_PERIOD;
        	mChannelConfig.freq = 57; // 2457Mhz (ANT+ frequency)
        	mChannelConfig.proxSearch = mProximityThreshold;
            mHrmState = ChannelStates.PENDING_OPEN;

            if(mCallbackSink != null)
                mCallbackSink.notifyChannelStateChanged(channel);
            // Configure and open channel
            antChannelSetup(
                    (byte) 0x01, // Network: 1 (ANT+)
                    channel // channelConfig[channel] holds all the required info
                    );
        }
        else
        {
            mDeferredHrmStart = true;
            mHrmState = ChannelStates.PENDING_OPEN;
        }
    }
    
    /**
     * Attempts to cleanly close a specified channel 
     * @param channel The channel to close.
     */
    public void closeChannel(byte channel)
    {
    	mChannelConfig.isInitializing = false;
    	mChannelConfig.isDeinitializing = true;

        mHrmState = ChannelStates.CLOSED;

        if(mCallbackSink != null)
            mCallbackSink.notifyChannelStateChanged(channel);
        try
        {
           mAntReceiver.ANTCloseChannel(channel);
           // Unassign channel after getting channel closed event
        }
        catch (AntInterfaceException e)
        {
           Log.w(TAG, "closeChannel: could not cleanly close channel " + channel + ".");
           antError();
        }
        if((mHrmState == ChannelStates.CLOSED || mHrmState == ChannelStates.OFFLINE))
        {
            Log.i(TAG, "Stopping service.");
            mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));
        }
    }
    
    /**
     * Resets the channel state machines, used in error recovery.
     */
    public void clearChannelStates()
    {
        Log.i(TAG, "Stopping service.");
        mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));
        mHrmState = ChannelStates.CLOSED;
        if(mCallbackSink != null)
        {
			// TODO: Remove all calls to notifyChannelStateChanged(byte) ... don't need other (non-HR) channels
            mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
        }
    }
    
    /** check to see if a channel is open */
    public boolean isChannelOpen(byte channel)
    {
        if(mHrmState == ChannelStates.CLOSED || mHrmState == ChannelStates.OFFLINE) {
            return false;
        }

        return true;
    }
    
    /** request an ANT reset */
    public void requestReset()
    {
        try
        {
            mAntResetSent = true;
            mAntReceiver.ANTResetSystem();
            setAntConfiguration();
        } catch (AntInterfaceException e) {
            Log.e(TAG, "requestReset: Could not reset ANT", e);
            mAntResetSent = false;
            //Cancel pending channel open requests
            if(mDeferredHrmStart)
            {
                mDeferredHrmStart = false;
                mHrmState = ChannelStates.CLOSED;
                if(mCallbackSink != null)
                    mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
            }
        }
    }
    
    /**
     * Check if ANT is enabled
     * @return True if ANT is enabled, false otherwise.
     */
    public boolean isEnabled()
    {
        if(mAntReceiver == null || !mAntReceiver.isServiceConnected())
            return false;
        try
        {
            return mAntReceiver.isEnabled();
        } catch (AntInterfaceException e)
        {
            Log.w(TAG, "Problem checking enabled state.");
            return false;
        }
    }
    
    /**
     * Attempt to enable the ANT chip.
     */
    public void doEnable()
    {
        if(mAntReceiver == null || mDisabling || isAirPlaneModeOn())
            return;
        try
        {
            mAntReceiver.enable();
        } catch (AntInterfaceException e)
        {
            //Not much error recovery possible.
            Log.e(TAG, "Could not enable ANT.");
            return;
        }
    }
    
    /**
     * Attempt to disable the ANT chip.
     */
    public void doDisable()
    {
        if(mAntReceiver == null || mEnabling)
            return;
        try
        {
            mAntReceiver.disable();
        } catch (AntInterfaceException e)
        {
            //Not much error recovery possible.
            Log.e(TAG, "Could not disable ANT.");
            return;
        }
    }
    
    /** Receives all of the ANT status intents. */
    private final BroadcastReceiver mAntStatusReceiver = new BroadcastReceiver() 
    {      
       public void onReceive(Context context, Intent intent) 
       {
          String ANTAction = intent.getAction();

          Log.d(TAG, "enter onReceive: " + ANTAction);
          if (ANTAction.equals(AntInterfaceIntent.ANT_ENABLING_ACTION))
          {
              Log.i(TAG, "onReceive: ANT ENABLING");
              mEnabling = true;
              mDisabling = false;
              mAntStateText = mContext.getString(R.string.ANT_Enabling);
              if(mCallbackSink != null)
                  mCallbackSink.notifyAntStateChanged();
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_ENABLED_ACTION)) 
          {
             Log.i(TAG, "onReceive: ANT ENABLED");
             
             mEnabling = false;
             mDisabling = false;
             if(mCallbackSink != null)
                 mCallbackSink.notifyAntStateChanged();
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_DISABLING_ACTION))
          {
              Log.i(TAG, "onReceive: ANT DISABLING");
              mEnabling = false;
              mDisabling = true;
              mAntStateText = mContext.getString(R.string.ANT_Disabling);
              if(mCallbackSink != null)
                  mCallbackSink.notifyAntStateChanged();
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_DISABLED_ACTION)) 
          {
             Log.i(TAG, "onReceive: ANT DISABLED");
             mHrmState = ChannelStates.CLOSED;
             mAntStateText = mContext.getString(R.string.ANT_Disabled);
             
             mEnabling = false;
             mDisabling = false;
             
             if(mCallbackSink != null)
             {
                 mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                 mCallbackSink.notifyAntStateChanged();
             }
             Log.i(TAG, "Stopping service.");
             mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_RESET_ACTION))
          {
             Log.d(TAG, "onReceive: ANT RESET");
             
             Log.i(TAG, "Stopping service.");
             mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));
             
             if(false == mAntResetSent)
             {
                //Someone else triggered an ANT reset
                Log.d(TAG, "onReceive: ANT RESET: Resetting state");
                
                if(mHrmState != ChannelStates.CLOSED)
                {
                   mHrmState = ChannelStates.CLOSED;
                   if(mCallbackSink != null)
                       mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                }
             }
             else
             {
                mAntResetSent = false;
                //Reconfigure event buffering
                setAntConfiguration();
                //Check if opening a channel was deferred, if so open it now.
                if(mDeferredHrmStart)
                {
					// TODO: Simplify openChannel to only open HR settings; no need for non-HR channels
                    openChannel(HRM_CHANNEL, false);
                    mDeferredHrmStart = false;
                }
             }
          }
          else if (ANTAction.equals(AntInterfaceIntent.ANT_INTERFACE_CLAIMED_ACTION)) 
          {
             Log.i(TAG, "onReceive: ANT INTERFACE CLAIMED");
             
             boolean wasClaimed = mClaimedAntInterface;
             
             // Could also read ANT_INTERFACE_CLAIMED_PID from intent and see if it matches the current process PID.
             try
             {
                 mClaimedAntInterface = mAntReceiver.hasClaimedInterface();

                 if(mClaimedAntInterface)
                 {
                     Log.i(TAG, "onReceive: ANT Interface claimed");

                     receiveAntRxMessages(true);
                 }
                 else
                 {
                     // Another application claimed the ANT Interface...
                     if(wasClaimed)
                     {
                         // ...and we had control before that.  
                         Log.i(TAG, "onReceive: ANT Interface released");
                         
                         Log.i(TAG, "Stopping service.");
                         mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));

                         receiveAntRxMessages(false);
                         
                         mAntStateText = mContext.getString(R.string.ANT_In_Use);
                         if(mCallbackSink != null)
                             mCallbackSink.notifyAntStateChanged();
                     }
                 }
             }
             catch(AntInterfaceException e)
             {
                 antError();
             }
          }
          else if (ANTAction.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED))
          {
              Log.i(TAG, "onReceive: AIR_PLANE_MODE_CHANGED");
              if(isAirPlaneModeOn())
              {
                  mHrmState = ChannelStates.CLOSED;
                  mAntStateText = mContext.getString(R.string.ANT_Airplane_Mode);
                  
                  Log.i(TAG, "Stopping service.");
                  mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));
                  
                  if(mCallbackSink != null)
                  {
                      mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                      mCallbackSink.notifyAntStateChanged();
                  }
              }
              else
              {
                  if(mCallbackSink != null)
                      mCallbackSink.notifyAntStateChanged();
              }
          }
          if(mCallbackSink != null)
              mCallbackSink.notifyAntStateChanged();
       }
    };
    
    public static String getHexString(byte[] data)
    {
        if(null == data)
        {
            return "";
        }

        StringBuffer hexString = new StringBuffer();
        for(int i = 0;i < data.length; i++)
        {
           hexString.append("[").append(String.format("%02X", data[i] & 0xFF)).append("]");
        }

        return hexString.toString();
    }
    
    /** Receives all of the ANT message intents and dispatches to the proper handler. */
    private final BroadcastReceiver mAntMessageReceiver = new BroadcastReceiver() 
    {      
       Context mContext;

       public void onReceive(Context context, Intent intent) 
       {
          mContext = context;
          String ANTAction = intent.getAction();

          Log.d(TAG, "enter onReceive: " + ANTAction);
          if (ANTAction.equals(AntInterfaceIntent.ANT_RX_MESSAGE_ACTION)) 
          {
             Log.d(TAG, "onReceive: ANT RX MESSAGE");

             byte[] ANTRxMessage = intent.getByteArrayExtra(AntInterfaceIntent.ANT_MESSAGE);

             Log.d(TAG, "Rx:"+ getHexString(ANTRxMessage));

             switch(ANTRxMessage[AntMesg.MESG_ID_OFFSET])
             {
                 case AntMesg.MESG_STARTUP_MESG_ID:
                     break;
                 case AntMesg.MESG_BROADCAST_DATA_ID:
                 case AntMesg.MESG_ACKNOWLEDGED_DATA_ID:
                     byte channelNum = ANTRxMessage[AntMesg.MESG_DATA_OFFSET];
                     if(channelNum == HRM_CHANNEL) {
                         antDecodeHRM(ANTRxMessage);
                     }
                     
                     // ANT received a message in the designated channel period
                     mHRMData.PacketsReceived++;
                     
                     break;
                 case AntMesg.MESG_BURST_DATA_ID:
                     break;
                 case AntMesg.MESG_RESPONSE_EVENT_ID:
                     responseEventHandler(ANTRxMessage);
                     break;
                 case AntMesg.MESG_CHANNEL_STATUS_ID:
                     break;
                 case AntMesg.MESG_CHANNEL_ID_ID:
                     short deviceNum = (short) ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1]&0xFF | ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2]&0xFF) << 8)) & 0xFFFF);
                     if(ANTRxMessage[AntMesg.MESG_DATA_OFFSET] == HRM_CHANNEL) {   //Switch on channel number
                         Log.i(TAG, "onRecieve: Received HRM device number");
                         mHRMData.DeviceNumber = deviceNum;
                     }
                     break;
                 case AntMesg.MESG_VERSION_ID:
                     break;
                 case AntMesg.MESG_CAPABILITIES_ID:
                     break;
                 case AntMesg.MESG_GET_SERIAL_NUM_ID:
                     break;
                 case AntMesg.MESG_EXT_ACKNOWLEDGED_DATA_ID:
                     break;
                 case AntMesg.MESG_EXT_BROADCAST_DATA_ID:
                     break;
                 case AntMesg.MESG_EXT_BURST_DATA_ID:
                     break;
             }
          }
       }
       
       /**
        * Handles response and channel event messages
        * @param ANTRxMessage
        */
       private void responseEventHandler(byte[] ANTRxMessage)
       {
           // For a list of possible message codes
           // see ANT Message Protocol and Usage section 9.5.6.1
           // available from thisisant.com
           byte channelNumber = ANTRxMessage[AntMesg.MESG_DATA_OFFSET];

           if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_EVENT_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.EVENT_RX_SEARCH_TIMEOUT))
           {
               // A channel timed out searching, unassign it
               mChannelConfig.isInitializing = false;
               mChannelConfig.isDeinitializing = false;

               if(channelNumber == HRM_CHANNEL) {
                   try
                   {
                       Log.i(TAG, "responseEventHandler: Received search timeout on HRM channel");

                       mHrmState = ChannelStates.OFFLINE;
                       if(mCallbackSink != null)
                           mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                       mAntReceiver.ANTUnassignChannel(HRM_CHANNEL);
                   }
                   catch(AntInterfaceException e)
                   {
                       antError();
                   }
               }
               if((mHrmState == ChannelStates.CLOSED || mHrmState == ChannelStates.OFFLINE))
               {
                   Log.i(TAG, "Stopping service.");
                   mContext.stopService(new Intent(mContext, HeartRateMonitorService.class));
               }
           }
           
           if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_EVENT_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.EVENT_RX_FAIL)) {
        	   // ANT failed to receive a message in the designated channel period
        	   mHRMData.PacketsDropped++;
           }
           
           if (mChannelConfig.isInitializing)
           {
               if (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] != 0) // Error response
               {
                   Log.e(TAG, String.format("Error code(%#02x) on message ID(%#02x) on channel %d", ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2], ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1], channelNumber));
               }
               else
               {
                   switch (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1]) // Switch on Message ID
                   {
                       case AntMesg.MESG_ASSIGN_CHANNEL_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelId(channelNumber, mChannelConfig.deviceNumber, mChannelConfig.deviceType, mChannelConfig.TransmissionType);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_ID_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelPeriod(channelNumber, mChannelConfig.period);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_MESG_PERIOD_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelRFFreq(channelNumber, mChannelConfig.freq);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_RADIO_FREQ_ID:
                           try
                           {
                               mAntReceiver.ANTSetChannelSearchTimeout(channelNumber, HRM_CHANNEL); // Disable high priority search
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_CHANNEL_SEARCH_TIMEOUT_ID:
                           try
                           {
                               mAntReceiver.ANTSetLowPriorityChannelSearchTimeout(channelNumber,(byte) 12); // Set search timeout to 30 seconds (low priority search)
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_SET_LP_SEARCH_TIMEOUT_ID:
                           if (mChannelConfig.deviceNumber == WILDCARD)
                           {
                               try
                               {
                                   mAntReceiver.ANTSetProximitySearch(channelNumber, mChannelConfig.proxSearch);   // Configure proximity search, if using wild card search
                               }
                               catch (AntInterfaceException e)
                               {
                                   antError();
                               }
                           }
                           else
                           {
                               try
                               {
                                   mAntReceiver.ANTOpenChannel(channelNumber);
                               }
                               catch (AntInterfaceException e)
                               {
                                   antError();
                               }
                           }
                           break;
                       case AntMesg.MESG_PROX_SEARCH_CONFIG_ID:
                           try
                           {
                               mAntReceiver.ANTOpenChannel(channelNumber);
                           }
                           catch (AntInterfaceException e)
                           {
                               antError();
                           }
                           break;
                       case AntMesg.MESG_OPEN_CHANNEL_ID:
                    	   mChannelConfig.isInitializing = false;
                           if(channelNumber == HRM_CHANNEL) {
                               mHrmState = ChannelStates.SEARCHING;
                               if(mCallbackSink != null)
                                   mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
                           }
                   }
               }
           }
           else if (mChannelConfig.isDeinitializing)
           {
               if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_EVENT_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.EVENT_CHANNEL_CLOSED))
               {
                   try
                   {
                       mAntReceiver.ANTUnassignChannel(channelNumber);
                   }
                   catch (AntInterfaceException e)
                   {
                       antError();
                   }
               }
               else if ((ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 1] == AntMesg.MESG_UNASSIGN_CHANNEL_ID) && (ANTRxMessage[AntMesg.MESG_DATA_OFFSET + 2] == AntDefine.RESPONSE_NO_ERROR))
               {
            	   mChannelConfig.isDeinitializing = false;
               }
           }
       }
       
       /**
        * Decode ANT+ HRM messages.
        *
        * @param ANTRxMessage the received ANT message.
        */
       private void antDecodeHRM(byte[] ANTRxMessage)
       {
    	   Log.d(TAG, "antDecodeHRM start");
          
    	   Log.d(TAG, "antDecodeHRM: Received broadcast");
         
    	   if(mHrmState != ChannelStates.CLOSED)
    	   {
    		   Log.d(TAG, "antDecodeHRM: Tracking data");

    		   mHrmState = ChannelStates.TRACKING_DATA;
    		   if(mCallbackSink != null)
    			   mCallbackSink.notifyChannelStateChanged(HRM_CHANNEL);
    	   }

    	   if(mHRMData.DeviceNumber == WILDCARD)
    	   {
    		   try
    		   {
    			   Log.i(TAG, "antDecodeHRM: Requesting device number");
    			   mAntReceiver.ANTRequestMessage(HRM_CHANNEL, AntMesg.MESG_CHANNEL_ID_ID);
    		   }
    		   catch(AntInterfaceException e)
    		   {
    			   antError();
    		   }
    	   }

    	   // Monitor page toggle bit
    	   byte currentPage = (byte) ANTRxMessage[PKT_PAGE];
    	   switch (mStateHRM) {
    	   case PAGE_INIT:
    		   mStateHRM = HRMPageState.PAGE_STD;
    		   break;
    	   case PAGE_STD:
    		   if (currentPage == mLastPage) {
    			   break;
    		   }
    		   // Intentionally fall through into PAGE_EXT if the page has changed
    		   mStateHRM = HRMPageState.PAGE_EXT;
    	   case PAGE_EXT:
    		   switch (currentPage & ~PKT_TOGGLE_MASK) {
    		   case 1:
    			   // Decode the cumulative operating time
    			   mHRMData.OperatingTime = (ANTRxMessage[PKT_PAYLOAD + 1] & 0xFF);
    			   mHRMData.OperatingTime |= (ANTRxMessage[PKT_PAYLOAD + 2] & 0xFF) << 8;
    			   mHRMData.OperatingTime |= (ANTRxMessage[PKT_PAYLOAD + 3] & 0xFF) << 16;
    			   mHRMData.OperatingTime *= 2;
    			   break;
    		   case 2:
    			   // Decode the Manufacturer ID
    			   mHRMData.ManufacturerID = ANTRxMessage[PKT_PAYLOAD + 1];
    			   // Decode the 4-byte Serial Number
    			   mHRMData.SerialNumber = mHRMData.DeviceNumber;
    			   mHRMData.SerialNumber |= (ANTRxMessage[PKT_PAYLOAD + 2] & 0xFF) << 16;
    			   mHRMData.SerialNumber |= (ANTRxMessage[PKT_PAYLOAD + 3] & 0xFF) << 24;
    			   break;
    		   case 3:
    			   // Decode the Hardware Version, Software Version and Model Number
    			   mHRMData.HardwareVersion = (ANTRxMessage[PKT_PAYLOAD + 1] & 0xFF);
    			   mHRMData.SoftwareVersion = (ANTRxMessage[PKT_PAYLOAD + 2] & 0xFF);
    			   mHRMData.ModelNumber = (ANTRxMessage[PKT_PAYLOAD + 3] & 0xFF);
    			   break;
    		   case 4:
    			   // Decode the previous heart beat measurement time
//    			   mHRMData.PreviousBeatTime = (ANTRxMessage[PKT_PAYLOAD + 2] & 0xFF);
//    			   mHRMData.PreviousBeatTime |= (ANTRxMessage[PKT_PAYLOAD + 3] & 0xFF) << 8;
    			   break;
    		   }
    		   break;
    	   }
    	   
    	   mLastPage = currentPage;
    	   
    	   antDecodeDefaultHRM(ANTRxMessage);
             
    	   Log.d(TAG, "antDecodeHRM end");
       	}
       
       
   		/**
        * Decode Default ANT+ HRM messages.
        *
        * @param ANTRxMessage the received ANT message.
        */
       	private void antDecodeDefaultHRM(byte[] ANTRxMessage)
       	{
       		// Decode the beat time
       		mHRMData.CurrentBeatTime = (ANTRxMessage[PKT_PAYLOAD + 4] & 0xFF);
       		mHRMData.CurrentBeatTime |= (ANTRxMessage[PKT_PAYLOAD + 5] & 0xFF) << 8;
           
       		// Decode the beat count
       		mHRMData.CurrentBeatCount = (ANTRxMessage[PKT_PAYLOAD + 6] & 0xFF);
           
       		// Heart rate            
       		mHRMData.BPM = (ANTRxMessage[PKT_PAYLOAD + 7] & 0xFF);
       		
       		if (ANTRxMessage[PKT_LENGTH] > (PKT_EXT - 1)) {
       			// Extended data exists in packet
       			// Extract RSSI from packet
       			mHRMData.RSSI = ANTRxMessage[PKT_EXT + 2];
       			
       			// Callback to notify arrival of new RSSI data
           		if(mCallbackSink != null) {
           			mCallbackSink.notifyNewRSSIData(HRM_CHANNEL);
           		}
       		}
       		
       		if (mHRMData.CurrentBeatCount != mHRMData.PreviousBeatCount) {
       			// New beat has arrived
       			
           		// Callback to notify arrival of new BPM data
           		if(mCallbackSink != null) {
           			mCallbackSink.notifyNewBPMData();
           		}
           		
       			if (((mHRMData.PreviousBeatCount + 1) & 0xFF) == mHRMData.CurrentBeatCount) {
		   			// Confirmed one beat between intervals, accommodating for byte overflow, (can now obtain RR)
		   			// RR interval
       				if (mHRMData.CurrentBeatTime > mHRMData.PreviousBeatTime) {
       					mHRMData.RR = (mHRMData.CurrentBeatTime - mHRMData.PreviousBeatTime);
       				} else {
       					// Overflow condition
       					mHRMData.RR = (mHRMData.CurrentBeatTime + (0xFFFF - mHRMData.PreviousBeatTime));
       				}
       				// Convert result from 1/1024sec into milliseconds
		       		mHRMData.RR = (int)((mHRMData.RR * 1000) / 1024);
		       		
		       		// Callback to notify arrival of new RR data
		       		if(mCallbackSink != null) {
		       			mCallbackSink.notifyNewRRData();
		       		}
		   		}
       		}
       		
       		// Update the previous beat count and beat time
       		mHRMData.PreviousBeatCount = mHRMData.CurrentBeatCount;
       		mHRMData.PreviousBeatTime = mHRMData.CurrentBeatTime;
       	}
    };
    
    /**
     * ANT Lib Config to enable extended data
     *
     * @param libConfig the lib config parameter; OR the desired LC_* options together
     */
    private void antLibConfig(byte libConfig) throws AntInterfaceException
    {
    	byte[] enableExt = new byte[4];
    	
    	enableExt[0] = (byte) 2;			// Length
    	enableExt[1] = (byte) 0x6e;			// Message ID
    	enableExt[2] = (byte) 0;			// Filler
    	enableExt[3] = (byte) libConfig;	// Lib Config value
    	
    	mAntReceiver.ANTTxMessage(enableExt);
    }
    
    /**
     * ANT Channel Configuration.
     *
     * @param networkNumber the network number
     * @param channelNumber the channel number
     * @param deviceNumber the device number
     * @param deviceType the device type
     * @param txType the tx type
     * @param channelPeriod the channel period
     * @param radioFreq the radio freq
     * @param proxSearch the prox search
     * @return true, if successfully configured and opened channel
     */   
    private void antChannelSetup(byte networkNumber, byte channel)
    {
       try
       {
    	   mChannelConfig.isInitializing = true;
           mChannelConfig.isDeinitializing = false;

           // Configure ANT+ to send extended data with RSSI
           antLibConfig(LC_RSSI);
           
           mAntReceiver.ANTAssignChannel(channel, AntDefine.PARAMETER_RX_NOT_TX, networkNumber);  // Assign as slave channel on selected network (0 = public, 1 = ANT+, 2 = ANTFS)
           // The rest of the channel configuration will occur after the response is received (in responseEventHandler)
       }
       catch(AntInterfaceException aie)
       {
           antError();
       }
    }
    
    /**
     * Enable/disable receiving ANT Rx messages.
     *
     * @param register If want to register to receive the ANT Rx Messages
     */
    private void receiveAntRxMessages(boolean register)
    {
        if(register)
        {
            Log.i(TAG, "receiveAntRxMessages: START");
            mContext.registerReceiver(mAntMessageReceiver, new IntentFilter(AntInterfaceIntent.ANT_RX_MESSAGE_ACTION));
        }
        else
        {
            try
            {
                mContext.unregisterReceiver(mAntMessageReceiver);
            }
            catch(IllegalArgumentException e)
            {
                // Receiver wasn't registered, ignore as that's what we wanted anyway
            }

            Log.i(TAG, "receiveAntRxMessages: STOP");
        }
    }
    
    /**
     * Checks if ANT is sensitive to airplane mode, if airplane mode is on and if ANT is not toggleable in airplane
     * mode. Only returns true if all 3 criteria are met.
     * @return True if airplane mode is stopping ANT from being enabled, false otherwise.
     */
    private boolean isAirPlaneModeOn()
    {
        if(!Settings.System.getString(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_RADIOS).contains(RADIO_ANT))
            return false;
        if(Settings.System.getInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 0)
            return false;
        
        try
        {
            Field field = Settings.System.class.getField("AIRPLANE_MODE_TOGGLEABLE_RADIOS");
            if(Settings.System.getString(mContext.getContentResolver(),
                    (String) field.get(null)).contains(RADIO_ANT))
                return false;
            else
                return true;
        } catch(Exception e)
        {
            return true; //This is expected if the list does not yet exist so we just assume we would not be on it.
        }
    }

}
