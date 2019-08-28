package com.oasisfeng.nevo.decorators.media;

import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.oasisfeng.nevo.sdk.Decorating;
import com.oasisfeng.nevo.sdk.Decorator;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

/**
 * Created by Deng on 2019/2/18.
 */
public class MediaDecorator extends NevoDecoratorService {
	private interface BindAction {
		void bind(RemoteViews remoteViews, int id, Notification.Action action);
	}

	private static final String TAG = "MediaDecorator";

	@Override public SystemUIDecorator createSystemUIDecorator() {
		return new SystemUIDecorator(this.prefKey) {
			@Override public Decorating onNotificationPosted(final StatusBarNotification sbn) {
				final Notification n = sbn.getNotification();
				if (!isMediaNotification(n)) {
					// Log.d(TAG, "not media notification");
					return Decorating.Unprocessed;
				}
				Bundle extras = n.extras;
				int[] array = extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS);
				CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE, null);
				CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT, null);
				int backgroundColor, textColor;
				if (n.getLargeIcon() != null){
					Bitmap bitmap = getLargeIcon(n);
					int[] colors = ColorUtil.getColor(bitmap);
					backgroundColor = colors[0];
					textColor = colors[1];
				} else {
					backgroundColor = Color.BLACK;
					textColor = Color.WHITE;
				}
				String packageName = sbn.getPackageName();
				Context context = getPackageContext();
				PackageManager pm = context.getPackageManager();
				String appLabel;
				try {
					appLabel = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
				} catch (PackageManager.NameNotFoundException ex) { appLabel = "??"; }
				final String appLabel0 = appLabel;
				TypedArray typedArray = context.obtainStyledAttributes(new int[] { android.R.attr.selectableItemBackground });
				int selectableItemBackground = typedArray.getResourceId(0, 0);
				typedArray.recycle();
				BindRemoteViews bindRemoteViews = (remoteViews) -> {
					remoteViews.setTextViewText(R.id.appName, appLabel0);
					if (title != null)
						remoteViews.setTextViewText(R.id.title, title);
					if (text != null)
						remoteViews.setTextViewText(R.id.subtitle, text);
					remoteViews.setImageViewIcon(R.id.smallIcon, n.getSmallIcon());
					remoteViews.setTextColor(R.id.appName, textColor);
					remoteViews.setTextColor(R.id.title, textColor);
					remoteViews.setTextColor(R.id.subtitle, textColor);
					remoteViews.setImageViewIcon(R.id.largeIcon, n.getLargeIcon());
					remoteViews.setInt(R.id.smallIcon, "setColorFilter", textColor);
					remoteViews.setInt(R.id.foregroundImage, "setColorFilter", backgroundColor);
					remoteViews.setInt(R.id.background, "setBackgroundColor", backgroundColor);
				};
				try {
					Context target = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
					BindAction bindAction = (remoteViews, id, action) -> {
						// Log.d(TAG, id + " " + action.getIcon() + " " + n.getSmallIcon());
						remoteViews.setViewVisibility(id, View.VISIBLE);
						// remoteViews.setImageViewBitmap(id, BitmapFactory.decodeResource(context.getResources(),action.getIcon().getResId()));
						remoteViews.setImageViewIcon(id, Icon.createWithResource(target, action.getIcon().getResId()));
						remoteViews.setOnClickPendingIntent(id, action.actionIntent);
						remoteViews.setInt(id, "setColorFilter", textColor);
						remoteViews.setInt(id, "setBackgroundResource", selectableItemBackground);
					};
					int[] compacts = n.extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS);
					if (overridedContentView(n) == null) {
						n.contentView = overrideContentView(n, new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.media_notifition_layout));
					}
					bindRemoteViews.bind(n.contentView);
					if (n.actions != null) {
						Log.d(TAG, "" + n.actions.length);
						if (n.actions.length > 0)
							bindAction.bind(n.contentView, R.id.ic_0, n.actions[compacts[0]]);
						if (n.actions.length > 1)
							bindAction.bind(n.contentView, R.id.ic_1, n.actions[compacts[1]]);
						if (n.actions.length > 2)
							bindAction.bind(n.contentView, R.id.ic_2, n.actions[compacts[2]]);
					} else {
						Log.d(TAG, "no action");
					}
					if (overridedBigContentView(n) == null) {
						n.bigContentView = overrideBigContentView(n, new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.media_notifition_layout_big));
					}
					bindRemoteViews.bind(n.bigContentView);
					if (n.actions != null) {
						Log.d(TAG, "" + n.actions.length);
						if (n.actions.length > 0)
							bindAction.bind(n.bigContentView, R.id.ic_0, n.actions[0]);
						if (n.actions.length > 1)
							bindAction.bind(n.bigContentView, R.id.ic_1, n.actions[1]);
						if (n.actions.length > 2)
							bindAction.bind(n.bigContentView, R.id.ic_2, n.actions[2]);
						if (n.actions.length > 3)
							bindAction.bind(n.bigContentView, R.id.ic_3, n.actions[3]);
						if (n.actions.length > 4)
							bindAction.bind(n.bigContentView, R.id.ic_4, n.actions[4]);
					} else {
						Log.d(TAG, "no action");
					}
				} catch (PackageManager.NameNotFoundException ex) { Log.d(TAG, "package " + packageName + "not found"); }
				// Log.d(TAG, "notification " + n);
				return Decorating.Processed;
			}
		};
	}

	private Bitmap getLargeIcon(Notification notification){
		Bitmap bitmap = null;
		final Icon icon = notification.getLargeIcon();
		if (icon != null)
			bitmap = (Bitmap) XposedHelpers.callMethod(notification.getLargeIcon(), "getBitmap");
		return bitmap;
	}

	private boolean isMediaNotification(Notification notification){
		if (notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)){
			return true;
		} else {
			return Notification.MediaStyle.class.getName().equals(notification.extras.getString(Notification.EXTRA_TEMPLATE));
		}
	}

}
