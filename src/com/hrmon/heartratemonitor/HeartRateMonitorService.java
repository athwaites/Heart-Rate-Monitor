package com.hrmon.heartratemonitor;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class HeartRateMonitorService extends Service {
	
    private static final String TAG = "HRMon - Service";
    
    public class LocalBinder extends Binder
    {
        public ConnectionManager getManager()
        {
            return mManager;
        }
        
        public MonitorSession getSession()
        {
            return mSession;
        }
    }
    
    private final LocalBinder mBinder = new LocalBinder();

    public static final int NOTIFICATION_ID = 1;
    
    private ConnectionManager mManager;
    
    private MonitorSession mSession;

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "First Client bound.");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent)
    {
        Log.i(TAG, "Client rebound");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Log.i(TAG, "All clients unbound.");
        // TODO Auto-generated method stub
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "Service created.");
        super.onCreate();
        mManager = new ConnectionManager();
        mManager.start(this);
        
        mSession = new MonitorSession();
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        Notification notification = new Notification(R.drawable.ic_notification, getString(R.string.Notify_Started),
                System.currentTimeMillis());
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, HeartRateMonitorActivity.class),
                PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(this, getString(R.string.app_name),
                getString(R.string.Notify_Started_Body), pi);
        this.startForeground(NOTIFICATION_ID, notification);
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy()
    {
    	mSession.stop();
    	mSession = null;
    	
        mManager.setCallbacks(null);
        mManager.shutDown();
        mManager = null;
        super.onDestroy();
        Log.i(TAG, "Service destroyed.");
    }
    
}
