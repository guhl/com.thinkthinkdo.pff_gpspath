/*
	PFF-GPSPath: map based tool for the PFF enabled Android to set and 
	             simulate the spoofed location.
    It uses the MapQuest elevation and routing service to calculate elevation
    and routing information. Map data is based on  OpenStreetMap 	              
	 
	Copyright (C) 2013-2014 Guhl
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.thinkthinkdo.pffgpspath;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.SortedMap;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.mapquest.android.maps.DefaultItemizedOverlay;
import com.mapquest.android.maps.ItemizedOverlay;
import com.mapquest.android.maps.LineOverlay;
import com.mapquest.android.maps.MapActivity;
import com.mapquest.android.maps.MapView.MapViewEventListener;
import com.mapquest.android.maps.Overlay;
import com.mapquest.android.maps.Overlay.OverlayTouchEventListener;
import com.mapquest.android.maps.OverlayItem;
import com.mapquest.android.maps.RouteManager;
import com.mapquest.android.maps.RouteResponse;
import com.mapquest.android.maps.ServiceResponse.Info;
import com.mapquest.android.maps.MapView;
import com.mapquest.android.maps.GeoPoint;

public class PffGPSPathActivity extends MapActivity
	implements OnSeekBarChangeListener, ServiceConnection, Observer
	{

	final public int ABOUT = 0;
	
	private static final String LOGTAG = "PffGPSPathActivity";
    private static final GeoPoint EVEREST = new GeoPoint(27.988056, 86.925278);
    
    private MapView mMap;

    private OverlayItem mMarker;
    private OverlayItem mPffMarker;
    private SortedMap<String,OverlayItem> mMarkers = new TreeMap<String,OverlayItem>();
    
    private DefaultItemizedOverlay mPffOverlay;
    private DefaultItemizedOverlay mRouteOverlay;
    private LineOverlay mLineOverlay;

    private TextView mTopText;
    private PackageManager mPm;
    private GeoPoint mPos;
    private GeoPoint mLastPosSet = new GeoPoint(0.0,0.0);
    private GeoPoint mLastClickPos = new GeoPoint(0.0,0.0);
    private ArrayList<GeoPoint> mRoute = new ArrayList<GeoPoint>();
    
    private Messenger mServiceMessenger = null;
    boolean mIsBound;
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    private ServiceConnection mConnection = this;
    
    private SeekBar mSpeedBar;
    private static final int SPEED_MAX = 50;
    private static final int SPEED_DEF = 1;
    private int mSpeed;
    
    private OpenElevationService mOpenElevationService = new OpenElevationService();
    private RouteManager mRouteManager;

    @Override
    public boolean isRouteDisplayed() {
      return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPm = getApplicationContext().getPackageManager();
        mPos = this.getPffLocation();
    	setContentView(R.layout.map_marker);
        mTopText = (TextView) findViewById(R.id.top_text);
        mMarkers = new TreeMap<String,OverlayItem>();

        mSpeedBar = (SeekBar) findViewById(R.id.speedSeekBar);
        mSpeedBar.setMax(SPEED_MAX);
        mSpeedBar.setProgress(SPEED_DEF);
        mSpeed = SPEED_DEF;

        automaticBind();
        
        setUpMapIfNeeded();
        mOpenElevationService.addObserver(this);
        
    	mRouteManager=new RouteManager(this, "Fmjtd%7Cluub2dur2g%2Cb5%3Do5-9u2xu6");
    	mRouteManager.setDebug(true);
    	mRouteManager.setRouteCallback(new RouteManager.RouteCallback() {
			@Override
			public void onError(RouteResponse routeResponse) {
				Info info=routeResponse.info;
				int statusCode=info.statusCode;
				
				StringBuilder message =new StringBuilder();
				message.append("Unable to create route.\n")
					.append("Error: ").append(statusCode).append("\n")
					.append("Message: ").append(info.messages);
				Toast.makeText(getApplicationContext(), message.toString(), Toast.LENGTH_LONG).show();
			}

			@Override
			public void onSuccess(RouteResponse routeResponse) {
				String shapePoints = "";
				List<GeoPoint> positions = new ArrayList<GeoPoint>();
				if (routeResponse.serviceResponse != null) {
					try {
						JSONObject route = routeResponse.serviceResponse.getJSONObject("route");
						if (route!=null) {
							JSONObject shape = route.getJSONObject("shape");
							if (shape!=null) {
								shapePoints = shape.getString("shapePoints");
								positions = decompressShapePoints(shapePoints);
							}
						}
					} catch (JSONException e){
						e.printStackTrace();
					}
				}
				if (!positions.isEmpty()){
					Toast.makeText(getApplicationContext(), R.string.route_not_empty, Toast.LENGTH_LONG).show();
			        mRoute = new ArrayList<GeoPoint>();
					for(GeoPoint pos : positions) {
						mRoute.add(pos);
					}
		    		mLineOverlay.setData(mRoute);
		    		mMap.invalidate();
				} else {
					Toast.makeText(getApplicationContext(), R.string.route_empty, Toast.LENGTH_LONG).show();
				}
			}
		});
        
    }
    
    private List<GeoPoint> decompressShapePoints(String encoded) {
    	List<GeoPoint> positions = new ArrayList<GeoPoint>();
    	   double precision = Math.pow(10, -6);
    	   int len = encoded.length(), index=0;
    	   double lat=0.0, lng = 0.0;
    	   while (index < len) {
    	      int b, shift = 0, result = 0;
    	      do {
    	         b = encoded.charAt(index++) - 63;
    	         result |= (b & 0x1f) << shift;
    	         shift += 5;
    	      } while (b >= 0x20);
    	      int dlat = ((result & 1)!=0 ? ~(result >> 1) : (result >> 1));
    	      lat += dlat;
    	      shift = 0;
    	      result = 0;
    	      do {
    	         b = encoded.charAt(index++) - 63;
    	         result |= (b & 0x1f) << shift;
    	         shift += 5;
    	      } while (b >= 0x20);
    	      int dlng = ((result & 1)!=0 ? ~(result >> 1) : (result >> 1));
    	      lng += dlng;
    	      GeoPoint pos = new GeoPoint(lat * precision, lng * precision);
    	      positions.add(pos);
    	   }
    	   return positions;
    	}    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0,ABOUT,0,"About");
    	return true;
    }   
    
    public boolean onOptionsItemSelected (MenuItem item){
    	switch (item.getItemId()) {
    		case ABOUT:
	    	AboutDialog about = new AboutDialog(this);
	    	about.setTitle(R.string.about_title);
	    	about.show();
	    	break;
    	}
    	return true;
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
            mMap = (MapView) findViewById(R.id.map);
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        // Add markers to the map.
    	List<Overlay> overlays = mMap.getOverlays();
    	overlays.clear();

    	MapGestureListener mapGestureListener = new MapGestureListener();
    	MapGestureDetectorOverlay mapGestureDetectorOverlay = new MapGestureDetectorOverlay(mapGestureListener);
    	overlays.add(mapGestureDetectorOverlay);

        Drawable icon = getResources().getDrawable(R.drawable.location_marker);
        mRouteOverlay = new DefaultItemizedOverlay(icon);       
    	mRouteOverlay.setTapListener(new MapOverlayTapListener());
    	overlays.add(mRouteOverlay);

        Drawable pfficon = getResources().getDrawable(R.drawable.pff_marker);
        mPffOverlay = new DefaultItemizedOverlay(pfficon);       
    	overlays.add(mPffOverlay);
    	
        TouchOverlay touchOverlay = new TouchOverlay();
        mMap.getOverlays().add(touchOverlay);
        
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        mLineOverlay = new LineOverlay(paint);
        mMap.getOverlays().add(mLineOverlay);

        mMap.getController().setZoom(12);
        mMap.getController().setCenter(mPos);

        addMarkersToMap();

        mSpeedBar.setOnSeekBarChangeListener(this);

        // Pan to see all markers in view. (TODO: implement this)
        // Cannot zoom to bounds until the map has a size.
        final MapView mapView = (MapView) findViewById(R.id.map);
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
//                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mPos, 11));
                }
            });
        }
    }

    private void addMarkersToMap() {
        // Uses a colored icon.
    	mPffMarker = new OverlayItem(mPos,"PFF","");
    	mPffOverlay.addItem(mPffMarker);
    	if (mMarkers==null || mMarkers.isEmpty()) {
    		mMarker = new OverlayItem(mPos,"S","");
	        mMarkers.put("0",mMarker);
    	} else {
    		int i = 0;
    		SortedMap<String,OverlayItem> tmpMarkers = new TreeMap<String,OverlayItem>();
    		for(Map.Entry<String,OverlayItem> entry : mMarkers.entrySet()) {
    	        String titel = entry.getValue().getTitle();
    	        if (titel!=null && titel.compareTo("S")==0){
    	        	mRouteOverlay.addItem(entry.getValue());
    	        } else {
    	        	mRouteOverlay.addItem(entry.getValue());
    	        }
    	        tmpMarkers.put(entry.getKey(),mMarker);
    		}
    		mMarkers = tmpMarkers;
    	}
		if (mRoute.isEmpty()){
    		for(Map.Entry<String,OverlayItem> entry : mMarkers.entrySet()) {
    			mRoute.add(entry.getValue().getPoint());		
    		}
		} 
		mLineOverlay.setData(mRoute);
    }    

    private boolean checkReady() {
        if (mMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
   
    /** Called when the Clear button is clicked. */
    public void onClearMap(View view) {
        if (!checkReady()) {
            return;
        }
        mMarkers.clear();
        mRoute.clear();
        mPos = this.getPffLocation();
        setUpMap();
        mMap.invalidate();
    }

    /** Called when the Reset button is clicked. */
    public void onResetMap(View view) {
        if (!checkReady()) {
            return;
        }
        mPos = this.getPffLocation();
        setUpMap();
        mMap.invalidate();
    }
    
    public void onRoute(View view) {
    	java.util.List<java.lang.String> locations = new ArrayList<String>();

		for(Map.Entry<String,OverlayItem> entry : mMarkers.entrySet()) {
			GeoPoint pos = entry.getValue().getPoint();
			String ns = pos.getLatitude()+","+pos.getLongitude();
			locations.add(ns);
		}
    	
		mRouteManager.createRoute(locations);
        Toast.makeText(this, R.string.route_wait, Toast.LENGTH_LONG).show();
    }
    
    public void onStartMap(View view) {
        if (!checkReady()) {
            return;
        }
        Toast.makeText(getBaseContext(), getString(R.string.start_map) +mMarker.getPoint(), Toast.LENGTH_SHORT).show();
        mMap.getController().setCenter(mMarker.getPoint());
        startPffPaths(mSpeed, false);
    }

    public void onStopMap(View view) {
        if (!checkReady()) {
            return;
        }
        Toast.makeText(getBaseContext(), R.string.stop_map, Toast.LENGTH_SHORT).show();
		Intent i = new Intent(PffGPSPathActivity.this, PffGPSPathService.class);
		i.putExtra("action", "com.thinkthinkdo.pffgpspath.stop");
		startService(i);
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

		ArrayList<String> path = new ArrayList<String>();
		if (mRoute.isEmpty()) { 
			for(Map.Entry<String,OverlayItem> entry : mMarkers.entrySet()) {
				GeoPoint pos = entry.getValue().getPoint();
				String ns = pos.getLatitude()+":"+pos.getLongitude();
				path.add(ns);
			}
		} else {
			for(GeoPoint pos : mRoute) {
				String ns = pos.getLatitude()+":"+pos.getLongitude();
				path.add(ns);
			}			
		}
		i.putStringArrayListExtra("locations", path);

		startService(i);
		automaticBind();
	}
    
    GeoPoint getPffLocation() {
    	GeoPoint pos = null;
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
	    		pos = new GeoPoint((Double)locGetLat.invoke(locBean),(Double)locGetLng.invoke(locBean));
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
		if ( mLastPosSet.getLatitude() != 0 && mLastPosSet.getLongitude() != 0 ) {
		    try {
		        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
		        Object locBean = locationBeanClass.newInstance();
		        Method locSetLat = locationBeanClass.getMethod("setLatitude", Double.class);
		        Method locSetLng = locationBeanClass.getMethod("setLongitude", Double.class);
		        Method locSetAlt = locationBeanClass.getMethod("setAltitude", Double.class);
	        	locSetLat.invoke(locBean, mLastPosSet.getLatitude());
	        	locSetLng.invoke(locBean, mLastPosSet.getLongitude());
	    		locSetAlt.invoke(locBean, alt);
	        	Method pffSetLocationMethod = mPm.getClass().getMethod("pffSetLocation", locationBeanClass);
	        	pffSetLocationMethod.invoke(mPm, locBean);
				Log.i(LOGTAG, "setPffLocation: lat="+mLastPosSet.getLatitude()+", lng="+mLastPosSet.getLongitude()+", alt="+alt);	
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
	
	void setPffLocation(GeoPoint pos) {
	    mLastPosSet = pos;
		// start the Elevation Thread
    	mOpenElevationService.getElevation(pos);
	    try {
	        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
	        Object locBean = locationBeanClass.newInstance();
	        Method locSetLat = locationBeanClass.getMethod("setLatitude", Double.class);
	        Method locSetLng = locationBeanClass.getMethod("setLongitude", Double.class);
	        Method locSetAlt = locationBeanClass.getMethod("setAltitude", Double.class);
        	
        	locSetLat.invoke(locBean, pos.getLatitude());
        	locSetLng.invoke(locBean, pos.getLongitude());
        	// if we have a last elevation -> use it
	    	if (mOpenElevationService.elevation!=Double.NaN)
	    		locSetAlt.invoke(locBean, mOpenElevationService.elevation);
        	// update the location
        	Method pffSetLocationMethod = mPm.getClass().getMethod("pffSetLocation", locationBeanClass);
        	pffSetLocationMethod.invoke(mPm, locBean);
			Log.i(LOGTAG, "setPffLocation: lat="+pos.getLatitude()+", lng="+pos.getLongitude()+", alt="+mOpenElevationService.elevation);	
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
                    	    GeoPoint loc = new GeoPoint(lat,lng);
                    	    DecimalFormat f = new DecimalFormat("##.000000");  
                    		mTopText.setText("Running - Pos: " + f.format(lat)+"; "+f.format(lng));
                    		mPffMarker = new OverlayItem(loc, "PFF", "");
                    		mPffOverlay.clear();
                    		mPffOverlay.addItem(mPffMarker);
                            mMap.getController().setCenter(loc);
                    		mMap.invalidate();
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
    
    private class TouchOverlay extends Overlay {
    	@Override
    	public boolean onTouchEvent(MotionEvent event, final MapView mapView) {
    		if(event.getAction() == MotionEvent.ACTION_DOWN) {
    			final GeoPoint p = mapView.getProjection().fromPixels((int)event.getX(), (int)event.getY());
    			mLastClickPos = p;
    			Log.i(LOGTAG, "onTouchEvent: lat="+p.getLatitude()+", lng="+p.getLongitude());	
    		}
    		return false;
    	}
   	
    }
    
    private class MapGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent event) {
        	if (mMap!=null){
				final GeoPoint p = mMap.getProjection().fromPixels((int)event.getX(), (int)event.getY());
	    		Log.i(LOGTAG, "onLongPress: lat="+p.getLatitude()+", lng="+p.getLongitude());	
        	    DecimalFormat f = new DecimalFormat("##.000000");  
        		mTopText.setText("long pressed, Pos: " + f.format(p.getLatitude())+"; "+f.format(p.getLongitude()));
	    		Toast.makeText(getApplicationContext(), "onLongPress: " + f.format(p.getLatitude())+"; "+f.format(p.getLongitude()), Toast.LENGTH_LONG).show();
	    		int cnt = mMarkers.size();
	    		Log.i(LOGTAG, "onMapLongClick: point="+p+", cnt="+cnt);
	    		mMarker = new OverlayItem(p,"P","");
	    		mMarkers.put(Integer.toString(cnt), mMarker);
	    		mRouteOverlay.addItem(mMarker);
	    		mRoute.add(p);
	    		mLineOverlay.setData(mRoute);
	    		mMap.invalidate();
        	}
        }   	
    }
    
    private class MapOverlayTapListener implements ItemizedOverlay.OverlayTapListener {
		@Override
		public void onTap(GeoPoint pt, MapView mapView) {
			int lastTouchedIndex = mRouteOverlay.getLastFocusedIndex();
			if(lastTouchedIndex>-1){
				OverlayItem marker = mRouteOverlay.getItem(lastTouchedIndex);
				Log.i(LOGTAG, "onTap: marker.getId()"+marker.getTitle()+", pos="+marker.getPoint());
	    	    DecimalFormat f = new DecimalFormat("##.000000");  
	    		mTopText.setText("Setting PFF-Location: " + f.format(marker.getPoint().getLatitude())+"; "+f.format(marker.getPoint().getLongitude()));
	    		Toast.makeText(getApplicationContext(), "Setting PFF-Location: " + f.format(marker.getPoint().getLatitude())+"; "+f.format(marker.getPoint().getLongitude()), Toast.LENGTH_LONG).show();
				
				setPffLocation(marker.getPoint());
			}
		}
    	
    }
}
