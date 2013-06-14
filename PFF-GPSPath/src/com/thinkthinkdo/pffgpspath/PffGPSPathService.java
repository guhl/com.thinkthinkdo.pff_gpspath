package com.thinkthinkdo.pffgpspath;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.android.gms.maps.model.LatLng;
import com.thinkthinkdo.pffgpspath.PffGPSPathActivity;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class PffGPSPathService  extends Service {

    private static final String LOGTAG = "PffGPSPathService";
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler()); // Target we publish for clients to send messages to IncomingHandler.
    private List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.

    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_SET_LATLNG_VALUE = 3;
    public static final int MSG_SET_STRING_VALUE = 4;

    private static boolean isRunning = false;

    /**
	 * How quickly to update the location. Lower values will give a better
	 * output but use more CPU in calculations.
	 */
	private static long TIME_BETWEEN_UPDATES_MS = 500;

	/**
	 * Special instance variable that is set whenever this service is running.
	 * Allows easy access to the service without having to use a binder. Only
	 * works from within the same process, however, which is fine for our use.
	 */
	public static PffGPSPathService instance = null;

	UpdateGPSThread currentThread = null;

	@Override
	public IBinder onBind(Intent intent) {
       Log.i(LOGTAG, "onBind");
       return mMessenger.getBinder();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent.getStringExtra("action").equalsIgnoreCase("com.thinkthinkdo.pffgpspath.start")) {
			if (currentThread != null) {
				currentThread.Running = false;
			}

			currentThread = new UpdateGPSThread();

			ArrayList<String> startLocations = intent.getStringArrayListExtra("locations");
			for (String loc : startLocations) {
				Log.i(LOGTAG, "loc="+loc);
				String[] arr = loc.split(":");
				LatLng newloc = new LatLng((Double.parseDouble(arr[0])), (Double.parseDouble(arr[1])));
				currentThread.locations.add(newloc);
			}

			currentThread.MperSec = intent.getDoubleExtra("MperSec", 500);
			currentThread.randomizespeed = intent.getBooleanExtra("randomizespeed", false);
			currentThread.start();
		}

		if (intent.getStringExtra("action").equalsIgnoreCase("com.thinkthinkdo.pffgpspath.stop")) {
			if (currentThread != null) {
				currentThread.Running = false;
				currentThread.interrupt();
				currentThread = null;
				stopSelf();
			}
		}

		return START_STICKY;
	}

    public static boolean isRunning() {
    	return isRunning;
    }
	
	/**
	 * The actual worker thread that will do calculations and update the GPS
	 * location.
	 * 
	 */
	class UpdateGPSThread extends Thread {

		ArrayList<LatLng> locations = new ArrayList<LatLng>();
		double[] distances;
		public boolean Running;
		public double MperSec;
		public boolean randomizespeed;

		private double curLat, curLng;
		private long startTime;
		private float curBearing;

		private double lastMetersTravelled = 0;

		@Override
		public void run() {
			Log.i(LOGTAG, "Starting UpdateGPSThread");
//			createProgressNotification();
			Running = true;

			PackageManager mPm = getPackageManager();

			startTime = System.currentTimeMillis();
			distances = new double[locations.size() - 1];
			for (int i = 0; i < locations.size() - 1; i++) {
				distances[i] = MapsHelper.distance(locations.get(i), locations.get(i + 1));
				Log.i(LOGTAG, "UpdateGPSThread run: after distances[i]="+distances[i]);
			}

			while (Running) {
				calcCurrentPosition();
				Log.i(LOGTAG, "UpdateGPSThread run: after calcCurrentPosition curLat="+curLat+", curLng="+curLng);

		        try {
			        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
			        Object locBean = locationBeanClass.newInstance();
			        Method locSetLat = locationBeanClass.getMethod("setLatitude", Double.class);
			        Method locSetLng = locationBeanClass.getMethod("setLongitude", Double.class);
			        Method locSetAlt = locationBeanClass.getMethod("setAltitude", Double.class);
			            locSetLat.invoke(locBean, curLat);
			            locSetLng.invoke(locBean, curLng);
			        	Method pffSetLocationMethod = mPm.getClass().getMethod("pffSetLocation", locationBeanClass);
			        	pffSetLocationMethod.invoke(mPm, locBean);
			        	// notify the UI
			        	sendLatLngToUI(new LatLng(curLat,curLng));
		        } catch (NoSuchMethodException e) {
		            e.printStackTrace();
		        } catch (IllegalAccessException e) {
		            e.printStackTrace();
		        } catch (InvocationTargetException e) {
		            e.printStackTrace();
		        } catch (InstantiationException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				
				try {
					Thread.sleep(TIME_BETWEEN_UPDATES_MS);
				} catch (Exception e) {
				}
			}
			if (currentThread == this)
				currentThread = null;
			Log.i(LOGTAG, "Ending UpdateGPSThread");
		}

		private void calcCurrentPosition() {
			long nanoSecondsPassed = System.currentTimeMillis() - startTime;
			double metersTravelled = nanoSecondsPassed / 1000.0 * MperSec;
			if (randomizespeed) {
				// Bugged. The intervals are too small and most navigation has a
				// smoothing function which smoothes out this randomization.
				// Algorithm needs to be changed to one that works over large
				// time intervals (10sec+) in order to avoid the randomization
				// being smoothed out.
				double diff = metersTravelled - lastMetersTravelled;
				metersTravelled = lastMetersTravelled + Math.random() * 2.0 * diff;

				lastMetersTravelled = metersTravelled;
			}
			Log.i(LOGTAG, "calcCurrentPosition: MperSec="+MperSec+", metersTravelled="+metersTravelled);
			for (int i = 0; i < locations.size() - 1; i++) {
				if (metersTravelled < distances[i]) {
					double perc = metersTravelled / distances[i];
					Log.i(LOGTAG, "calcCurrentPosition: i="+i+", distances[i]="+distances[i]+", perc="+perc);
					curLat = locations.get(i).latitude;
					curLat -= perc * (locations.get(i).latitude - locations.get(i + 1).latitude);
					curLng = locations.get(i).longitude;
					curLng -= perc * (locations.get(i).longitude - locations.get(i + 1).longitude);
					curBearing = MapsHelper.bearing(locations.get(i), locations.get(i + 1));
					return;
				}
				metersTravelled -= distances[i];
			}

			curLat = locations.get(locations.size() - 1).latitude;
			curLng = locations.get(locations.size() - 1).longitude;
			Running = false;
		}

	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
        isRunning = true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (instance == this)
			instance = null;
		isRunning = false;
	}

    /**
     * Send the data to all clients.
     * @param intvaluetosend The value to send.
     */
    private void sendLatLngToUI(LatLng loc) {
            Iterator<Messenger> messengerIterator = mClients.iterator();            
            while(messengerIterator.hasNext()) {
                    Messenger messenger = messengerIterator.next();
                    try {
                            // Send data as a String
                            Bundle bundle = new Bundle();
                            bundle.putString("lat", Double.toString(loc.latitude));
                            bundle.putString("lng", Double.toString(loc.longitude));
                            Message msg = Message.obtain(null, MSG_SET_LATLNG_VALUE);
                            msg.setData(bundle);
                            messenger.send(msg);

                    } catch (RemoteException e) {
                            // The client is dead. Remove it from the list.
                            mClients.remove(messenger);
                    }
            }
    }
    
    /**
     * Handle incoming messages from MainActivity
     */
    private class IncomingMessageHandler extends Handler { // Handler of incoming messages from clients.
            @Override
            public void handleMessage(Message msg) {
                    Log.d(LOGTAG,"handleMessage: " + msg.what);
                    switch (msg.what) {
                    case MSG_REGISTER_CLIENT:
                            mClients.add(msg.replyTo);
                            break;
                    case MSG_UNREGISTER_CLIENT:
                            mClients.remove(msg.replyTo);
                            break;
                    default:
                            super.handleMessage(msg);
                    }
            }
    }	
}
