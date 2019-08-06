package com.oasisfeng.nevo.xposed;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Keep;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
	private static final String TAG = "MainHook";

	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();
	private final NevoDecoratorService miui = new com.oasisfeng.nevo.decorators.MIUIDecorator();
	private final NevoDecoratorService media = new com.oasisfeng.nevo.decorators.media.MediaDecorator();

	@Keep
	private static void inspect(XC_LoadPackage.LoadPackageParam loadPackageParam, String className, String... methods) {
		try {
			final Class<?> clazz = XposedHelpers.findClass(className, loadPackageParam.classLoader);
			XposedBridge.log("inspect clazz: " + clazz + " " + loadPackageParam.packageName);
			Consumer<String> inspect = method -> {
				XposedBridge.hookAllMethods(clazz, method, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Log.d("inspect.method", loadPackageParam.packageName + " " + param.method.getName());
						for (Object arg : param.args) {
							Log.d("inspect.method", loadPackageParam.packageName + " arg " + arg);
						}
						// Log.d("inspect.method", loadPackageParam.packageName + " " + Log.getStackTraceString(new Exception()));
					}
				});
			};
			for (String method : methods) {
				inspect.accept(method);
			}
		} catch (XposedHelpers.ClassNotFoundError e) { /* XposedBridge.log("ContextImpl hook failed"); */ }
	}

	@Keep
	private static void inspectThen(XC_LoadPackage.LoadPackageParam loadPackageParam, String className, Consumer<Class<?>>... thens) {
		try {
			final Class<?> clazz = XposedHelpers.findClass(className, loadPackageParam.classLoader);
			XposedBridge.log("inspect clazz: " + clazz + " " + loadPackageParam.packageName);
			for (Consumer<Class<?>> then : thens) {
				then.accept(clazz);
			}
		} catch (XposedHelpers.ClassNotFoundError e) { /* XposedBridge.log("ContextImpl hook failed"); */ }
	}

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		switch (loadPackageParam.packageName) {
			case "com.android.systemui":
			hookSystemUI(loadPackageParam);
			break;
			case "com.tencent.mm":
			hookWeChat(loadPackageParam);
		}
		/* inspect(loadPackageParam,
				"com.android.server.notification.NotificationManagerService",
				"getNotificationChannel",
				"deleteNotificationChannel",
				"deleteNotificationChannelGroup",
				"createNotificationChannels"); */
	}

	private void hookSystemUI(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		AtomicReference<NotificationListenerService> nlsRef = new AtomicReference<>();
		final XC_MethodHook onNotificationPosted = new XC_MethodHook() { // 捕获通知到达
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// RankingMap rankingMap = (RankingMap)param.args[1];
				Log.d(TAG, "onNotificationPosted");
				onNotificationPosted(sbn);
			}
		}, onNotificationRemoved = new XC_MethodHook() { // 捕获通知移除
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// RankingMap rankingMap = (RankingMap)param.args[1];
				// NotificationStats stats = (NotificationStats)param.args[2];
				int reason = (int)param.args[3];
				Log.d(TAG, "onNotificationRemoved");
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
					Log.d(TAG, "method " + method);
					XposedBridge.hookMethod(method, onNotificationPosted);
					method = XposedHelpers.findMethodBestMatch(clazz, "onNotificationRemoved",  StatusBarNotification.class, RankingMap.class,
							XposedHelpers.findClass("android.service.notification.NotificationStats", loadPackageParam.classLoader), int.class);
					Log.d(TAG, "method " + method);
					XposedBridge.hookMethod(method, onNotificationRemoved);
				} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("NL hook failed "); }
			}
		};
		try {
			XposedBridge.hookAllConstructors(NotificationListenerService.class, nls);
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("NotificationListenerService hook failed "); }
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.app.ContextImpl", loadPackageParam.classLoader);
			XposedBridge.log("CI clazz: " + clazz);
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedBridge.hookAllMethods(clazz, "createAppContext", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					Context context = (Context)param.getResult();
					if (ref.compareAndSet(null, context)) {
						XposedBridge.log("onCreate " + context);
						onCreate(context);
					}
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("ContextImpl hook failed"); }
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.app.Notification$Builder", loadPackageParam.classLoader);
			XposedBridge.log("Builder clazz: " + clazz);
			// 让应用了Notification.Style的通知也可以自定义视图
			XposedBridge.hookAllMethods(clazz, "createContentView", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					// Log.d("inspect", "createContentView");
					Notification.Builder builder = (Notification.Builder)param.thisObject;
					// Notification.Style style = (Notification.Style)XposedHelpers.getObjectField(builder, "mStyle");
					Notification n = (Notification)XposedHelpers.getObjectField(builder, "mN");
					// Log.d("inspect", "mStyle/mN " + style + "/" + n + " " + (style instanceof Notification.MediaStyle));
					RemoteViews remoteViews = NevoDecoratorService.overridedContentView(n);
					if (remoteViews != null) {
						Log.d(TAG, "cheating createContentView");
						param.setResult(remoteViews);
					}
				}
			});
			XposedBridge.hookAllMethods(clazz, "createBigContentView", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					// Log.d("inspect", "createContentView");
					Notification.Builder builder = (Notification.Builder)param.thisObject;
					// Notification.Style style = (Notification.Style)XposedHelpers.getObjectField(builder, "mStyle");
					Notification n = (Notification)XposedHelpers.getObjectField(builder, "mN");
					// Log.d("inspect", "mStyle/mN " + style + "/" + n + " " + (style instanceof Notification.MediaStyle));
					RemoteViews remoteViews = NevoDecoratorService.overridedBigContentView(n);
					if (remoteViews != null) {
						Log.d(TAG, "cheating createBigContentView");
						param.setResult(remoteViews);
					}
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("Notification.Builder hook failed"); }
	}

	private void hookWeChat(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.app.NotificationManager", loadPackageParam.classLoader);
			XposedBridge.log("NM clazz: " + clazz);
			Method method = XposedHelpers.findMethodExact(clazz, "notify", String.class, int.class, Notification.class);
			XposedBridge.log("NM.notify: " + method);
			XposedBridge.hookMethod(method, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					NotificationManager nm = (NotificationManager)param.thisObject;
					String tag = (String)param.args[0];
					int id = (int)param.args[1];
					Notification n = (Notification)param.args[2];
					preApply(nm, tag, id, n);
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("NotificationManager hook failed"); }
	}
	
	private void onCreate(Context context) {
		NevoDecoratorService.setAppContext(context);
		wechat.onCreate();
		miui.onCreate();
		media.onCreate();
	}
	
	private void preApply(NotificationManager nm, String tag, int id, Notification n) {
		if (XposedHelpers.getAdditionalInstanceField(n, "pre-applied") != null) {
			Log.d(TAG, "skip " + n);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(n, "pre-applied", true);
		wechat.preApply(nm, tag, id, n);
	}

	private void onNotificationPosted(StatusBarNotification sbn) {
		if (XposedHelpers.getAdditionalInstanceField(sbn, "applied") != null) {
			Log.d(TAG, "skip " + sbn);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(sbn, "applied", true);
		switch (sbn.getPackageName()) {
			case "com.tencent.mm":
			wechat.apply(sbn);
			break;
			case "com.xiaomi.xmsf":
			miui.apply(sbn);
			break;
		}
		media.apply(sbn);
	}

	private void onNotificationRemoved(StatusBarNotification sbn, int reason) {
		switch (sbn.getPackageName()) {
			case "com.tencent.mm":
			wechat.onNotificationRemoved(sbn, reason);
			break;
			case "com.xiaomi.xmsf":
			miui.onNotificationRemoved(sbn, reason);
			break;
		}
		media.onNotificationRemoved(sbn, reason);
	}
}
