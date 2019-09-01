package com.notxx.notification;

import android.app.Notification;
import android.os.Bundle;
import android.util.Log;

import com.oasisfeng.nevo.sdk.HookSupport;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


class MIUIBetaFix(val primary: Notification.Style, val secondary: Notification.Style) : Notification.Style() {
	constructor() : this(XposedHelpers.newInstance(Notification.MessagingStyle::class.java) as Notification.Style, XposedHelpers.newInstance(Notification.BigTextStyle::class.java) as Notification.Style){
		Log.d("inspect", "new MIUIBetaFix")
	}
}

fun java.lang.reflect.Method.isAbstract(): Boolean {
	return java.lang.reflect.Modifier.isAbstract(this.getModifiers())
}

fun java.lang.reflect.Method.isStatic(): Boolean {
	return java.lang.reflect.Modifier.isStatic(this.getModifiers())
}

fun Notification.Style.addExtras(extras: Bundle): Unit {
	XposedHelpers.callMethod(this, "addExtras", extras);
}

class MIUIBetaFixXposed : HookSupport {
	override fun hook(lpp: XC_LoadPackage.LoadPackageParam): Unit {
		// val clazz = MIUIBetaFix::class.java
		run {
			val method = XposedHelpers.findMethodExact(Notification::class.java, "getNotificationStyleClass", String::class.java)
			XposedBridge.hookMethod(method, object : XC_MethodHook() {
				override fun beforeHookedMethod(param: MethodHookParam) {
					val templateClass = param.args[0];
					if (templateClass !is String) return
					// Log.d("inspect", "getNotificationStyleClass " + templateClass)
					when (templateClass) {
						NevoDecoratorService.TEMPLATE_MESSAGING0 -> param.setResult(MIUIBetaFix::class.java)
						NevoDecoratorService.TEMPLATE_MESSAGING -> param.setResult(MIUIBetaFix::class.java)
					}
				}
			})
		}
		for (method in Notification.Style::class.java.getDeclaredMethods()) {
			if (method.isStatic()) continue
			if (method.isAbstract()) continue
			// val method = XposedHelpers.findMethodExact(clazz, m.getName(), m.getParameterTypes())
			// Log.d("inspect", "method " + method)
			when (method.getName()) {
				// addExtras(extras:Bundle)
				"addExtras" -> {
					XposedBridge.hookMethod(method, object : XC_MethodHook() {
						override fun beforeHookedMethod(param: MethodHookParam) {
							val thiz = param.thisObject
							if (thiz !is MIUIBetaFix) return
							val extras = param.args[0] as Bundle
							thiz.primary.addExtras(extras)
							thiz.secondary.addExtras(extras)
							param.setResult(extras.putString(Notification.EXTRA_TEMPLATE, thiz.javaClass.getName()))
						}
					})
					Log.d("inspect", "hook addExtras")
				}
				// makeContentView
				"makeContentView" -> {
					XposedBridge.hookMethod(method, object : XC_MethodHook() {
						override fun beforeHookedMethod(param: MethodHookParam) {
							val thiz = param.thisObject
							if (thiz !is MIUIBetaFix) return
							param.setResult(XposedBridge.invokeOriginalMethod(method, thiz.secondary, param.args))
						}
					})
					Log.d("inspect", "hook makeContentView")
				}
				// makeBigContentView
				"makeBigContentView" -> {
					XposedBridge.hookMethod(method, object : XC_MethodHook() {
						override fun beforeHookedMethod(param: MethodHookParam) {
							val thiz = param.thisObject
							if (thiz !is MIUIBetaFix) return
							param.setResult(XposedBridge.invokeOriginalMethod(method, thiz.primary, param.args))
						}
					})
					Log.d("inspect", "hook makeBigContentView")
				}
				// else
				else -> {
					XposedBridge.hookMethod(method, object : XC_MethodHook() {
						override fun beforeHookedMethod(param: MethodHookParam) {
							val thiz = param.thisObject
							if (thiz !is MIUIBetaFix) return
							param.setResult(XposedBridge.invokeOriginalMethod(method, thiz.primary, param.args))
						}
					})
					// Log.d("inspect", "hook " + method)
				}
			}
		}
	}
}