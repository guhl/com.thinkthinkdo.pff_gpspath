package com.thinkthinkdo.pffgpspath;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;
import android.widget.Toast;

public class PffGPSPathActivity extends FragmentActivity 
	implements OnMarkerClickListener, OnInfoWindowClickListener, OnMarkerDragListener
	{

    private static final LatLng EVEREST = new LatLng(27.988056, 86.925278);

    private GoogleMap mMap;

    private Marker mMarker;
    private TextView mTopText;
    private PackageManager mPm;
    private LatLng mPos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPm = getApplicationContext().getPackageManager();

        try {
	        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
	        Object locBean = locationBeanClass.newInstance();
	        Method locGetLat = locationBeanClass.getMethod("getLatitude");
	        Method locGetLng = locationBeanClass.getMethod("getLongitude");
	        	Method pffGetLocationMethod = mPm.getClass().getMethod("pffGetLocation");
	        	locBean = (Object) pffGetLocationMethod.invoke(mPm);
	//          String[] spoofed = mPm.getSpoofedPermissions(perm.packageName);
	        
	    	if (locBean==null) {
				Log.i("PffGPSPathActivity", "onCreate: locBean==null --> EVEREST");
	    		mPos = EVEREST;
	    	} else {
				Log.i("PffGPSPathActivity", "onCreate: locBean lat="+locGetLat.invoke(locBean)+", lon="+locGetLng.invoke(locBean));
	    		mPos = new LatLng((Double)locGetLat.invoke(locBean),(Double)locGetLng.invoke(locBean));
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

    	setContentView(R.layout.map_marker);
        mTopText = (TextView) findViewById(R.id.top_text);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
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

        // Add lots of markers to the map.
        addMarkersToMap();

        // Setting an info window adapter allows us to change the both the contents and look of the
        // info window.
//        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter());

        // Set listeners for marker events.  See the bottom of this class for their behavior.
        mMap.setOnMarkerClickListener(this);
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnMarkerDragListener(this);

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
        mMarker = mMap.addMarker(new MarkerOptions()
                .position(mPos)
                .title("PFF")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        		.draggable(true));
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
        mMap.clear();
    }

    /** Called when the Reset button is clicked. */
    public void onResetMap(View view) {
        if (!checkReady()) {
            return;
        }
        // Clear the map because we don't want duplicates of the markers.
        mMap.clear();
        // Refresh the mPos position from the PFF framework
        try {
	        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
	        Object locBean = locationBeanClass.newInstance();
	        Method locGetLat = locationBeanClass.getMethod("getLatitude");
	        Method locGetLng = locationBeanClass.getMethod("getLongitude");
	        	Method pffGetLocationMethod = mPm.getClass().getMethod("pffGetLocation");
	        	locBean = (Object) pffGetLocationMethod.invoke(mPm);
	//          String[] spoofed = mPm.getSpoofedPermissions(perm.packageName);
	        
	    	if (locBean==null) {
				Log.i("PffGPSPathActivity", "onCreate: locBean==null --> EVEREST");
	    		mPos = EVEREST;
	    	} else {
				Log.i("PffGPSPathActivity", "onCreate: locBean lat="+locGetLat.invoke(locBean)+", lon="+locGetLng.invoke(locBean));
	    		mPos = new LatLng((Double)locGetLat.invoke(locBean),(Double)locGetLng.invoke(locBean));
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
        
        addMarkersToMap();
    }
    
    public void onGoMap(View view) {
        if (!checkReady()) {
            return;
        }
        Toast.makeText(getBaseContext(), "Setting PFF Position to"+mMarker.getPosition(), Toast.LENGTH_SHORT).show();
        LatLng mPosition = mMarker.getPosition();
        try {
	        Class locationBeanClass = Class.forName("android.content.pff.LocationBean");
	        Object locBean = locationBeanClass.newInstance();
	        Method locSetLat = locationBeanClass.getMethod("setLatitude", Double.class);
	        Method locSetLng = locationBeanClass.getMethod("setLongitude", Double.class);
	            locSetLat.invoke(locBean, mPosition.latitude);
	            locSetLng.invoke(locBean, mPosition.longitude);
	        	Method pffSetLocationMethod = mPm.getClass().getMethod("pffSetLocation", locationBeanClass);
	        	pffSetLocationMethod.invoke(mPm, locBean);
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
    //
    // Marker related listeners.
    //

    @Override
    public boolean onMarkerClick(final Marker marker) {
        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
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
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        mTopText.setText("onMarkerDrag.  Current Position: " + marker.getPosition());
        mMarker = marker;
    }
    
    
}
