package notxx.NevolutionXposed;

import android.app.Application;
import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Deng on 2018/10/20.
 */


public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		new NotificationHook().init();
		if (!"com.android.systemui".equals(loadPackageParam.packageName)) return;
		try {
			final Class<?> clazz = XposedHelpers.findClass("com.android.systemui.ForegroundServiceControllerImpl", loadPackageParam.classLoader);
			XposedBridge.log("clazz: " + clazz);
			XposedHelpers.findAndHookMethod(clazz, "updateNotification", StatusBarNotification.class, int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					StatusBarNotification sbn = (StatusBarNotification)param.args[0];
					// XposedBridge.log("sbn: " + sbn);
					String pkg = sbn.getPackageName();
					if (!"com.tencent.mm".equals(pkg)) return;
					Notification notification = sbn.getNotification();
					Bundle extras = NotificationCompat.getExtras(notification);
					String title = extras.getString(NotificationCompat.EXTRA_TITLE, "title");
                    String text = extras.getString(NotificationCompat.EXTRA_TEXT, "text");
					extras.putString(NotificationCompat.EXTRA_TITLE, "!! " + title);
					if (text != null && text.startsWith("[2条]")) {
						param.setResult(null);
						extras.putString(NotificationCompat.EXTRA_TEXT, "?? " + text);
					}
				}
			});
		} catch (Throwable e) { XposedBridge.log("ForegroundServiceControllerImpl hook failed"); }
    }

    private Context getContext(){
        return AndroidAppHelper.currentApplication().getApplicationContext();
    }

}
