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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat.MessagingStyle;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.service.notification.NotificationListenerService.REASON_APP_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_CHANNEL_BANNED;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import com.oasisfeng.nevo.sdk.HookSupport;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

/**
 * Bring state-of-art notification experience to WeChat.
 *
 * Created by Oasis on 2015/6/1.
 *
 * @class WeChatImageDecorator.
 * 
 * Created by Oasis on 2018-11-30.
 * Modify by Kr328 on 2019-1-5
 * Modify by notXX on 2019-8-5
 */
public class WeChatDecorator extends NevoDecoratorService implements HookSupport {

	public static final String WECHAT_PACKAGE = "com.tencent.mm";
	private static final int MAX_NUM_ARCHIVED = 20;
	private static final long GROUP_CHAT_SORT_KEY_SHIFT = 24 * 60 * 60 * 1000L;			// Sort group chat like one day older message.
	private static final String CHANNEL_MESSAGE = "message_channel_new_id";				// Channel ID used by WeChat for all message notifications
	private static final String OLD_CHANNEL_MESSAGE = "message";						//   old name for migration
	private static final String CHANNEL_MISC = "reminder_channel_id";					// Channel ID used by WeChat for misc. notifications
	private static final String OLD_CHANNEL_MISC = "misc";								//   old name for migration
	private static final String CHANNEL_DND = "message_dnd_mode_channel_id";			// Channel ID used by WeChat for its own DND mode
	private static final String CHANNEL_GROUP_CONVERSATION = "group";					// WeChat has no separate group for group conversation
	private static final String RECALL_PATTERN = "\\[(\\d+)条\\](\"([^\"]+)\" )?撤回了?一条消息";
	private static final Pattern pattern = Pattern.compile(RECALL_PATTERN);				// [2条]"🦉 " 撤回了一条消息 / [2条]撤回一条消息

	private static final @ColorInt int PRIMARY_COLOR = 0xFF33B332;
	private static final @ColorInt int LIGHT_COLOR = 0xFF00FF00;
	static final String ACTION_SETTINGS_CHANGED = "SETTINGS_CHANGED";
	static final String ACTION_DEBUG_NOTIFICATION = "DEBUG";
	private static final String KEY_SILENT_REVIVAL = "nevo.wechat.revival";
	private static final String EXTRA_RECALL = "nevo.wechat.recall";
	private static final String EXTRA_RECALLER = "nevo.wechat.recaller";
	public static final String EXTRA_PICTURE_PATH = "nevo.wechat.picturePath";
	private static final String EXTRA_PICTURE = "nevo.wechat.picture";
	private static final String STORAGE_PREFIX = "/storage/emulated/0/";

	private static String mPath;
	private static long mCreated, mClosed;

	private static long now() { return System.currentTimeMillis(); }

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
				synchronized (this) {
					mPath = path;
					mCreated = created;
					mClosed = closed;
				}
			}
		});
	}

	@Override public void preApply(NotificationManager nm, String tag, int id, Notification n) {
		mWeChatTargetingO = isWeChatTargeting26OrAbove();

		final Bundle extras = n.extras;
		CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
		if (title == null || title.length() == 0) {
			Log.e(TAG, "Title is missing: " + n);
			return;
		}
		if (title != (title = EmojiTranslator.translate(title))) extras.putCharSequence(Notification.EXTRA_TITLE, title);

		String channel_id = SDK_INT >= O ? n.getChannelId() : null;
		if (CHANNEL_MISC.equals(channel_id)) return;	// Misc. notifications on Android 8+.
		
		final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
		String content = text != null ? text.toString() : null;
		// [2条]...
		if (content != null && content.startsWith("[")) {
			if (BuildConfig.DEBUG) Log.d(TAG, "content " + content);
			final int end = content.indexOf(']');
			if (content.charAt(end - 1) == '条') {
				n.number = Integer.parseInt(content.substring(1, end - 1));
				if (BuildConfig.DEBUG) Log.d(TAG, "n.number " + n.number);
				content = content.substring(end + 1);
			}
		}
		// 撤回...
		int type = Conversation.TYPE_UNKNOWN;
		if (content != null && content.contains("撤回")) {
			boolean is_recall = false;
			String recaller = null;
			if (BuildConfig.DEBUG) Log.d(TAG, "content " + content);
			if (CHANNEL_MISC.equals(channel_id)) {	// Misc. notifications on Android 8+.
				return;
			} else if (n.tickerText == null) {		// Legacy misc. notifications.
				if (SDK_INT >= O && channel_id == null) setChannelId(n, CHANNEL_MISC);
				Matcher matcher = pattern.matcher(content);
				if (matcher.matches()) {
					// 撤回
					// Log.d(TAG, matcher.group(0) + ", " + matcher.group(1) + ", " + matcher.group(2) + ", " + matcher.group(3));
					is_recall = true;
					recaller = matcher.group(3);
					extras.putBoolean(EXTRA_RECALL, true);
					extras.putString(EXTRA_RECALLER, recaller);
					if (BuildConfig.DEBUG) Log.d(TAG, "recaller " + recaller);
				} else {
					Log.d(TAG, "Skip further process for non-conversation notification: " + title);    // E.g. web login confirmation notification.
					return;
				}
			}
			if (is_recall) type = (recaller == null) ? Conversation.TYPE_DM_RECALL : Conversation.TYPE_GC_RECALL;
		}
		if (content == null || content.isEmpty()) return;

		extras.putCharSequence(Notification.EXTRA_TEXT, content);

		int sep = content.indexOf(WeChatMessage.SENDER_MESSAGE_SEPARATOR);
		if (sep > 0) {
			String person = content.substring(0, sep);
			String msg = content.substring(sep + WeChatMessage.SENDER_MESSAGE_SEPARATOR.length());
			if (BuildConfig.DEBUG) Log.d(TAG, person + "|" + msg);
			if ("[图片]".equals(msg) && mPath != null && mClosed - now() < 1000) {
				synchronized (this) {
					if (BuildConfig.DEBUG) Log.d(TAG, "putString " + mPath);
					extras.putString(EXTRA_PICTURE_PATH, mPath); // 保存图片地址
					mPath = null;
				}
			}
		}

		if (type == Conversation.TYPE_UNKNOWN) type = WeChatMessage.guessConversationType(content, n.tickerText.toString().trim(), title);
		final boolean is_group_chat = Conversation.isGroupChat(type);
		if (SDK_INT >= O) {
			if (extras.containsKey(KEY_SILENT_REVIVAL)) {
				setGroup(n, "nevo.group.auto");	// Special group name to let Nevolution auto-group it as if not yet grouped. (To be standardized in SDK)
				setGroupAlertBehavior(n, Notification.GROUP_ALERT_SUMMARY);		// This trick makes notification silent
			}
			if (is_group_chat && ! CHANNEL_DND.equals(channel_id)) setChannelId(n, CHANNEL_GROUP_CONVERSATION);
			else if (channel_id == null) setChannelId(n, CHANNEL_MESSAGE);		// WeChat versions targeting O+ have its own channel for message
		}

		channel_id = n.getChannelId();
		if (channel_id != null) { // 确保NotificationChannel存在
			NotificationChannel channel = nm.getNotificationChannel(channel_id);
			if (BuildConfig.DEBUG) Log.d(TAG, channel_id + " " + channel);
			if (channel != null) return;
			switch (channel_id) {
				case CHANNEL_GROUP_CONVERSATION:
				channel = makeChannel(CHANNEL_GROUP_CONVERSATION, R.string.channel_group_message, false);
				break;
				case CHANNEL_MESSAGE:
				channel = migrate(nm, OLD_CHANNEL_MESSAGE,	CHANNEL_MESSAGE,	R.string.channel_message, false);
				break;
				case CHANNEL_MISC:
				channel = migrate(nm, OLD_CHANNEL_MISC,		CHANNEL_MISC,		R.string.channel_misc, true);
				break;
			}
			if (BuildConfig.DEBUG) Log.d(TAG, channel_id + " " + channel);
			nm.createNotificationChannel(channel);
			channel = nm.getNotificationChannel(channel_id);
			if (BuildConfig.DEBUG) Log.d(TAG, channel_id + " " + channel);
		}
	}

	@Override public void apply(final StatusBarNotification evolving) {
		final String key = evolving.getKey();
		setOriginalId(evolving, evolving.getId());
		setOriginalKey(evolving, key);
		setOriginalTag(evolving, evolving.getTag());
		LinkedList<StatusBarNotification> queue = map.get(key);
		if (queue == null) {
			queue = new LinkedList<>();
			map.put(key, queue);
		}
		queue.add(evolving);
		if (queue.size() > MAX_NUM_ARCHIVED) queue.remove();

		final Notification n = evolving.getNotification();
		final Bundle extras = n.extras;
		final Context context = getPackageContext();

		// 图片
		if (extras.containsKey(EXTRA_PICTURE_PATH)) {
			final String path = extras.getString(EXTRA_PICTURE_PATH);
			File file = new File(path);
			if (!file.exists() && path.startsWith(STORAGE_PREFIX)) { // StorageRedirect
				// path = "/storage/emulated/0/Android/data/com.tencent.mm/sdcard/" + path.substring(STORAGE_PREFIX.length());
				if (BuildConfig.DEBUG) Log.d(TAG, "path " + path);
				file = new File(path.replace(STORAGE_PREFIX, "/storage/emulated/0/Android/data/com.tencent.mm/sdcard/"));
			}
			if (file.exists()) extras.putString(EXTRA_PICTURE_PATH, file.getPath()); // 更新文件路径
		}

		CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
		if (title == null || title.length() == 0) {
			Log.e(TAG, "Title is missing: " + evolving);
			return;
		}
		if (title != (title = EmojiTranslator.translate(title))) extras.putCharSequence(Notification.EXTRA_TITLE, title);
		n.color = PRIMARY_COLOR;        // Tint the small icon

		final String channel_id = SDK_INT >= O ? n.getChannelId() : null;
		final CharSequence content_text = extras.getCharSequence(Notification.EXTRA_TEXT);
		if (BuildConfig.DEBUG) Log.d(TAG, "content_text " + content_text);
		boolean is_recall = extras.getBoolean(EXTRA_RECALL);
		String recaller = extras.getString(EXTRA_RECALLER);
		if (CHANNEL_MISC.equals(channel_id)) {	// Misc. notifications on Android 8+.
			return;
		} else if (n.tickerText == null) {		// Legacy misc. notifications.
			if (!is_recall) {
				Log.d(TAG, "Skip further process for non-conversation notification: " + title);    // E.g. web login confirmation notification.
				return;
			}
		}
		if (content_text == null) return;

		// WeChat previously uses dynamic counter starting from 4097 as notification ID, which is reused after cancelled by WeChat itself,
		//   causing conversation duplicate or overwritten notifications.
		final Conversation conversation;
		if (! isDistinctId(n, evolving.getPackageName())) {
			final int title_hash = title.hashCode();	// Not using the hash code of original title, which might have already evolved.
			setId(evolving, title_hash);
			conversation = mConversationManager.getConversation(title_hash);
		} else conversation = mConversationManager.getConversation(getOriginalId(evolving));

		conversation.setTitle(title);
		conversation.summary = content_text;
		conversation.ticker = n.tickerText;
		conversation.timestamp = n.when;
		if (is_recall)
			conversation.setType((recaller == null) ? Conversation.TYPE_DM_RECALL : Conversation.TYPE_GC_RECALL);
		else if (conversation.getType() == Conversation.TYPE_UNKNOWN)
			conversation.setType(WeChatMessage.guessConversationType(conversation));
		final boolean is_group_chat = conversation.isGroupChat();

		extras.putBoolean(Notification.EXTRA_SHOW_WHEN, true);
		if (mPreferences.getBoolean(mPrefKeyWear, false)) n.flags &= ~ Notification.FLAG_LOCAL_ONLY;
		setSortKey(n, String.valueOf(Long.MAX_VALUE - n.when + (is_group_chat ? GROUP_CHAT_SORT_KEY_SHIFT : 0))); // Place group chat below other messages

		MessagingStyle messaging = mMessagingBuilder.buildFromExtender(conversation, evolving, title, getArchivedNotifications(getOriginalKey(evolving), MAX_NUM_ARCHIVED)); // build message from android auto
		if (messaging == null)	// EXTRA_TEXT will be written in buildFromArchive()
			messaging = mMessagingBuilder.buildFromArchive(conversation, n, title, getArchivedNotifications(getOriginalKey(evolving), MAX_NUM_ARCHIVED));
		if (messaging == null) return;
		final List<MessagingStyle.Message> messages = messaging.getMessages();
		if (messages.isEmpty()) return;

		if (is_group_chat) messaging.setGroupConversation(true).setConversationTitle(title);
		MessagingBuilder.flatIntoExtras(messaging, extras);
		extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_MESSAGING);

		if (SDK_INT >= N && extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) != null)
			n.flags |= Notification.FLAG_ONLY_ALERT_ONCE;		// No more alert for direct-replied notification.

		// fix for recent (around 20190720) MIUI bugs
		if (overridedContentView(n) == null) {
			n.contentView = overrideContentView(n, new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.wechat_notifition_layout));
		}
		n.contentView.setTextViewText(R.id.title, title);
		n.contentView.setTextViewText(R.id.subtitle, content_text);
		n.contentView.setImageViewIcon(R.id.smallIcon, n.getSmallIcon());
		n.contentView.setImageViewIcon(R.id.largeIcon, n.getLargeIcon());
		n.contentView.setInt(R.id.smallIcon, "setColorFilter", PRIMARY_COLOR);
	}
	
	private boolean isDistinctId(final Notification n, final String pkg) {
		if (mDistinctIdSupported != null) return mDistinctIdSupported;
		int version = 0;
		final ApplicationInfo app_info = n.extras.getParcelable("android.appInfo");
		if (app_info != null) try {
			if (pkg.equals(app_info.packageName))	// This will be Nevolution for active evolved notifications.
				//noinspection JavaReflectionMemberAccess
				version = (int) ApplicationInfo.class.getField("versionCode").get(app_info);
		} catch (final IllegalAccessException | NoSuchFieldException | ClassCastException ignored) {}    // Fall-through
		if (version == 0) try {
			version = getPackageManager().getPackageInfo(pkg, 0).versionCode;
		} catch (final PackageManager.NameNotFoundException ignored) {}
		return version != 0 && (mDistinctIdSupported = version >= 1340);	// Distinct ID is supported since WeChat 6.7.3.
	}
	private Boolean mDistinctIdSupported;

	@Override public void onNotificationRemoved(final StatusBarNotification notification, final int reason) {
		Log.d(TAG, "onNotificationRemoved(" + notification + ", " + reason + ")");
		super.onNotificationRemoved(notification, reason);
	}

	@Override public void onNotificationRemoved(final String key, final int reason) {
		Log.d(TAG, "onNotificationRemoved(" + key + ", " + reason + ")");
		if (reason == REASON_APP_CANCEL) {		// Only if "Removal-Aware" of Nevolution is activated
			Log.d(TAG, "Cancel notification: " + key);
			cancelNotification(key);	// Will cancel all notifications evolved from this original key, thus trigger the "else" branch below
		} else if (reason == REASON_CHANNEL_BANNED) {	// In case WeChat deleted our notification channel for group conversation in Insider delivery mode
			// mHandler.post(() -> reviveNotificationAfterChannelDeletion(key));
		} else if (SDK_INT < O || reason == REASON_CANCEL) {	// Exclude the removal request by us in above case. (Removal-Aware is only supported on Android 8+)
			mMessagingBuilder.markRead(key);
		}
	}

	private void reviveNotificationAfterChannelDeletion(final String key) {
		Log.d(TAG, ("Revive silently: ") + key);
		final Bundle addition = new Bundle();
		addition.putBoolean(KEY_SILENT_REVIVAL, true);
		recastNotification(key, addition);
	}

	@RequiresApi(O) private NotificationChannel migrate(NotificationManager nm, final String old_id, final String new_id, final @StringRes int new_name, final boolean silent) {
		final NotificationChannel channel_message = nm.getNotificationChannel(old_id);
		nm.deleteNotificationChannel(old_id);
		if (channel_message != null) return cloneChannel(channel_message, new_id, new_name);
		else return makeChannel(new_id, new_name, silent);
	}

	@RequiresApi(O) private NotificationChannel makeChannel(final String channel_id, final @StringRes int name, final boolean silent) {
		final NotificationChannel channel = new NotificationChannel(channel_id, getString(name), NotificationManager.IMPORTANCE_HIGH/* Allow heads-up (by default) */);
		if (silent) channel.setSound(null, null);
		else channel.setSound(getDefaultSound(), new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).build());
		channel.enableLights(true);
		channel.setLightColor(LIGHT_COLOR);
		return channel;
	}

	@RequiresApi(O) private NotificationChannel cloneChannel(final NotificationChannel channel, final String id, final int new_name) {
		final NotificationChannel clone = new NotificationChannel(id, getString(new_name), channel.getImportance());
		clone.setGroup(channel.getGroup());
		clone.setDescription(channel.getDescription());
		clone.setLockscreenVisibility(channel.getLockscreenVisibility());
		clone.setSound(Optional.ofNullable(channel.getSound()).orElse(getDefaultSound()), channel.getAudioAttributes());
		clone.setBypassDnd(channel.canBypassDnd());
		clone.setLightColor(channel.getLightColor());
		clone.setShowBadge(channel.canShowBadge());
		clone.setVibrationPattern(channel.getVibrationPattern());
		return clone;
	}

	@Nullable private Uri getDefaultSound() {	// Before targeting O, WeChat actually plays sound by itself (not via Notification).
		return mWeChatTargetingO ? Settings.System.DEFAULT_NOTIFICATION_URI : null;
	}

	private boolean isWeChatTargeting26OrAbove() {
		try {
			return getPackageManager().getApplicationInfo(WECHAT_PACKAGE, PackageManager.GET_UNINSTALLED_PACKAGES).targetSdkVersion >= O;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	@Override public void onCreate() {
		super.onCreate();
		loadPreferences();
		mPrefKeyWear = getString(R.string.pref_wear);

		mMessagingBuilder = new MessagingBuilder(getAppContext(), getPackageContext(), mPreferences, this::recastNotification);		// Must be called after loadPreferences().
	}

	@Override public void onDestroy() {
	// 	unregisterReceiver(mSettingsChangedReceiver);
	// 	unregisterReceiver(mPackageEventReceiver);
		mMessagingBuilder.close();
		super.onDestroy();
	}

	private void loadPreferences() {
		Context context = getPackageContext();
		if (SDK_INT >= N)
			context = context.createDeviceProtectedStorageContext();
		//noinspection deprecation
		mPreferences = context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_MULTI_PROCESS);
	}

	private static String getDefaultSharedPreferencesName(final Context context) {
		return SDK_INT >= N ? PreferenceManager.getDefaultSharedPreferencesName(context) : context.getPackageName() + "_preferences";
	}



	private final ConversationManager mConversationManager = new ConversationManager();
	private MessagingBuilder mMessagingBuilder;
	private boolean mWeChatTargetingO;
	private SharedPreferences mPreferences;
	private String mPrefKeyWear;
	// private final Handler mHandler = new Handler();

	static final String TAG = "Nevo.Decorator[WeChat]";

	private static final Map<String, LinkedList<StatusBarNotification>> map = new WeakHashMap<>();

	public static List<StatusBarNotification> getArchivedNotifications(String key, int max) {
		LinkedList<StatusBarNotification> queue = map.get(key);
		return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
	}

	public static interface ModifyStatusBarNotification { void modify(StatusBarNotification sbn); }

	private void recastNotification(final String key, final @Nullable Bundle fillInExtras, final ModifyStatusBarNotification... modifies) {
		LinkedList<StatusBarNotification> queue = map.get(key);
		if (queue == null) return;
		StatusBarNotification sbn = queue.getLast();
		if (fillInExtras != null) sbn.getNotification().extras.putAll(fillInExtras);
		for (ModifyStatusBarNotification modify : modifies) modify.modify(sbn);
		recastNotification(sbn);
	}
}
