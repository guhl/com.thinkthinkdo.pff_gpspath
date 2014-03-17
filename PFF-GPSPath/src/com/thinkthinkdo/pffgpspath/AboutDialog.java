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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.util.Linkify;

import android.graphics.Color;

import android.widget.TextView;

public class AboutDialog extends Dialog{
	private static Context mContext = null;
	public AboutDialog(Context context) {
		super(context);
		mContext = context;
	}

	/**
	 * Standard Android on create method that gets called when the activity initialized.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.about);	
		TextView tv = (TextView)findViewById(R.id.legal_text);
		tv.setText(readRawTextFile(R.raw.legal));
		tv = (TextView)findViewById(R.id.info_text);
		String infoText = readRawTextFile(R.raw.info);
		Spanned info = Html.fromHtml(infoText);
		tv.setText(info);
		tv.setLinkTextColor(Color.BLUE);
		Linkify.addLinks(tv, Linkify.ALL);
	}

	public static String readRawTextFile(int id) {
		InputStream inputStream = mContext.getResources().openRawResource(id);
		InputStreamReader in = new InputStreamReader(inputStream);
		BufferedReader buf = new BufferedReader(in);
		String line;
		StringBuilder text = new StringBuilder();
		try {
		
		while (( line = buf.readLine()) != null) text.append(line);
		} catch (IOException e) {
		return null;
		}
		return text.toString();
	}
}