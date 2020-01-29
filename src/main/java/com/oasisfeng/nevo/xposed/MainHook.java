package com.oasisfeng.nevo.xposed;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Keep;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.notxx.xposed.DeviceSharedPreferences;

import com.oasisfeng.nevo.sdk.HookSupport;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * hook and manupinate notifications.
 * 
 * @author notXX
 */
public class MainHook implements IXposedHookLoadPackage {
	private static final String TAG = "MainHook";

	private final XSharedPreferences pref = DeviceSharedPreferences.get(BuildConfig.APPLICATION_ID);
	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();

	private static void inspect(XC_LoadPackage.LoadPackageParam loadPackageParam, String className, String... methods) {
		try {
			final Class<?> clazz = XposedHelpers.findClass(className, loadPackageParam.classLoader);
			XposedBridge.log("inspect clazz: " + clazz + " " + loadPackageParam.packageName);
			Consumer<String> inspect = method -> {
				XposedBridge.hookAllMethods(clazz, method, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Log.d("inspect.method", loadPackageParam.packageName + " " + param.method.getName());
						for (Object arg : param.args) {
							Log.d("inspect.method", loadPackageParam.packageName + " arg " + arg);
						}
						if (BuildConfig.DEBUG) Log.d(TAG, loadPackageParam.packageName + " " + Log.getStackTraceString(new Exception()));
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
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		if (!"com.tencent.mm".equals(loadPackageParam.packageName)) return;
		if (!"com.tencent.mm".equals(loadPackageParam.processName)) return;
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
					Log.d(TAG, "before apply " + nm + " " + tag + " " + id);
					applyLocally(nm, tag, id, n);
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("NotificationManager hook failed"); }
		try {
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext", Context.class, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context context = (Context)param.args[0];
					if (ref.compareAndSet(null, context)) {
						NevoDecoratorService.setAppContext(context);
						wechat.onCreate(pref);
						if (!wechat.isDisabled() && (wechat instanceof HookSupport)) ((HookSupport)wechat).hook(loadPackageParam); // TODO 没法用onCreate(XSharedPreferences)实现动态配置，需要搞定
					}
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("ContextWrapper hook failed"); }
		/* inspect(loadPackageParam,
				"com.android.server.notification.NotificationManagerService",
				"getNotificationChannel",
				"deleteNotificationChannel",
				"deleteNotificationChannelGroup",
				"createNotificationChannels"); */
	}

	// TODO
	private void applyLocally(NotificationManager nm, String tag, int id, Notification n) {
		if (XposedHelpers.getAdditionalInstanceField(n, "pre-applied") != null) {
			Log.d(TAG, "skip " + n);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(n, "pre-applied", true);
		NevoDecoratorService.setNM(nm);
		if (!wechat.isDisabled()) wechat.apply(nm, tag, id, n);
	}
}
