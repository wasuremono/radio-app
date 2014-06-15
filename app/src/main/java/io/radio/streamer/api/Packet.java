package io.radio.streamer.api;

import org.json.JSONException;
import org.json.JSONObject;

public class Packet {
	public Main main;
	public Meta meta;
	
	public Packet(JSONObject packet) throws JSONException
	{
		this.main = new Main(packet.getJSONObject("main"));
		this.meta = new Meta(packet.getJSONObject("meta"));
	}

    public String toString() {
        return main.toString();
    }
}