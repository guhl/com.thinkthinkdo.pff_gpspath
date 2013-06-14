package com.thinkthinkdo.pffgpspath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Observable;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.os.AsyncTask;

import com.google.android.gms.maps.model.LatLng;

public class OpenElevationService extends Observable {
	public double elevation = Double.NaN;

	public void getElevation(LatLng location) {
		new GetElevationTask().execute(location);
    }
	
	private class GetElevationTask extends AsyncTask<LatLng, Integer, Double> {
		
		protected Double doInBackground(LatLng... location) {
	         double result = Double.NaN;
	         HttpClient httpClient = new DefaultHttpClient();
	         HttpContext localContext = new BasicHttpContext();
//	         String url = "http://open.mapquestapi.com/elevation/v1/profile?key=Fmjtd|luub2dur2g%2Cb5%3Do5-9u2xu6&shapeFormat=raw"
	         String url = "http://open.mapquestapi.com/elevation/v1/profile?key=Fmjtd%7Cluub2dur2g%2Cb5%3Do5-9u2xu6&shapeFormat=raw"        
	                 + "&latLngCollection=" + location[0].latitude
	                 + "," + location[0].longitude;
	         HttpGet httpGet = new HttpGet(url);
	         try {
	             HttpResponse response = httpClient.execute(httpGet, localContext);
	             HttpEntity entity = response.getEntity();
	             if (entity != null) {
	                 InputStream instream = entity.getContent();
	                 int r = -1;
	                 StringBuffer respStr = new StringBuffer();
	                 while ((r = instream.read()) != -1)
	                     respStr.append((char) r);
	                 String tagOpen = "\"elevationProfile\":";
	                 String tagClose = ",\"info\":";
	                 if (respStr.indexOf(tagOpen) != -1) {
	                     int start = respStr.indexOf(tagOpen) + tagOpen.length();
	                     int end = respStr.indexOf(tagClose);
	                     String elevationProfile = respStr.substring(start, end);
	                     tagOpen = "\"height\":";
	                     tagClose = "}]";
	                     start = elevationProfile.indexOf(tagOpen) + tagOpen.length();
	                     end = elevationProfile.indexOf(tagClose);
	                     String value = elevationProfile.substring(start, end);
	                     result = (double)(Double.parseDouble(value)); 
	                 }
	                 instream.close();
	             }
	         } catch (ClientProtocolException e) {} 
	         catch (IOException e) {}

	         return result;
		}
		
		protected void onPostExecute(Double result) {
			if (result!=Double.NaN) {
				elevation = result;
				triggerObservers();
			}
	    }
		
		private void triggerObservers() {
			setChanged();
			notifyObservers(Double.valueOf(elevation));
		}
	}
}
