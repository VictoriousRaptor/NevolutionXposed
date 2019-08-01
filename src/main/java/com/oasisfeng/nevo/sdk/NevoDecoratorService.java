package com.oasisfeng.nevo.sdk;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

public abstract class NevoDecoratorService {
	/** Valid constant values for {@link android.app.Notification#EXTRA_TEMPLATE} */
	public static final String TEMPLATE_BIG_TEXT	= "android.app.Notification$BigTextStyle";
	public static final String TEMPLATE_INBOX		= "android.app.Notification$InboxStyle";
	public static final String TEMPLATE_BIG_PICTURE	= "android.app.Notification$BigPictureStyle";
	public static final String TEMPLATE_MEDIA		= "android.app.Notification$MediaStyle";
	public static final String TEMPLATE_MESSAGING	= "android.app.Notification$MessagingStyle";

	private static final String TAG = "NevoDecoratorService";

	public static interface RecastAction {
		public void recast(StatusBarNotification sbn);
	}

	private static volatile Context appContext;
	private static volatile NotificationListenerService mNLS;

	protected static Context getAppContext() {
		return appContext;
	}

	public static void setAppContext(Context context) {
		appContext = context;
	}

	public static void setNLS(NotificationListenerService nls) {
		mNLS = nls;
	}

	protected static Context getPackageContext() {
		try {
			return appContext.createPackageContext("com.oasisfeng.nevo.xposed", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
		} catch (PackageManager.NameNotFoundException ig) { return null; }
	}

	protected static PackageManager getPackageManager() {
		return getAppContext().getPackageManager();
	}

	protected static String getString(int key) {
		return getAppContext().getString(key);
	}

	@Keep public void onCreate() {}
	@Keep public void onDestroy() {}

	@Keep public void apply(final StatusBarNotification evolving) {}
	@Keep public void onNotificationRemoved(final StatusBarNotification evolving, final int reason) {
		Log.d(TAG, "onNotificationRemoved(" + evolving + ", " + reason + ")");
		onNotificationRemoved(evolving.getKey(), reason);
	}
	@Keep public void onNotificationRemoved(final String key, final int reason) {}

	protected final void cancelNotification(String key) {
		Log.d(TAG, "cancelNotification " + key);
		if (mNLS != null) mNLS.cancelNotification(key);
	}

	protected final void recastNotification(final StatusBarNotification sbn) {
		Log.d(TAG, "recastNotification " + sbn + " " + mNLS);
		if (mNLS != null) mNLS.onNotificationPosted(sbn, null);
	}
}