package io.radio.streamer.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import io.radio.streamer.api.Util;

public class RemoteControlReceiver extends BroadcastReceiver {
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
			KeyEvent ev = (KeyEvent) intent
					.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (ev.getAction() == KeyEvent.ACTION_DOWN)
				switch (ev.getKeyCode()) {
				case KeyEvent.KEYCODE_MEDIA_STOP:
					RadioService.sendCommand(context, Util.REMOTEMUSICSTOP);
					break;
				case KeyEvent.KEYCODE_MEDIA_PLAY:
					RadioService.sendCommand(context, Util.REMOTEMUSICPLAY);
					break;
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
					RadioService.sendCommand(context, Util.REMOTEMUSICPLAYPAUSE);
					break;
				}
		}

	}
}
