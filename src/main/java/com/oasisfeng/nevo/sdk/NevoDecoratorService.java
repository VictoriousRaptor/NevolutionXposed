package com.oasisfeng.nevo.sdk;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

public abstract class NevoDecoratorService {
	private static final int MAX_NUM_ARCHIVED = 20;

	public static final String EXTRAS_BIG_CONTENT_VIEW_OVERRIDE = "nevo.bigContentView";
	public static final String EXTRAS_CONTENT_VIEW_OVERRIDE = "nevo.contentView";
	/** Valid constant values for {@link android.app.Notification#EXTRA_TEMPLATE} */
	public static final String TEMPLATE_BIG_TEXT	= "android.app.Notification$BigTextStyle";
	public static final String TEMPLATE_BIG_PICTURE	= "android.app.Notification$BigPictureStyle";
	public static final String TEMPLATE_CUSTOM		= "android.app.Notification$DecoratedCustomViewStyle";
	public static final String TEMPLATE_INBOX		= "android.app.Notification$InboxStyle";
	public static final String TEMPLATE_MEDIA		= "android.app.Notification$MediaStyle";
	public static final String TEMPLATE_MESSAGING	= "android.app.Notification$MessagingStyle";

	private static final String TAG = "NevoDecoratorService";

	private static volatile Context appContext, packageContext;
	private static volatile NotificationListenerService mNLS;
	private static volatile NotificationManager mNM;

	public static Context getAppContext() {
		if (appContext == null)
			appContext = android.app.AndroidAppHelper.currentApplication().getApplicationContext();
		return appContext;
	}

	public static void setAppContext(Context context) {
		appContext = context;
	}

	public static void setNLS(NotificationListenerService nls) {
		mNLS = nls;
	}

	public static void setNM(NotificationManager nm) {
		mNM = nm;
	}

	protected static Context getPackageContext() {
		if (packageContext == null)
			packageContext = getPackageContext(BuildConfig.APPLICATION_ID);
		return packageContext;
	}

	protected static Context getPackageContext(String packageName) {
		try {
			return getAppContext().createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
		} catch (PackageManager.NameNotFoundException ig) { return null; }
	}

	protected static PackageManager getPackageManager() {
		return getAppContext().getPackageManager();
	}

	protected static String getString(int key) {
		return getPackageContext().getString(key);
	}

	public static void setChannelId(Notification n, String channelId) {
		XposedHelpers.setObjectField(n, "mChannelId", channelId);
	}

	public static void setGroup(Notification n, String groupKey) {
		XposedHelpers.setObjectField(n, "mGroupKey", groupKey);
	}

	public static void setGroupAlertBehavior(Notification n, int behavior) {
		XposedHelpers.setIntField(n, "mGroupAlertBehavior", behavior);
	}

	public static void setSortKey(Notification n, String sortKey) {
		XposedHelpers.setObjectField(n, "mSortKey", sortKey);
	}

	public static void setActions(Notification n, Action... actions) {
		XposedHelpers.setObjectField(n, "actions", actions);
	}

	private static final Map<Integer, LinkedList<Notification>> map = new WeakHashMap<>();

	protected static void cache(final int id, final Notification n) {
		Log.d(TAG, "cache id " + id);
		LinkedList<Notification> queue = map.get(id);
		if (queue == null) {
			queue = new LinkedList<>();
			map.put(id, queue);
		}
		queue.add(n);
		Log.d(TAG, "cache queue " + queue);
		if (queue.size() > MAX_NUM_ARCHIVED) queue.remove();
	}

	protected static List<Notification> getArchivedNotifications(int key) {
		LinkedList<Notification> queue = map.get(key);
		return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
	}

	protected static Notification getArchivedNotification(int key) {
		LinkedList<Notification> queue = map.get(key);
		return queue.getLast();
	}

	protected static boolean hasArchivedNotifications(int key) {
		return map.containsKey(key);
	}

	/**
	 * 在应用进程中执行的通知预处理，某些功能（NotificationChannel等）在此实现。
	 */
	// public static class LocalDecorator {
	// 	protected final String prefKey;
	// 	private boolean disabled;

	// 	protected LocalDecorator(String prefKey) { this.prefKey = prefKey; }

	// 	public boolean isDisabled() { return disabled; }
	// 	public void setDisabled(boolean disabled) { this.disabled = disabled; }
	
	// 	@Keep public void onCreate(SharedPreferences pref) {
	// 		this.disabled = !pref.getBoolean(prefKey  + ".enabled", true);
	// 		if (BuildConfig.DEBUG) Log.d(TAG, prefKey + ".disabled " + this.disabled);
	// 	}
	
	// 	@Keep public void onDestroy() {}
	
	// 	@Keep public Decorating apply(NotificationManager nm, String tag, int id, Notification n) {
	// 		return Decorating.Unprocessed;
	// 	}
	// }

	protected final String prefKey;
	private boolean disabled;

	public NevoDecoratorService() {
		this.prefKey = getClass().getSimpleName();
	}

	public boolean isDisabled() { return disabled; }
	public void setDisabled(boolean disabled) { this.disabled = disabled; }

	@Keep public void onCreate(SharedPreferences pref) {
		this.disabled = !pref.getBoolean(prefKey  + ".enabled", true);
		if (BuildConfig.DEBUG) Log.d(TAG, prefKey + ".disabled " + this.disabled);
	}

	@Keep public void onDestroy() {}

	@Keep public Decorating apply(NotificationManager nm, String tag, int id, Notification n) {
		return Decorating.Unprocessed;
	}

	protected final void cancelNotification(int id) {
		Log.d(TAG, "cancelNotification " + mNM + " " + id);
		if (mNM != null) mNM.cancel(null, id);
	}

	protected final void recastNotification(final int id, final Notification n) {
		Log.d(TAG, "recastNotification " + mNM + " " + n.extras.getCharSequence(Notification.EXTRA_TITLE));
		if (mNM != null) mNM.notify(null, id, n);
	}
}