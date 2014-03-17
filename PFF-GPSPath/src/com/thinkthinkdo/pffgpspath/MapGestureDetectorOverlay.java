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

import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;

import com.mapquest.android.maps.MapView;
import com.mapquest.android.maps.Overlay;

public class MapGestureDetectorOverlay extends Overlay implements OnGestureListener {
	
	 private GestureDetector gestureDetector;
	 private OnGestureListener onGestureListener;

	 public MapGestureDetectorOverlay() {
	  gestureDetector = new GestureDetector(this);
	 }

	 public MapGestureDetectorOverlay(OnGestureListener onGestureListener) {
	  this();
	  setOnGestureListener(onGestureListener);
	 }

	 @Override
	 public boolean onTouchEvent(MotionEvent event, MapView mapView) {
	  if (gestureDetector.onTouchEvent(event)) {
	   return true;
	  }
	  return false;
	 }

	 @Override
	 public boolean onDown(MotionEvent e) {
	  if (onGestureListener != null) {
	   return onGestureListener.onDown(e);
	  }
	  return false;
	 }

	 @Override
	 public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
	   float velocityY) {
	  if (onGestureListener != null) {
	   return onGestureListener.onFling(e1, e2, velocityX, velocityY);
	  }
	  return false;
	 }

	 @Override
	 public void onLongPress(MotionEvent e) {
	  if (onGestureListener != null) {
	   onGestureListener.onLongPress(e);
	  }
	 }

	 @Override
	 public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
	   float distanceY) {
	  if (onGestureListener != null) {
	   onGestureListener.onScroll(e1, e2, distanceX, distanceY);
	  }
	  return false;
	 }

	 @Override
	 public void onShowPress(MotionEvent e) {
	  if (onGestureListener != null) {
	   onGestureListener.onShowPress(e);
	  }
	 }

	 @Override
	 public boolean onSingleTapUp(MotionEvent e) {
	  if (onGestureListener != null) {
	   onGestureListener.onSingleTapUp(e);
	  }
	  return false;
	 }

	 public boolean isLongpressEnabled() {
	  return gestureDetector.isLongpressEnabled();
	 }

	 public void setIsLongpressEnabled(boolean isLongpressEnabled) {
	  gestureDetector.setIsLongpressEnabled(isLongpressEnabled);
	 }

	 public OnGestureListener getOnGestureListener() {
	  return onGestureListener;
	 }

	 public void setOnGestureListener(OnGestureListener onGestureListener) {
	  this.onGestureListener = onGestureListener;
	 }
}
