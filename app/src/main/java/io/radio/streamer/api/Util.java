package io.radio.streamer.api;

import java.util.ArrayList;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.Html;

import io.radio.streamer.objects.Track;

public class Util {
	public static final int NPUPDATE = 0;
	public static final int ACTIVITYCONNECTED = 1;
	public static final int ACTIVITYDISCONNECTED = 2;
	public static final int PROGRESSUPDATE = 3;
    public static final int MUSICSTART = 4;
    public static final int MUSICSTOP = 5;
    
    public static final int REMOTEMUSICSTOP = 1;
    public static final int REMOTEMUSICPLAY = 2;
    public static final int REMOTEMUSICPLAYPAUSE = 3;

    private static Track[] getTracks(JSONArray JSONarray) {
        ArrayList<Track> list = new ArrayList<Track>();
            for (int i = 0; i < JSONarray.length(); i++) {
                JSONArray obj = null;
                String track = "";
                boolean isRequest = false;
                try {
                    obj = (JSONArray) JSONarray.get(i);
                    track = obj.getString(1);
                    isRequest = obj.getInt(2) == 1 ? true : false;
                } catch (Exception e) {}
                String songName = "-";
                String artistName = "-";
                int hyphenPos = track.indexOf(" - ");
                
                if (hyphenPos==-1)
                {
                    songName = track;
                }
                else
                {
                    try {
                    	songName = Html.fromHtml(track.substring(hyphenPos+3)).toString();
                        artistName = Html.fromHtml(track.substring(0,hyphenPos)).toString();
                    } catch (Exception e) {}
                }
                list.add(new Track(songName, artistName, isRequest));
            }
            Object[] array = list.toArray();
            return Arrays.copyOf(array, array.length, Track[].class);
    }
	
	static public String formatSongLength(int progress, int length) {
		StringBuilder sb = new StringBuilder();

		int progMins = progress / 60;
		int progSecs = progress % 60;
		if (progMins < 10)
			sb.append("0");
		sb.append(progMins);
		sb.append(":");
		if (progSecs < 10)
			sb.append("0");
		sb.append(progSecs);

		sb.append(" / ");

		int lenMins = length / 60;
		int lenSecs = length % 60;
		if (lenMins < 10)
			sb.append("0");
		sb.append(lenMins);
		sb.append(":");
		if (lenSecs < 10)
			sb.append("0");
		sb.append(lenSecs);

		return sb.toString();
	}
}