package com.oasisfeng.nevo.sdk;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
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

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.oasisfeng.nevo.xposed.BuildConfig;

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

	protected interface BindRemoteViews {
		void bind(RemoteViews remoteViews);
	}

	private static volatile Context appContext, packageContext;
	private static volatile NotificationListenerService mNLS;

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

	public static void setId(StatusBarNotification sbn, int id) {
		XposedHelpers.setIntField(sbn, "id", id);
	}

	public static void setNotification(StatusBarNotification sbn, Notification n) {
		XposedHelpers.setObjectField(sbn, "notification", n);
	}

	public static int getOriginalId(StatusBarNotification sbn) {
		return (Integer)XposedHelpers.getAdditionalInstanceField(sbn, "originalId");
	}

	public static void setOriginalId(StatusBarNotification sbn, int id) {
		XposedHelpers.setAdditionalInstanceField(sbn, "originalId", (Integer)id);
	}

	public static String getOriginalKey(StatusBarNotification sbn) {
		return (String)XposedHelpers.getAdditionalInstanceField(sbn, "originalKey");
	}

	public static void setOriginalKey(StatusBarNotification sbn, String key) {
		XposedHelpers.setAdditionalInstanceField(sbn, "originalKey", key);
	}

	public static void setOriginalTag(StatusBarNotification sbn, String tag) {
		XposedHelpers.setAdditionalInstanceField(sbn, "originalTag", tag);
	}

	public static RemoteViews overrideBigContentView(Notification n, RemoteViews remoteViews) {
		n.extras.putParcelable(EXTRAS_BIG_CONTENT_VIEW_OVERRIDE, remoteViews);
		return remoteViews;
	}

	public static RemoteViews overridedBigContentView(Notification n) {
		return n.extras.getParcelable(EXTRAS_BIG_CONTENT_VIEW_OVERRIDE);
	}

	public static RemoteViews overrideContentView(Notification n, RemoteViews remoteViews) {
		n.extras.putParcelable(EXTRAS_CONTENT_VIEW_OVERRIDE, remoteViews);
		return remoteViews;
	}

	public static RemoteViews overridedContentView(Notification n) {
		return (RemoteViews)n.extras.getParcelable(EXTRAS_CONTENT_VIEW_OVERRIDE);
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

	private static final Map<String, LinkedList<StatusBarNotification>> map = new WeakHashMap<>();

	protected static void cache(StatusBarNotification sbn) {
		final String key = sbn.getKey();
		LinkedList<StatusBarNotification> queue = map.get(key);
		if (queue == null) {
			queue = new LinkedList<>();
			map.put(key, queue);
		}
		queue.add(sbn);
		if (queue.size() > MAX_NUM_ARCHIVED) queue.remove();
	}

	protected static List<StatusBarNotification> getArchivedNotifications(String key) {
		LinkedList<StatusBarNotification> queue = map.get(key);
		return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
	}

	protected static StatusBarNotification getArchivedNotification(String key) {
		LinkedList<StatusBarNotification> queue = map.get(key);
		return queue.getLast();
	}

	protected static boolean hasArchivedNotifications(String key) {
		return map.containsKey(key);
	}

	@Keep public void onCreate() {}
	@Keep public void onDestroy() {}

	
	/**
	 * 在应用进程中执行的通知预处理，某些功能（NotificationChannel等）在此实现。
	 */
	@Keep public void preApply(NotificationManager nm, String tag, int id, Notification n) {}
	@Keep public Decorating onNotificationPosted(final StatusBarNotification sbn) {
		apply(sbn);
		return Decorating.Processed;
	}
	/**
	 * 在系统UI（SystemUI）中执行的通知处理。
	 */
	@Deprecated
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