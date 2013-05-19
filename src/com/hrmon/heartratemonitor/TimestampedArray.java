package com.hrmon.heartratemonitor;

import java.util.ArrayList;
import android.os.SystemClock;

public class TimestampedArray<V> {
	private ArrayList<Long> times = new ArrayList<Long>();
	private ArrayList<V> values = new ArrayList<V>();
	private int size = 0;
	
	// Taking the elapsed offset, to ensure that the timestamps are consistent, even if the user changes the system clock
	private static long elapsedOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime();
	
	public void add(V value)
	{
		times.add(elapsedOffset + SystemClock.elapsedRealtime());
		values.add(value);
		size++;
	}
	
	public long getTime(int index)
	{
		if (index < size) {
			return times.get(index);
		}
		
		return 0;
	}
	
	public ArrayList<Long> getTime()
	{
		return times;
	}
	
	public V get(int index)
	{
		if (index < size) {
			return values.get(index);
		}
		
		return null;
	}
	
	public ArrayList<V> get()
	{
		return values;
	}
	
	public int size()
	{
		return size;
	}
	
	public V getLast()
	{
		if (values.size() > 0) {
			return values.get(values.size() - 1);
		}
		
		return null;
	}
	
	public void clear()
	{
		size = 0;
		times.clear();
		values.clear();
	}
	
}
