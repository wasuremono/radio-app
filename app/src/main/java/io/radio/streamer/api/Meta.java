package io.radio.streamer.api;

import org.json.JSONException;
import org.json.JSONObject;

public class Meta {
	public int length;
	public int offset;
	public int limit;
	public String stream;
	
	/**
	 * 
	 * @param json
	 * @throws JSONException 
	 */
	public Meta(JSONObject json) throws JSONException
	{
		this.length = json.getInt("length");
		this.offset = json.getInt("offset");
		this.limit = json.getInt("limit");
		this.stream = json.getString("stream");
	}
}
