package io.radio.streamer.api;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;

import javax.net.ssl.HttpsURLConnection;

import io.radio.streamer.R;


public class API {
    private static final String BASE_URL = "https://r-a-d.io/api";
    private Activity activity;

    public API(String url, Activity instance) {
        this.activity = instance;

        try {
            AsyncTask task = new UpdateAPITask().execute(getAbsoluteUrl(url));

        } catch (Exception e) {
            //
        }


    }

    private static String getAbsoluteUrl(String url) {
        return BASE_URL + url;
    }

    private class UpdateAPITask extends AsyncTask<String, Void, Packet> {
        @Override
        protected Packet doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                InputStream input = connection.getInputStream();

                String response = IOUtils.toString(input, "UTF-8");
                JSONObject json = new JSONObject(response);

                return new Packet(json);
            } catch (Exception e) {
                Log.e("api", "exception", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Packet packet) {
            if (packet == null) {
                Log.d("api", "Packet was null");
                return;
            }
            // we have access to the UI thread now
            ((TextView) activity.findViewById(R.id.main_SongName)).setText(packet.main.title);
            ((TextView) activity.findViewById(R.id.main_ArtistName)).setText(packet.main.artist);
            ((TextView) activity.findViewById(R.id.main_DjName)).setText(packet.main.dj.name);

            try {
                ((TextView) activity.findViewById(R.id.main_SongLength))
                        .setText(Util.formatSongLength(packet.main.progress, packet.main.length));
                ((TextView) activity.findViewById(R.id.main_ListenerCount))
                        .setText(packet.main.listeners);
            } catch (Exception e) {
                // can't seem to find these...
            }
        }
    }
}
