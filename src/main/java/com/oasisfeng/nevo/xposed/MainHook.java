package com.oasisfeng.nevo.xposed;

import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * hook and manupinate notifications.
 * 
 * @author notXX
 */
public class MainHook implements IXposedHookLoadPackage {
	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();
	private final NevoDecoratorService miui = new com.oasisfeng.nevo.decorators.MIUIDecorator();

	private final AtomicReference<NotificationListenerService> nlsRef = new AtomicReference<>();

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		if (!"com.android.systemui".equals(loadPackageParam.packageName)) return;
		final XC_MethodHook onNotificationPosted = new XC_MethodHook() { // 捕获通知到达
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// RankingMap rankingMap = (RankingMap)param.args[1];
				Log.d("MainHook", "onNotificationPosted");
				onNotificationPosted(sbn);
			}
		}, onNotificationRemoved = new XC_MethodHook() { // 捕获通知移除
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// RankingMap rankingMap = (RankingMap)param.args[1];
				// NotificationStats stats = (NotificationStats)param.args[2];
				int reason = (int)param.args[3];
				Log.d("MainHook", "onNotificationRemoved");
				onNotificationRemoved(sbn, reason);
			}
		}, nls = new XC_MethodHook() { // 捕获NotificationListenerService的具体实现
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				// XposedBridge.log("nls constructor " + param.thisObject);
				NotificationListenerService nls = (NotificationListenerService)param.thisObject;
				if (nlsRef.compareAndSet(null, nls)) {
					NevoDecoratorService.setNLS(nls);
				}
				try {
					final Class<?> clazz = nls.getClass();
					XposedBridge.log("NL clazz: " + clazz + " " + loadPackageParam.packageName);
					Method method = XposedHelpers.findMethodExact(clazz, "onNotificationPosted", 
							StatusBarNotification.class, RankingMap.class);
					Log.d("MainHook", "method " + method);
					XposedBridge.hookMethod(method, onNotificationPosted);
					method = XposedHelpers.findMethodBestMatch(clazz, "onNotificationRemoved",  StatusBarNotification.class, RankingMap.class,
							XposedHelpers.findClass("android.service.notification.NotificationStats", loadPackageParam.classLoader), int.class);
					Log.d("MainHook", "method " + method);
					XposedBridge.hookMethod(method, onNotificationRemoved);
				} catch (Throwable e) { XposedBridge.log("NL hook failed "); }
			}
		};
		try {
			XposedBridge.hookAllConstructors(NotificationListenerService.class, nls);
		} catch (Throwable e) { XposedBridge.log("NotificationListenerService hook failed "); }
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.app.ContextImpl", loadPackageParam.classLoader);
			XposedBridge.log("CI clazz: " + clazz);
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedBridge.hookAllMethods(clazz, "createAppContext", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					Context context = (Context)param.getResult();
					if (ref.compareAndSet(null, context)) {
						XposedBridge.log("setAppContext");
						onCreate(context);
					}
				}
			});
		} catch (Throwable e) { XposedBridge.log("ContextImpl hook failed"); }
	}

	private void onCreate(Context context) {
		NevoDecoratorService.setAppContext(context);
		wechat.onCreate();
		miui.onCreate();
	}

	private void onNotificationPosted(StatusBarNotification sbn) {
		if ("com.tencent.mm".equals(sbn.getPackageName())) {
			wechat.apply(sbn);
		} else if ("com.xiaomi.xmsf".equals(sbn.getPackageName())) {
			miui.apply(sbn);
		} else { }
	}

	private void onNotificationRemoved(StatusBarNotification sbn, int reason) {
		if ("com.tencent.mm".equals(sbn.getPackageName())) {
			wechat.onNotificationRemoved(sbn, reason);
		} else if ("com.xiaomi.xmsf".equals(sbn.getPackageName())) {
			miui.onNotificationRemoved(sbn, reason);
		} else { }
	}
}
