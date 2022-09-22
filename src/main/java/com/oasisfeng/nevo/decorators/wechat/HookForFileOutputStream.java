package com.oasisfeng.nevo.decorators.wechat;

import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.oasisfeng.nevo.xposed.BuildConfig;

public class HookForFileOutputStream {
	private static final String TAG = "WeChatDecorator.FileOutputStream";
	private static final String PATH = "path";
	private static final String CREATED = "created";

	private static long now() { return System.currentTimeMillis(); }

	private String mPath;
	private long mCreated, mClosed;

	public void inject(String msg, Bundle extras) {
		if ("[图片]".equals(msg) && mPath != null && mClosed - now() < 1000) {
			synchronized (this) {
				if (BuildConfig.DEBUG) Log.d(TAG, "putString " + mPath);
				extras.putString(WeChatDecorator.EXTRA_PICTURE_PATH, mPath); // 保存图片地址
				mPath = null;
			}
		}
	}

	public void hook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		// 图片预览
		Class<?> clazz = java.io.FileOutputStream.class;
		// FileOutputStream(String name, boolean append)
		XposedHelpers.findAndHookConstructor(clazz, String.class, boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				String path = (String)param.args[0];
				if (path == null) return;
				long created = now();
				if (path.endsWith("/test_writable") || path.endsWith("/xlogtest_writable")) return;
				if (!path.contains("/image2/")) {
					// XLog.d(TAG, created + " (file, append) ? " + path);
					return;
				}
				XposedHelpers.setAdditionalInstanceField(param.thisObject, PATH, path);
				XposedHelpers.setAdditionalInstanceField(param.thisObject, CREATED, created);
				XLog.d(TAG, created + " " + path);
			}
		});
		XposedHelpers.findAndHookMethod(clazz, "close", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				String path = (String)XposedHelpers.getAdditionalInstanceField(param.thisObject, PATH);
				if (path == null) return;
				long created = (Long)XposedHelpers.getAdditionalInstanceField(param.thisObject, CREATED);
				long closed = now();
				if (BuildConfig.DEBUG) Log.d(TAG, created + "=>" + closed + " " + path);
				synchronized (this) {
					mPath = path;
					mCreated = created;
					mClosed = closed;
				}
			}
		});
	}
}