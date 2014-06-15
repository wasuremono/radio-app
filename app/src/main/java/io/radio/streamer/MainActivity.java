/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.radio.streamer;

import java.security.KeyStore;
import java.util.Locale;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.gesture.GestureOverlayView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import org.apache.http.Header;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLPeerUnverifiedException;

import io.radio.streamer.api.API;
import io.radio.streamer.api.Packet;
import io.radio.streamer.api.Util;
import io.radio.streamer.views.FXView;
import io.radio.streamer.services.RadioService;
import io.radio.streamer.services.RemoteControlReceiver;


public class MainActivity extends Activity {
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mDrawerMenu;

    public static String PREFS_FILENAME = "RADIOPREFS";

    RadioService service;
    private TextView title;
    private TextView artist;
    private TextView dj;
    private ProgressBar songProgressBar;
    private Timer progressTimer;
    private Timer npTimer;
    private ImageView djImage;
    private TextView listeners;
    private TextView songLength;
    private int progress;
    private int length;
    private ImageButton playButton;
    private ImageButton shareButton;
    private ImageButton searchButton;
    private ImageButton faveButton;
    private ViewFlipper viewFlipper;
    private GestureOverlayView gestureOverlay;
    private ScrollView queueScroll;
    private ScrollView lpScroll;
    private FXView fxView;
    private AudioManager audioManager;
    private RemoteControlClient remoteControlClient;
    private int lastDj;

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    OnAudioFocusChangeListener afChangeListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                audioManager.abandonAudioFocus(afChangeListener);
                service.stopPlayer();
            }
        }
    };

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == Util.NPUPDATE) {
                Packet packet = (Packet) msg.obj;
                MainActivity.this.updateNP(packet);
            }
            if (msg.what == Util.PROGRESSUPDATE) {
                progress++;
                songProgressBar.setProgress(progress);
                songLength.setText(Util.formatSongLength(progress, length));
            }
            if (msg.what == Util.MUSICSTART) {
                audioManager
                        .requestAudioFocus(afChangeListener,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN);

                updatePlayButton();
                SharedPreferences sharedPref = PreferenceManager
                        .getDefaultSharedPreferences(getApplicationContext());

                // FxView
                boolean dBGraph = sharedPref
                        .getBoolean("dBGraphEnabled", false);
                boolean wavVis = sharedPref.getBoolean("waveVisEnabled", false);
                if (dBGraph || wavVis)
                    fxView.startFx(service.getAudioStreamId(), dBGraph, wavVis);
            }
            if (msg.what == Util.MUSICSTOP) {
                updatePlayButton();
                audioManager.abandonAudioFocus(afChangeListener);
                fxView.stopFx();
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Allow keys to change volume without playing
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        audioManager = (AudioManager) getApplicationContext().getSystemService(
            Context.AUDIO_SERVICE);

        // Initialize Remote Controls if SDK Version >=14
        initializeRemoteControls();

        initializeVariables();
        initializeSideBar();

        updateApiData();

        // first page in the menu (homepage) on first load
        if (savedInstanceState == null) {
            selectItem(0);
        }

        // Get the fxView
        fxView = (FXView) findViewById(R.id.audioFxView);

        Timer apiTimer = new Timer();
        apiTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    updateApiData();
                } catch (Exception e) {
                    Log.e("api", "exception", e);
                }
            }
        }, 0, 10000);

    }

    private void updateApiData() {
        API api = new API("/", this);
    }

    private void initializeVariables() {
        // Find and get all the layout items
        title = (TextView) findViewById(R.id.main_SongName);
        artist = (TextView) findViewById(R.id.main_ArtistName);
        dj = (TextView) findViewById(R.id.main_DjName);
        djImage = (ImageView) findViewById(R.id.main_DjImage);
        songProgressBar = (ProgressBar) findViewById(R.id.main_SongProgress);
        listeners = (TextView) findViewById(R.id.main_ListenerCount);
        songLength = (TextView) findViewById(R.id.main_SongLength);
    }

    private void initializeSideBar() {
        // MenuBar
        mTitle = mDrawerTitle = getTitle();
        mDrawerMenu = getResources().getStringArray(R.array.app_menu);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, mDrawerMenu));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
        ) {
            public void onDrawerClosed(View view) {
                getActionBar().setTitle(mTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle(mDrawerTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    @TargetApi(14)
    private void initializeRemoteControls() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            ComponentName eventReceiver = new ComponentName(getPackageName(),
                    RemoteControlReceiver.class.getName());
            audioManager.registerMediaButtonEventReceiver(eventReceiver);
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(eventReceiver);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(
                    getApplicationContext(), 0, mediaButtonIntent, 0);
            remoteControlClient = new RemoteControlClient(mediaPendingIntent);
            remoteControlClient
                    .setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            remoteControlClient
                    .setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                            | RemoteControlClient.FLAG_KEY_MEDIA_STOP
                            | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE);
            audioManager.registerRemoteControlClient(remoteControlClient);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void updatePlayButton() {
        if (RadioService.currentlyPlaying) {
            playButton.setImageResource(R.drawable.av_stop);
        } else {
            playButton.setImageResource(R.drawable.av_play);
        }
    }

    @TargetApi(14)
    public void updateRemoteMetadata(String artist, String track) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            MetadataEditor metaEditor = remoteControlClient.editMetadata(true);
            metaEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    track);
            metaEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    artist);
            metaEditor.apply();
        }
        Intent avrcp = new Intent("com.android.music.metachanged");
        avrcp.putExtra("track", track);
        avrcp.putExtra("artist", artist);
        // avrcp.putExtra("album", "album name");
        this.getApplicationContext().sendBroadcast(avrcp);
    }

    private void updateNP(Packet packet) {
        updateRemoteMetadata(Html.fromHtml(packet.main.artist).toString(), Html
                .fromHtml(packet.main.title).toString());

        progress = packet.main.progress;
        length = packet.main.length;
        title.setText(Html.fromHtml(packet.main.title));
        artist.setText(Html.fromHtml(packet.main.artist));
        dj.setText(Html.fromHtml(packet.main.dj.name));
        songProgressBar.setMax(length);
        songProgressBar.setProgress(progress);
        if (lastDj != packet.main.dj.id) {
            lastDj = packet.main.dj.id;
            DJImageLoader imageLoader = new DJImageLoader();
            imageLoader.execute(packet);

        }
        listeners.setText("Listeners: " + packet.main.listeners);
        songLength.setText(Util.formatSongLength(progress, length));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content view
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        menu.findItem(R.id.action_search).setVisible(!drawerOpen);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
         // The action bar home/up action should open or close the drawer.
         // ActionBarDrawerToggle will take care of this.
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle action buttons
        switch(item.getItemId()) {
            case R.id.action_search:
                // create intent to search
                Intent intent = new Intent(Intent.ACTION_SEARCH);

                // catch event that there's no activity to handle intent
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.search_not_available, Toast.LENGTH_LONG).show();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /* The click listener for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    private void selectItem(int position) {
        // update the main content by replacing fragments
        Fragment fragment = new MenuFragment();
        Bundle args = new Bundle();
        args.putString(MenuFragment.ARG_PAGE_ID, mDrawerMenu[position]);
        fragment.setArguments(args);

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        // update selected item and title, then close the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(mDrawerMenu[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getActionBar().setTitle(mTitle);
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /**
     * Fragment that appears in the "content_frame", shows a planet
     */
    public static class MenuFragment extends Fragment {
        public static final String ARG_PAGE_ID = "page_id";

        public MenuFragment() {
            // Empty constructor required for fragment subclasses
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.radio_homepage, container, false);
            return rootView;
        }
    }

    private class DJImageLoader extends AsyncTask<Packet, Void, Void> {
        private Bitmap image;

        @Override
        protected Void doInBackground(Packet... params) {
            Packet pack = params[0];
            URL url;
            try {
                url = new URL(getString(R.string.api_root) + String.format(getString(R.string.api_dj_image), pack.main.dj.id));
                HttpURLConnection conn = (HttpURLConnection) url
                        .openConnection();
                conn.setDoInput(true);
                conn.connect();
                InputStream is = conn.getInputStream();
                image = BitmapFactory.decodeStream(is);
            } catch (Exception e) {
                e.printStackTrace();
                image = null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (image != null) {
                djImage.setImageBitmap(image);
                service.updateNotificationImage(image);
            }
        }

    }
}