package com.oasisfeng.nevo.sdk;

import android.service.notification.StatusBarNotification;
import android.support.annotation.Keep;

public abstract class NevoDecoratorService {
	/** Valid constant values for {@link android.app.Notification#EXTRA_TEMPLATE} */
	public static final String TEMPLATE_BIG_TEXT	= "android.app.Notification$BigTextStyle";
	public static final String TEMPLATE_INBOX		= "android.app.Notification$InboxStyle";
	public static final String TEMPLATE_BIG_PICTURE	= "android.app.Notification$BigPictureStyle";
	public static final String TEMPLATE_MEDIA		= "android.app.Notification$MediaStyle";
	public static final String TEMPLATE_MESSAGING	= "android.app.Notification$MessagingStyle";

	public void callApply(final StatusBarNotification sbn) {
		apply(sbn);
	}
	@Keep protected void apply(final StatusBarNotification evolving) {}
}