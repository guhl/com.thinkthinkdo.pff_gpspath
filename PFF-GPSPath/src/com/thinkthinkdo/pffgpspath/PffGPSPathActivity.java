package com.thinkthinkdo.pffgpspath;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class PffGPSPathActivity extends FragmentActivity 
	implements OnMapLongClickListener, OnMarkerClickListener, OnInfoWindowClickListener, OnMarkerDragListener, 
	           OnSeekBarChangeListener, ServiceConnection, Observer
	{

    private static final String LOGTAG = "PffGPSPathActivity";
    private static final LatLng EVEREST = new LatLng(27.988056, 86.925278);

    private GoogleMap mMap;

    private Marker mMarker;
    private Marker mPffMarker;
    private SortedMap<String,Marker> mMarkers = new TreeMap<String,Marker>();
    private TextView mTopText;
    private PackageManager mPm;
    private LatLng mPos;
    private LatLng mLastPosSet = new LatLng(0.0,0.0);
    private Polyline mMutablePolyline;
    
    private Messenger mServiceMessenger = null;
    boolean mIsBound;
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    private ServiceConnection mConnection = this;
    
    private SeekBar mSpeedBar;
    private static final int SPEED_MAX = 50;
    private static final int SPEED_DEF = 1;
    private int mSpeed;
    
    private OpenElevationService mOpenElevationService = new OpenElevationService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPm = getApplicationContext().getPackageManager();
        mPos = this.getPffLocation();
    	setContentView(R.layout.map_marker);
        mTopText = (TextView) findViewById(R.id.top_text);
        mMarkers = new TreeMap<String,Marker>();

        mSpeedBar = (SeekBar) findViewById(R.id.speedSeekBar);
        mSpeedBar.setMax(SPEED_MAX);
        mSpeedBar.setProgress(SPEED_DEF);
        mSpeed = SPEED_DEF;
        
        automaticBind();
        
        setUpMapIfNeeded();
        mOpenElevationService.addObserver(this);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceMessenger = new Messenger(service);
            mTopText.setText("Attached.");
            try {
                    Message msg = Message.obtain(null, PffGPSPathService.MSG_REGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
            } 
            catch (RemoteException e) {
                    // In this case the service has crashed before we could even do anything with it
            } 
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mServiceMessenger = null;
            mTopText.setText("Disconnected.");
    }
    
    @Override
    protected void onDestroy() {
            super.onDestroy();
            try {
                    doUnbindService();
            } catch (Throwable t) {
                    Log.e(LOGTAG, "Failed to unbind from the service", t);
            }
    }

    @Override
    public void update(Observable observable, Object data) {
    	if (observable.getClass().equals(mOpenElevationService.getClass()))
    		updateElevation((Double)data);
    }
    
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        // Hide the zoom controls as the button panel will cover it.
        mMap.getUiSettings().setZoomControlsEnabled(false);

        // Add markers to the map.
        addMarkersToMap();

        
        mMap.setOnMapLongClickListener(this);
        // Set listeners for marker events.  See the bottom of this class for their behavior.
        mMap.setOnMarkerClickListener(this);
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnMarkerDragListener(this);

        mSpeedBar.setOnSeekBarChangeListener(this);

        // Pan to see all markers in view.
        // Cannot zoom to bounds until the map has a size.
        final View mapView = getSupportFragmentManager().findFragmentById(R.id.map).getView();
        if (mapView.getViewTreeObserver().isAlive()) {
            mapView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @SuppressWarnings("deprecation") // We use the new method when supported
//                @SuppressLint("NewApi") // We check which build version we are using.
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                      mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                      mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mPos, 11));
                }
            });
        }
    }

    private void addMarkersToMap() {
        // Uses a colored icon.
    	if (mMarkers==null || mMarkers.isEmpty()) {
	        mPffMarker = mMap.addMarker(new MarkerOptions()
		    		.position(mPos)
		    		.title("PFF")
		    		.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
		    		.draggable(false));
	        mMarker = mMap.addMarker(new MarkerOptions()
	                .position(mPos)
	                .title("S")
	                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
	        		.draggable(true));
	        mMarkers.put("0",mMarker);
    	} else {
    		int i = 0;
    		SortedMap<String,Marker> tmpMarkers = new TreeMap<String,Marker>();
    		for(Map.Entry<String,Marker> entry : mMarkers.entrySet()) {
    	        String titel = entry.getValue().getTitle();
    	        if (titel!=null && titel.compareTo("S")==0){
	    	        mMarker = mMap.addMarker(new MarkerOptions()
	                .position(entry.getValue().getPosition())
	                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
	                .title("S")
	        		.draggable(true));
    	        } else {
	    	        mMarker = mMap.addMarker(new MarkerOptions()
	                .position(entry.getValue().getPosition())
	                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
	                .title(entry.getKey())
	        		.draggable(true));   	        	
    	        }
    	        tmpMarkers.put(entry.getKey(),mMarker);
    		}
    		mMarkers = tmpMarkers;
            PolylineOptions options = new PolylineOptions();
    		for(Map.Entry<String,Marker> entry : mMarkers.entrySet()) {
                options.add(entry.getValue().getPosition());		
    		}
            Polyline mutablePolyline = mMap.addPolyline(options);
            mMutablePolyline = mutablePolyline;
    	}
    }    

    private boolean checkReady() {
        if (mMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    
    @Override
    public void onMapLongClick(LatLng point) {
    	mTopText.setText("long pressed, point=" + point);
    	int cnt = mMarkers.size();
		Log.i(LOGTAG, "onMapLongClick: point="+point+", cnt="+cnt);
        mMarker = mMap.addMarker(new MarkerOptions()
        .position(point)
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
		.draggable(true));
        mMarkers.put(Integer.toString(cnt), mMarker);
        PolylineOptions options = new PolylineOptions();
		for(Map.Entry<String,Marker> entry : mMarkers.entrySet()) {
            options.add(entry.getValue().getPosition());		
		}
        Polyline mutablePolyline = mMap.addPolyline(options);
        mMutablePolyline = mutablePolyline;
    }
    
    /** Called when the Clear button is clicked. */
    public void onClearMap(View view) {
        if (!checkReady()) {
            return;
        }
        mMap.clear();
        mMutablePolyline = null;
        mMarkers.clear();
        mPos = this.getPffLocation();
        addMarkersToMap();
    }

    /** Called when the Reset button is clicked. */
    public void onResetMap(View view) {
        if (!checkReady()) {
            return;
        }
        // Clear the map because we don't want duplicates of the markers.
        mMap.clear();
        mMutablePolyline = null;
        mPos = this.getPffLocation();
        addMarkersToMap();
    }
    
    public void onStartMap(View view) {
        if (!checkReady()) {
            return;
        }
        Toast.makeText(getBaseContext(), "Starting PFF Path start="+mMarker.getPosition(), Toast.LENGTH_SHORT).show();
        LatLngBounds.Builder boundsbuilder = new LatLngBounds.Builder();
		for(Map.Entry<String,Marker> entry : mMarkers.entrySet()) {
			boundsbuilder.include(entry.getValue().getPosition());
		}
		LatLngBounds bounds = boundsbuilder.build();
		mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
        startPffPaths(mSpeed, false);
    }

    public void onStopMap(View view) {
        if (!checkReady()) {
            return;
        }
        Toast.makeText(getBaseContext(), "Stoping PFF Path", Toast.LENGTH_SHORT).show();
		Intent i = new Intent(PffGPSPathActivity.this, PffGPSPathService.class);
		i.putExtra("action", "com.thinkthinkdo.pffgpspath.stop");
		startService(i);
    }
    
    //
    // Marker related listeners.
    //

    @Override
    public boolean onMarkerClick(final Marker marker) {
        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
		Log.i(LOGTAG, "onMarkerClick: marker.getId()"+marker.getId()+", pos="+marker.getPosition());
		this.setPffLocation(marker.getPosition());
        Toast.makeText(this, "PFF-Location set to"+marker.getPosition(), Toast.LENGTH_SHORT).show();
        return false;
    }
    
    @Override
    public void onInfoWindowClick(Marker marker) {        
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        mTopText.setText("onMarkerDragStart");
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        mTopText.setText("onMarkerDragEnd");
        mMarker = marker;
        if (mMutablePolyline!=null)
        	mMutablePolyline.remove();
        PolylineOptions options = new PolylineOptions();
		for(Map.Entry<String,Marker> entry : mMarkers.entrySet()) {
            options.add(entry.getValue().getPosition());		
		}
        Polyline mutablePolyline = mMap.addPolyline(options);
        mMutablePolyline = mutablePolyline;       
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Don't do anything here.
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Don't do anything here.
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        mTopText.setText("onMarkerDrag.  Current Position: " + marker.getPosition());
        mMarker = marker;
    }
    
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == mSpeedBar) {
        	mSpeed = progress;
            mTopText.setText("Speed set to "+mSpeed+" m/s");
        } 
    }

    void startPffPaths(double MperSec, boolean randomizespeed) {
		Intent i = new Intent(PffGPSPathActivity.this, PffGPSPathService.class);

		i.putExtra("action", "com.thinkthinkdo.pffgpspath.start");
		i.putExtra("MperSec", MperSec);
		i.putExtra("randomizespeed", randomizespeed);

		ArrayList<String> pass = new ArrayList<String>();
		int j=0;
		for(Map.Entry<String,Marker> entry : mMarkers.entrySet()) {
			LatLng pos = entry.getValue().getPosition();
			String ns = pos.latitude+":"+pos.longitude;
			pass.add(ns);
			j++;
		}

		i.putStringArrayListExtra("locations", pass);

		startService(i);
		automaticBind();
	}
    
	LatLng getPffLocation() {
        LatLng pos = null;
        try {
	        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
	        Object locBean = locationBeanClass.newInstance();
	        Method locGetLat = locationBeanClass.getMethod("getLatitude");
	        Method locGetLng = locationBeanClass.getMethod("getLongitude");
	        	Method pffGetLocationMethod = mPm.getClass().getMethod("pffGetLocation");
	        	locBean = (Object) pffGetLocationMethod.invoke(mPm);
	//          String[] spoofed = mPm.getSpoofedPermissions(perm.packageName);
	    	if (locBean==null) {
				Log.i(LOGTAG, "onCreate: locBean==null --> EVEREST");
	    		pos = EVEREST;
	    	} else {
				Log.i(LOGTAG, "onCreate: locBean lat="+locGetLat.invoke(locBean)+", lon="+locGetLng.invoke(locBean));
	    		pos = new LatLng((Double)locGetLat.invoke(locBean),(Double)locGetLng.invoke(locBean));
	    	}
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
        mLastPosSet = pos;
        return pos;
	}

	private void updateElevation(double alt) {
		// if the mLastPosSet is not 0 then update the location
		if ( mLastPosSet.latitude != 0 && mLastPosSet.longitude != 0 ) {
		    try {
		        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
		        Object locBean = locationBeanClass.newInstance();
		        Method locSetLat = locationBeanClass.getMethod("setLatitude", Double.class);
		        Method locSetLng = locationBeanClass.getMethod("setLongitude", Double.class);
		        Method locSetAlt = locationBeanClass.getMethod("setAltitude", Double.class);
	        	locSetLat.invoke(locBean, mLastPosSet.latitude);
	        	locSetLng.invoke(locBean, mLastPosSet.longitude);
	    		locSetAlt.invoke(locBean, alt);
	        	Method pffSetLocationMethod = mPm.getClass().getMethod("pffSetLocation", locationBeanClass);
	        	pffSetLocationMethod.invoke(mPm, locBean);
				Log.i(LOGTAG, "setPffLocation: lat="+mLastPosSet.latitude+", lng="+mLastPosSet.longitude+", alt="+alt);	
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
		}
	}
	
	void setPffLocation(LatLng pos) {
	    mLastPosSet = pos;
		// start the Elevation Thread
    	mOpenElevationService.getElevation(pos);
	    try {
	        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
	        Object locBean = locationBeanClass.newInstance();
	        Method locSetLat = locationBeanClass.getMethod("setLatitude", Double.class);
	        Method locSetLng = locationBeanClass.getMethod("setLongitude", Double.class);
	        Method locSetAlt = locationBeanClass.getMethod("setAltitude", Double.class);
        	
        	locSetLat.invoke(locBean, pos.latitude);
        	locSetLng.invoke(locBean, pos.longitude);
        	// if we have a last elevation -> use it
	    	if (mOpenElevationService.elevation!=Double.NaN)
	    		locSetAlt.invoke(locBean, mOpenElevationService.elevation);
        	// update the location
        	Method pffSetLocationMethod = mPm.getClass().getMethod("pffSetLocation", locationBeanClass);
        	pffSetLocationMethod.invoke(mPm, locBean);
			Log.i(LOGTAG, "setPffLocation: lat="+pos.latitude+", lng="+pos.longitude+", alt="+mOpenElevationService.elevation);	
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
	}

    /**
     * Check if the service is running. If the service is running 
     * when the activity starts, we want to automatically bind to it.
     */
    private void automaticBind() {
    	doBindService();
    }    

    /**
     * Bind this Activity to MyService
     */
    private void doBindService() {
            bindService(new Intent(this, PffGPSPathService.class), mConnection, Context.BIND_AUTO_CREATE);
            mIsBound = true;
            mTopText.setText("Binding.");
    }

    /**
     * Un-bind this Activity to MyService
     */     
    private void doUnbindService() {
            if (mIsBound) {
                    // If we have received the service, and hence registered with it, then now is the time to unregister.
                    if (mServiceMessenger != null) {
                            try {
                                    Message msg = Message.obtain(null, PffGPSPathService.MSG_UNREGISTER_CLIENT);
                                    msg.replyTo = mMessenger;
                                    mServiceMessenger.send(msg);
                            } catch (RemoteException e) {
                                    // There is nothing special we need to do if the service has crashed.
                            }
                    }
                    // Detach our existing connection.
                    unbindService(mConnection);
                    mIsBound = false;
                    mTopText.setText("Unbinding.");
            }
    }
	
    /**
     * Handle incoming messages from MyService
     */
    private class IncomingMessageHandler extends Handler {          
            @Override
            public void handleMessage(Message msg) {
                    // Log.d(LOGTAG,"IncomingHandler:handleMessage");
                    switch (msg.what) {
                    case PffGPSPathService.MSG_SET_LATLNG_VALUE:
                    	    double lat = Double.parseDouble(msg.getData().getString("lat"));
                    	    double lng = Double.parseDouble(msg.getData().getString("lng"));
                    	    LatLng loc = new LatLng(lat,lng);
                    		mTopText.setText("Running - Pos: " + loc);
                    		mPffMarker.setPosition(loc);
                            break;
                    case PffGPSPathService.MSG_SET_STRING_VALUE:
                            String str1 = msg.getData().getString("str1");
                    		mTopText.setText("Running - Pos: " + str1);
                            break;
                    default:
                            super.handleMessage(msg);
                    }
            }
    }       	
}
