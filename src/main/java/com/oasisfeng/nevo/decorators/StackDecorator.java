
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

package com.oasisfeng.nevo.decorators;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.oasisfeng.nevo.sdk.Decorating;
import com.oasisfeng.nevo.sdk.Decorator;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.R;

import static android.app.Notification.EXTRA_BIG_TEXT;
import static android.app.Notification.EXTRA_TEXT;

/** @author Oasis */
@Decorator(title = R.string.decorator_stack_title, description = R.string.decorator_stack_description, priority = -20)
public class StackDecorator extends NevoDecoratorService {

	private static final int KMaxNumLines = 10;

	@Override public SystemUIDecorator createSystemUIDecorator() {
		return new SystemUIDecorator(this.prefKey) {
			@Override public Decorating onNotificationPosted(final StatusBarNotification sbn) {
				final Notification n = sbn.getNotification();
				final String template = n.extras.getString(Notification.EXTRA_TEMPLATE);
				if (template != null && ! template.equals(TEMPLATE_BIG_TEXT)) return Decorating.Unprocessed;		// Skip except for BigTextStyle.

				final Collection<StatusBarNotification> history = getArchivedNotifications(sbn.getKey());
				if (history.size() <= 1) return Decorating.Unprocessed;

				final List<CharSequence> lines = new ArrayList<>(KMaxNumLines);
				for (final StatusBarNotification sbn0 : history) {
					final Bundle extras = sbn0.getNotification().extras;
					final CharSequence text = template == null ? extras.getCharSequence(EXTRA_TEXT) : extras.getCharSequence(EXTRA_BIG_TEXT);
					if (text != null) lines.add(text);
					if (lines.size() >= KMaxNumLines) break;
				}
				if (lines.isEmpty()) return Decorating.Unprocessed;
				Collections.reverse(lines);			// Latest first, since earliest lines will be trimmed by InboxStyle.

				n.extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_INBOX);
				n.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, lines.toArray(new CharSequence[lines.size()]));
				final CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
				if (title != null) n.extras.putCharSequence(Notification.EXTRA_TITLE_BIG, title);
				return Decorating.Processed;
			}
		};
	}
}
