package com.notxx.notification;

import android.app.Notification;

import de.robv.android.xposed.XposedHelpers;

public class MIUIFix extends Notification.Style {
	private final Notification.BigTextStyle bigText;
	private final Notification.MessagingStyle messaging;

	public MIUIFix() {
		bigText = (Notification.BigTextStyle)XposedHelpers.newInstance(Notification.BigTextStyle.class);
		messaging = (Notification.MessagingStyle)XposedHelpers.newInstance(Notification.MessagingStyle.class);
	}

	// @Override public RemoteViews makeContentView(boolean increasedHeight) { return bigText.makeContentView(increasedHeight); }
	// @Override public RemoteViews makeBigContentView() { return messaging.makeBigContentView(); }
	// @Override public RemoteViews makeHeadsUpContentView(boolean increasedHeight) { return messaging.makeHeadsUpContentView(increasedHeight); }

}