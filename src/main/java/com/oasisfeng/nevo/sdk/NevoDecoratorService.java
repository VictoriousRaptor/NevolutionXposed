package com.oasisfeng.nevo.sdk;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

public abstract class NevoDecoratorService {
	/** Valid constant values for {@link android.app.Notification#EXTRA_TEMPLATE} */
	public static final String TEMPLATE_BIG_TEXT	= "android.app.Notification$BigTextStyle";
	public static final String TEMPLATE_INBOX		= "android.app.Notification$InboxStyle";
	public static final String TEMPLATE_BIG_PICTURE	= "android.app.Notification$BigPictureStyle";
	public static final String TEMPLATE_MEDIA		= "android.app.Notification$MediaStyle";
	public static final String TEMPLATE_MESSAGING	= "android.app.Notification$MessagingStyle";

	private static volatile Context appContext;

	protected static Context getAppContext() {
		return appContext;
	}

	public static void setAppContext(Context context) {
		appContext = context;
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
	@Keep public void onNotificationRemoved(final StatusBarNotification evolving, final int reason) {}

	protected final void recastNotification(final String key, final @Nullable Bundle fillInExtras) {
		Log.d(TAG, "recastNotification");
	}
}