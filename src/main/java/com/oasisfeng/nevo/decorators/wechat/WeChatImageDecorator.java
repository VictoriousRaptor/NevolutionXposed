/*
 * Copyright (C) 2015 The Nevolution Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.NotificationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.File;

import androidx.core.content.FileProvider;

import static android.os.Build.VERSION.SDK_INT;
import android.os.Build.VERSION_CODES;

import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Oasis on 2018-11-30.
 * Modify by Kr328 on 2019-1-5
 * Modify by notXX on 2019-8-5
 */
public class WeChatImageDecorator extends NevoDecoratorService {
	static String mPath;
	static long mCreated, mClosed;

	static long now() { return System.currentTimeMillis(); }

	@Override public void hook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		Class<?> clazz = java.io.FileOutputStream.class;
		XposedHelpers.findAndHookConstructor(clazz, String.class, boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				String path = (String)param.args[0];
				if (path == null || !path.contains("/image2/")) return;
				long created = now();
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "path", path);
				XposedHelpers.setAdditionalInstanceField(param.thisObject, "created", created);
				// Log.d("inspect.construct", created + " " + path);
			}
		});
		XposedHelpers.findAndHookMethod(clazz, "close", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				String path = (String)XposedHelpers.getAdditionalInstanceField(param.thisObject, "path");
				if (path == null) return;
				long created = (Long)XposedHelpers.getAdditionalInstanceField(param.thisObject, "created");
				long closed = now();
				// Log.d("inspect.close", created + "=>" + closed + " " + path);
				synchronized (this) {
					mPath = path;
					mCreated = created;
					mClosed = closed;
				}
			}
		});
	}

	@Override public void preApply(NotificationManager nm, String tag, int id, Notification n) {
		final Bundle extras = n.extras;
		final CharSequence content_text = extras.getCharSequence(Notification.EXTRA_TEXT);
		if (content_text == null || !content_text.toString().contains("[图片]")) return;
		if (mPath == null || mClosed - now() > 1000) return;
		synchronized (this) {
			// Log.d("inspect", "putString");
			extras.putString("nevo.wechat.image", mPath);
			mPath = null;
		}
	}

	private static String PREFIX = "/storage/emulated/0/";

	@Override public void apply(final StatusBarNotification evolving) {
		final Notification n = evolving.getNotification();
		final Bundle extras = n.extras;
		String path = (String)extras.getString("nevo.wechat.image");
		if (path == null) return;
		// Log.d("inspect", "path " + path);
		File file = new File(path);
		// if (!file.exists()) file = new File(path + ".jpg");
		if (!file.exists() && path.startsWith(PREFIX)) { // StorageRedirect
			path = "/storage/emulated/0/Android/data/com.tencent.mm/sdcard/" + path.substring(PREFIX.length());
			// Log.d("inspect", "path " + path);
			file = new File(path);
		}
		// if (!file.exists()) file = new File(path + ".jpg");
		// Log.d("inspect", "file " + file.exists());
		if (!file.exists()) return;

		String template = extras.getString(Notification.EXTRA_TEMPLATE);
		// Log.d("inspect", path + " " + template);
		switch (template) {
			case TEMPLATE_MESSAGING:
			handleMessaging(n, Uri.fromFile(file));
			break;
			case TEMPLATE_BIG_TEXT:
			break;
		}
	}

	private static final String KEY_DATA_MIME_TYPE = "type";
	private static final String KEY_DATA_URI= "uri";

	private void handleMessaging(Notification n, Uri path) {
		Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
		if ( SDK_INT < VERSION_CODES.P || messages == null || messages.length < 1 ) return;
		int index = messages.length - 1;
		Object last_message = messages[index];
		if (!(last_message instanceof Bundle)) return;

		Bundle data = (Bundle)last_message;
		data.putString(KEY_DATA_MIME_TYPE, "image/jpeg");
		data.putParcelable(KEY_DATA_URI, path);
		// Log.d("inspect", "data " + data);

		n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, messages);
	}
}
