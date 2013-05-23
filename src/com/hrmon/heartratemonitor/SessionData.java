package com.hrmon.heartratemonitor;

import android.os.SystemClock;

public class SessionData {
	
	/** Time limit (24h) */
	public static final long SESSION_TIMELIMIT_HOURS = 12;
	public static final long SESSION_TIMELIMIT_MILLISEC = SESSION_TIMELIMIT_HOURS * 3600000;
	
	/** Started flag */
	private boolean mIsStarted = false;
	
	/** Starting elapsed time in milliseconds */
	private long mStartTime = 0;
	
	/** Starting elapsed time in milliseconds */
	private long mElapsedTime = 0;
	
	/** Packets received in this session */
	private int mPacketsReceived = 0;
	
	/** Packets dropped in this session */
	private int mPacketsDropped = 0;
	
	/** Total packets in this session */
	private long mTotalPackets = 0;
	
	/** Current packet throughput */
	private int mThroughput = 0;
	
	/** RR data */
	private TimestampedArray<Integer> mRR = new TimestampedArray<Integer>();
	
	/** BPM data */
	private TimestampedArray<Integer> mBPM = new TimestampedArray<Integer>();
		
	/** Received signal strength indicator data */
	private TimestampedArray<Integer> mRSSI = new TimestampedArray<Integer>();
	
	/** Dropped packet status data */
	private TimestampedArray<Integer> mDroppedPackets = new TimestampedArray<Integer>();
	
	/** Received packet status data */
	private TimestampedArray<Integer> mReceivedPackets = new TimestampedArray<Integer>();
	
	/** Get the started flag state */
	public boolean isStarted()
	{
		return mIsStarted;
	}
	
	/** Get the elapsed time */
	public long getElapsedTime()
	{
		if (mIsStarted) {
			mElapsedTime = (SystemClock.elapsedRealtime() - mStartTime);
		}
		
		return mElapsedTime;
	}
	
	/** Get the current received packets counter */
	public long getPacketsReceived()
	{
		return mPacketsReceived;
	}
	
	/** Get the current dropped packets counter */
	public long getPacketsDropped()
	{
		return mPacketsDropped;
	}
	
	/** Get the current total packets counter */
	public long getPacketsTotal()
	{
		return mTotalPackets;
	}
	
	/** Get the current packet throughput */
	public int getPacketThroughput()
	{
		return mThroughput;
	}
	
	/** Get the last RR */
	public int getLastRR()
	{
		Integer val = mRR.getLast();
		
		if (val == null) {
			return 0;
		}
		
		return val;
	}
	
	/** Get the last BPM */
	public int getLastBPM()
	{
		Integer val = mBPM.getLast();
		
		if (val == null) {
			return 0;
		}
		
		return val;
	}

	/** Get the last RSSI */
	public int getLastRSSI()
	{
		Integer val = mRSSI.getLast();
		
		if (val == null) {
			return 0;
		}
		
		return val;
	}
	
	/** Get the RRs */
	public Integer[] getRRs()
	{
		return mRR.get().toArray(new Integer[0]);
	}
	
	/** Get the RR times */
	public Long[] getRRTimes()
	{
		return mRR.getTime().toArray(new Long[0]);
	}
	
	/** Get the HRs */
	public Integer[] getBPMs()
	{
		return mBPM.get().toArray(new Integer[0]);
	}
	
	/** Get the HR times */
	public Long[] getBPMTimes()
	{
		return mBPM.getTime().toArray(new Long[0]);
	}
	
	/** Get the RSSIs */
	public Integer[] getRSSIs()
	{
		return mRSSI.get().toArray(new Integer[0]);
	}
	
	/** Get the RSSI times */
	public Long[] getRSSITimes()
	{
		return mRSSI.getTime().toArray(new Long[0]);
	}
	
	/** Get the Dropped Packets */
	public Integer[] getDroppedPackets()
	{
		return mDroppedPackets.get().toArray(new Integer[0]);
	}
	
	/** Get the Dropped Packet times */
	public Long[] getDroppedPacketTimes()
	{
		return mDroppedPackets.getTime().toArray(new Long[0]);
	}
	
	/** Get the Received Packets */
	public Integer[] getReceivedPackets()
	{
		return mReceivedPackets.get().toArray(new Integer[0]);
	}
	
	/** Get the Received Packet times */
	public Long[] getReceivedPacketTimes()
	{
		return mReceivedPackets.getTime().toArray(new Long[0]);
	}
	
	/** Add a new RR to the session data */
	public void addRR(int curRR)
	{
		checkTimeLimit();
		
		if (mIsStarted) {
			mRR.add(curRR);
		}
	}
	
	/** Add a new BPM to the session data */
	public void addBPM(int curBPM)
	{
		checkTimeLimit();
		
		if (mIsStarted) {
			mBPM.add(curBPM);
		}
	}
	
	/** Add a new RSSI to the session data */
	public void addRSSI(int curRSSI)
	{
		checkTimeLimit();
		
		if (mIsStarted) {
			mRSSI.add(curRSSI);
		}
	}
	
	/** Add the packets received */
	public void addPacketsReceived(int packetsReceived)
	{
		checkTimeLimit();
		
		if (mIsStarted) {
			// Add the number of received packets to the packet status data
			mReceivedPackets.add(packetsReceived);
			
			// Add the number of received packets to the counter
			mPacketsReceived += packetsReceived;
			mTotalPackets += packetsReceived;
			
			// Update the throughput
			updateThroughput();
		}
	}
	
	/** Add the packets dropped */
	public void addPacketsDropped(int packetsDropped)
	{
		checkTimeLimit();
		
		if (mIsStarted) {
			// Add the number of dropped packets to the packet status data
			mDroppedPackets.add(packetsDropped);
			
			// Add the number of dropped packets to the counter
			mPacketsDropped += packetsDropped;
			mTotalPackets += packetsDropped;
			
			// Update the throughput
			updateThroughput();
		}
	}

	/** Update the packet throughput */
	private void updateThroughput()
	{
		if (mTotalPackets > 0) {
    		mThroughput = (int)((100 * mPacketsReceived) / (mTotalPackets));
    	} else {
    		mThroughput = 0;
    	}
	}
	
	/** Start session */
	public void start() {
		// Set the start flag
		mIsStarted = true;
		
		// Set the start time
		mStartTime = SystemClock.elapsedRealtime();
	}

	/** Stop session */
	public void stop() {
		// Clear the start flag
		mIsStarted = false;
	}
	
	/** Clear session */
	public void clear() {
		// Clear counters
		mPacketsReceived = 0;
		mPacketsDropped = 0;
		mTotalPackets = 0;
		mThroughput = 0;
		
		// Clear arrays
		mRR.clear();
		mBPM.clear();
		mRSSI.clear();
		mReceivedPackets.clear();
		mDroppedPackets.clear();
	}
	
	/** Check time limit */
	public void checkTimeLimit() {
		if (mIsStarted) {
			if (getElapsedTime() >= SESSION_TIMELIMIT_MILLISEC) {
				stop();
			}
		}
	}
	
}
