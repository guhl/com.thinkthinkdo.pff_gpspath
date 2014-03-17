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

import android.location.Location;

import com.mapquest.android.maps.GeoPoint;

public class MapsHelper {

	/**
	 * Calculate the bearing between two points.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static float bearing(GeoPoint p1, GeoPoint p2) {
		float[] result = new float[2];
		Location.distanceBetween(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude(), result);
		return result[1];
	}

	/**
	 * Calculate the distance between two points.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static double distance(GeoPoint p1, GeoPoint p2) {
		float[] result = new float[1];
		Location.distanceBetween(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude(), result);
		return result[0];
	}
	
}
