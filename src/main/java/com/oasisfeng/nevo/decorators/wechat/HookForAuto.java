package com.oasisfeng.nevo.decorators.wechat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.oasisfeng.nevo.xposed.BuildConfig;

public class HookForAuto {
	private static final String TAG = "WeChatDecorator.HookForAuto";

	private static void hookUiModeManager(ClassLoader cl) {
		Class<?> clazz = XposedHelpers.findClassIfExists("android.app.UiModeManager", cl);
		if (clazz == null) return;
		// Log.d(TAG, clazz + " " + cl);
		Object method = XposedHelpers.findMethodBestMatch(clazz, "getCurrentModeType");
		// Log.d(TAG, "method: " + method);
		XposedHelpers.findAndHookMethod(clazz, "getCurrentModeType", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				// Log.d(TAG, "sendBroadcast: " + param.args.length);
				Log.d(TAG, " getCurrentModeType: " + cl);
				param.setResult(3);
			}
		});
	}

	private static void hookApplicationPackageManager(ClassLoader cl) {
		Class<?> clazz = XposedHelpers.findClassIfExists("android.app.ApplicationPackageManager", cl);
		if (clazz == null) return;
		// Log.d(TAG, processName + " " + clazz + " " + cl);
		Object method = XposedHelpers.findMethodBestMatch(clazz, "getPackageInfo", String.class, int.class);
		Log.d(TAG, "method: " + method);
		XposedHelpers.findAndHookMethod(clazz, "getPackageInfo", String.class, int.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				// Log.d(TAG, "sendBroadcast: " + param.args.length);
				String pkg = (String)param.args[0];
				if ("com.google.android.projection.gearhead".equals(pkg)) {
					param.setResult(null);
				} else if (!"com.tencent.mm".equals(pkg)) {
					// Log.d(TAG, " getPackageInfo: " + pkg);
				}
			}
		});
	}

	public static void hook(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
		// Log.d(TAG, " " + loadPackageParam.classLoader);
		// XposedBridge.hookAllConstructors(Context.class, new XC_MethodHook() {
		// 	@Override
		// 	protected void beforeHookedMethod(MethodHookParam param) {
		// 		Class<?> clazz = param.thisObject.getClass();
		// 		Log.d(TAG, loadPackageParam.processName + " context this: " + clazz.getCanonicalName());
		// 	}
		// });
		hookUiModeManager(loadPackageParam.classLoader);
		hookApplicationPackageManager(loadPackageParam.classLoader);
		XposedBridge.hookAllConstructors(ClassLoader.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				ClassLoader cl = (ClassLoader)param.thisObject;
				// Log.d(TAG, "ClassLoader " + cl);
				hookUiModeManager(cl);
				hookApplicationPackageManager(loadPackageParam.classLoader);
			}
		});
		// XposedHelpers.findAndHookMethod("android.support.v4.content.LocalBroadcastManager",  loadPackageParam.classLoader, "sendBroadcast", Intent.class, new XC_MethodHook() {
		// 	@Override
		// 	protected void beforeHookedMethod(MethodHookParam param) {
		// 		// Log.d(TAG, "sendBroadcast: " + param.args.length);
		// 		Intent intent = (Intent)param.args[0];
		// 		Log.d(TAG, "sendBroadcast: " + intent.getAction());
		// 	}
		// });
		// XposedBridge.hookAllConstructors(BroadcastReceiver.class, new XC_MethodHook() {
		// 	@Override
		// 	protected void beforeHookedMethod(MethodHookParam param) {
		// 		Class<?> clazz = param.thisObject.getClass();
		// 		String name = clazz.getCanonicalName();
		// 		if (name == null) { // anonymous inner class
		// 		// 	Log.d(TAG, "null this: " + clazz);
		// 			return;
		// 		}
		// 		switch (name) {
		// 			case "com.tencent.mm.plugin.auto.service.MMAutoMessageHeardReceiver":
		// 				// Log.d(TAG, "auto message this: " + clazz);
		// 				Log.d(TAG, "MMAutoMessageHeardReceiver: " + param.args.length);
		// 			break;
		// 			case "com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver":
		// 				// Log.d(TAG, "auto message this: " + clazz);
		// 				Log.d(TAG, "MMAutoMessageReplyReceiver: " + param.args.length);
		// 			break;
		// 			case "com.tencent.mm.booter.NotifyReceiver":
		// 				Log.d(TAG, "notify receiver this: " + clazz);
		// 			break;
		// 			default:
		// 				// Log.d(TAG, "clazz this: " + clazz.getCanonicalName());
		// 			break;
		// 		}
		// 	}
		// });
	}
}