package com.notxx.miui;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import de.robv.android.xposed.XposedHelpers;

import notxx.NevolutionXposed.R;

import top.trumeet.common.cache.IconCache;

public class MIUIDecorator extends NevoDecoratorService {

    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";
	private static final String TAG = "MIUIDecorator";

	private static void setSmallIcon(Notification n, Icon icon) {
		XposedHelpers.setObjectField(n, "mSmallIcon", icon);
	}

    @Override
    protected void apply(StatusBarNotification evolving) {
		final Notification n = evolving.getNotification();
		final Context context = getAppContext();
        Log.d(TAG, "begin modifying " + context);
        Icon defIcon = Icon.createWithResource(context, R.drawable.default_notification_icon);
        Bundle extras = n.extras;
        String packageName = null;
        try {
            packageName = evolving.getPackageName();
            if ("com.xiaomi.xmsf".equals(packageName))
                packageName = extras.getString("target_package", null);
		} catch (final RuntimeException ignored) {}    // Fall-through
        if (packageName == null) {
            Log.e(TAG, "packageName is null");
            return;
        }
        extras.putBoolean("miui.isGrayscaleIcon", true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // do nothing
        } else {
            int iconId;
            // Log.d(TAG, "packageName: " + packageName);
			iconId = context.getResources().getIdentifier(NOTIFICATION_SMALL_ICON, "drawable", packageName);
			if (iconId > 0) // has icon
				setSmallIcon(n, Icon.createWithResource(packageName, iconId));
            if (iconId <= 0) { // does not have icon
                Icon iconCache = IconCache.getInstance().getIconCache(context, packageName, (ctx, b) -> Icon.createWithBitmap(b));
                if (iconCache != null) {
                    setSmallIcon(n, iconCache);
                } else {
                    setSmallIcon(n, defIcon);
                }
            }
        }
        Log.d(TAG, "end modifying");
    }
}
