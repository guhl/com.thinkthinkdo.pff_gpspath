package com.thinkthinkdo.pffgpspath;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

public class MapsHelper {

	/**
	 * Calculate the bearing between two points.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static float bearing(LatLng p1, LatLng p2) {
		float[] result = new float[2];
		Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, result);
		return result[1];
	}

	/**
	 * Calculate the distance between two points.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static double distance(LatLng p1, LatLng p2) {
		float[] result = new float[1];
		Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, result);
		return result[0];
	}
	
}
