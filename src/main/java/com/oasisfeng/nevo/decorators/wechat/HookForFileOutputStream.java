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
		XposedHelpers.findAndHookConstructor(clazz, File.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				File file = (File)param.args[0];
				if (file == null) return;
				String path = file.getAbsolutePath();
				if (path == null) return;
				long created = now();
				if (!path.contains("/image2/")) {
					Log.d(TAG, created + " (file) ? " + path);
					return;
				}
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "path", path);
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "created", created);
				if (BuildConfig.DEBUG) Log.d(TAG, created + " " + path);
			}
		});
		XposedHelpers.findAndHookConstructor(clazz, File.class, boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				File file = (File)param.args[0];
				if (file == null) return;
				String path = file.getAbsolutePath();
				if (path == null) return;
				long created = now();
				if (!path.contains("/image2/")) {
					Log.d(TAG, created + " (file, append) ? " + path);
					return;
				}
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "path", path);
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "created", created);
				if (BuildConfig.DEBUG) Log.d(TAG, created + " " + path);
			}
		});
		XposedHelpers.findAndHookConstructor(clazz, FileDescriptor.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				FileDescriptor fd = (FileDescriptor)param.args[0];
				if (fd == null) return;
				long created = now();
				Log.d(TAG, created + " (fd) ? " + fd);
			}
		});
		XposedHelpers.findAndHookConstructor(clazz, String.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				String path = (String)param.args[0];
				if (path == null) return;
				long created = now();
				if (!path.contains("/image2/")) {
					Log.d(TAG, created + " (name) ? " + path);
					return;
				}
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "path", path);
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "created", created);
				if (BuildConfig.DEBUG) Log.d(TAG, created + " " + path);
			}
		});
		XposedHelpers.findAndHookConstructor(clazz, String.class, boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				String path = (String)param.args[0];
				if (path == null) return;
				long created = now();
				if (!path.contains("/image2/")) {
					Log.d(TAG, created + " (name, append) ? " + path);
					return;
				}
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "path", path);
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "created", created);
				if (BuildConfig.DEBUG) Log.d(TAG, created + " " + path);
			}
		});
		XposedHelpers.findAndHookMethod(clazz, "close", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				String path = (String)XposedHelpers.getAdditionalInstanceField(param.thisObject, "path");
				if (path == null) return;
				long created = (Long)XposedHelpers.getAdditionalInstanceField(param.thisObject, "created");
				long closed = now();
				if (BuildConfig.DEBUG) Log.d(TAG, created + "=>" + closed + " " + path);
				// synchronized (this) {
				// 	mPath = path;
				// 	mCreated = created;
				// 	mClosed = closed;
				// }
			}
		});
	}
}