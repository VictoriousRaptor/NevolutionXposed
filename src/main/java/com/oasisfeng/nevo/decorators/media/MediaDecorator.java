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

import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

/**
 * Created by Deng on 2019/2/18.
 */

public class MediaDecorator extends NevoDecoratorService {
	private static final String TAG = "MediaDecorator";

	@Override public void apply(final StatusBarNotification evolved) {
		final Notification n = evolved.getNotification();
		if (!isMediaNotification(n)) {
			Log.d(TAG, "not media notification");
			return;
		}
		Bundle extras = n.extras;
		int[] array = extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS);
		String title = extras.getString(Notification.EXTRA_TITLE, "未知音乐");
		String text = extras.getString(Notification.EXTRA_TEXT, "未知艺术家");
		RemoteViews remoteViews = getContentView(title, text, n, evolved.getPackageName());
		// extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_CUSTOM);
		n.contentView = remoteViews;
		n.bigContentView = remoteViews;
		// XposedHelpers.setBooleanField(n, "mUsesStandardHeader", true);
		extras.remove(Notification.EXTRA_TEMPLATE);
		// extras.putString(Notification.EXTRA_MEDIA_SESSION, null);
		Log.d(TAG, "notification " + n);
	}

	private RemoteViews getContentView(String title, String subtitle, Notification n, String packageName) {
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
		Context context = getPackageContext();
		PackageManager pm = context.getPackageManager();
		RemoteViews remoteViews = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.notifition_layout);
		String appLabel;
		try {
			appLabel = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
		} catch (PackageManager.NameNotFoundException ex) { appLabel = "??"; }

		remoteViews.setTextViewText(R.id.appName, appLabel);
		remoteViews.setTextViewText(R.id.title, title);
		remoteViews.setTextViewText(R.id.subtitle, subtitle);
		remoteViews.setImageViewIcon(R.id.smallIcon, n.getSmallIcon());
		remoteViews.setTextColor(R.id.appName, textColor);
		remoteViews.setTextColor(R.id.title, textColor);
		remoteViews.setTextColor(R.id.subtitle, textColor);
		remoteViews.setImageViewIcon(R.id.largeIcon, n.getLargeIcon());
		remoteViews.setInt(R.id.smallIcon, "setColorFilter", textColor);
		remoteViews.setInt(R.id.foregroundImage, "setColorFilter", backgroundColor);
		remoteViews.setInt(R.id.background, "setBackgroundColor", backgroundColor);
		TypedArray typedArray = context.obtainStyledAttributes(new int[] { android.R.attr.selectableItemBackground });
		int selectableItemBackground = typedArray.getResourceId(0, 0);
		typedArray.recycle();
		try {
			Context target = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
			BindAction bindAction = (int id, Notification.Action action) -> {
				Log.d(TAG, id + " " + action.getIcon() + " " + n.getSmallIcon());
				remoteViews.setViewVisibility(id, View.VISIBLE);
				// remoteViews.setImageViewBitmap(id, BitmapFactory.decodeResource(context.getResources(),action.getIcon().getResId()));
				remoteViews.setImageViewIcon(id, Icon.createWithResource(target, action.getIcon().getResId()));
				remoteViews.setOnClickPendingIntent(id, action.actionIntent);
				remoteViews.setInt(id, "setColorFilter", textColor);
				remoteViews.setInt(id, "setBackgroundResource", selectableItemBackground);
			};
			if (n.actions != null) {
				Log.d(TAG, "" + n.actions.length);
				if (n.actions.length > 0)
					bindAction.bindAction(R.id.ic_0, n.actions[0]);
				if (n.actions.length > 1)
					bindAction.bindAction(R.id.ic_1, n.actions[1]);
				if (n.actions.length > 2)
					bindAction.bindAction(R.id.ic_2, n.actions[2]);
				if (n.actions.length > 3)
					bindAction.bindAction(R.id.ic_3, n.actions[3]);
				if (n.actions.length > 4)
					bindAction.bindAction(R.id.ic_4, n.actions[4]);
			} else {
				Log.d(TAG, "no action");
			}
		} catch (PackageManager.NameNotFoundException ex) { Log.d(TAG, "package " + packageName + "not found"); }
		return remoteViews;
	}

	private interface BindAction {
		void bindAction(int id, Notification.Action action);
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
