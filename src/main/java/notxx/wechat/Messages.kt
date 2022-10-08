package notxx.wechat

import android.app.Notification
import android.app.Notification.CarExtender.UnreadConversation
import android.app.Notification.MessagingStyle
import android.app.Notification.MessagingStyle.Message
import android.app.Notification.EXTRA_CONVERSATION_TITLE
import android.app.Notification.EXTRA_MESSAGES
import android.app.Notification.EXTRA_SELF_DISPLAY_NAME
import android.app.Notification.EXTRA_TEMPLATE
import android.app.Person
import android.os.Bundle
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.Parcelable
import android.text.SpannableString
import android.util.Log
import android.util.LruCache

import java.util.SortedMap

private const val TAG = "WeChat.Messages"
private const val SENDER_SEPARATOR = ": "

private const val THREAD_MAX = 10

class Messages {
	companion object {
		private val UNREAD_REGEX = "^\\[(\\d{1,4})条\\]".toRegex()
		private val RECALL_REGEX = "(\"(?<recaller>[^\"]+)\" )?撤回了?一条消息".toRegex()

		private val personCache = LruCache<String, Person>(100)
		private val threadCache = LruCache<Int, MutableList<Crumb>>(100)

		private fun parse_unread(n: Notification, text: CharSequence) {
			val unread_match = UNREAD_REGEX.find(text)
			if (unread_match != null) {
				val group = unread_match.groups[1]
				if (group != null) {
					n.number = Integer.parseInt(group.value)
				}
			}
		}
	}

	private fun process_internal(n: Notification) {
		val tickerText = n.tickerText; val text = n.text; val title = n.title
		if (text == null || title == null) {
			// ??
			Log.d(TAG, "0?? tickerText: $tickerText text: $text title: $title")
		} else if (tickerText == null) { // 撤回？
			n.type = MessageType.RECALL
			val recall_match = RECALL_REGEX.find(text)
			if (recall_match != null) {
				val group = recall_match.groups[2]
				// Log.d(TAG, "recall_match ${recall_match.range} ${recall_match.value}")
				// for (group in recall_match.groups) {
				// 	Log.d(TAG, "${group?.range} ${group?.value}")
				// }
				n.content = recall_match.groups[0]?.value
				if (group != null) {
					// Log.d(TAG, "group-2 ${group.range} ${group.value}")
					// n.type = MessageType.GROUP_RECALL_MAYBE
					n.person = group.value
				} else {
					// n.type = MessageType.PRIVATE_RECALL
					n.person = title
				}
			} else {
				Log.d(TAG, "1??")
			}
			parse_unread(n, text)
			// Log.d(TAG, "unread: ${n.unread} type: ${n.type} title: $title tickerText: $tickerText text: $text")
		} else { // tickerText != null && text != null && title != null
			n.type = MessageType.MESSAGE
			val pos = text.indexOf(tickerText.toString())
			if (tickerText.length + pos == text.length) {
				val r = tickerText.toString().split(SENDER_SEPARATOR, ignoreCase = false, limit = 2)
				if (tickerText.startsWith(title.toString(), SENDER_SEPARATOR)) {
					// n.type = MessageType.PRIVATE_MESSAGE
					n.person = title
					n.content = r[1]
				} else {
					// n.type = MessageType.GROUP_MESSAGE
					n.person = r[0]
					n.content = r[1]
				}
			} else if ("$title$SENDER_SEPARATOR$text" == tickerText) { // 第一条私信 or 公众号/服务号
				// n.type = MessageType.PRIVATE_MESSAGE
				n.person = title
				n.content = text
			} else {
				Log.d(TAG, "2?？")
			}
			parse_unread(n, text)
			// Log.d(TAG, "unread: ${n.unread} type: ${n.type} title: $title tickerText: $tickerText text: $text")
		}
		// Log.d(TAG, "title: $title tickerText: $tickerText text: $text")
		// Log.d(TAG, "unread: ${n.number} type: ${n.type} person: ${n.person} content: ${n.content}")
	}

	private fun find_person(key: String,
			fail: PersonPredicate = { false },
			build: PersonBuilder? = { Person.Builder().setName(key).build() }): Person {
		var person = personCache.get(key)
		if (person == null || fail(person)) {
			person = build?.invoke()
			personCache.put(key, person)
			// Log.d(TAG, "key $key person $person ${person.icon}")
		}
		return person
	}

	private fun export_conversation(extras: Bundle, participant: Person, thread: List<Crumb>) {
		@Suppress("DEPRECATION")
		extras.putCharSequence(EXTRA_SELF_DISPLAY_NAME, participant.name)
		// Log.d(TAG, "participantPerson $participantPerson ${participantPerson.name}")
		if (SDK_INT >= P) extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, participant); // Not included in NotificationCompat
		extras.putCharSequence(EXTRA_CONVERSATION_TITLE, participant.name);
		// Log.d(TAG, "thread ${thread.size}")
		if (!thread.isEmpty()) extras.putParcelableArray(EXTRA_MESSAGES, thread.toParcelableArray())
		//if (! mHistoricMessages.isEmpty()) extras.putParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES, MessagingBuilder.getBundleArrayForMessages(mHistoricMessages))
		val first = thread.find() { crumb ->
			crumb.senderName != participant.name
		}
		if (first != null) extras.isGroupConversation = true
		extras.putString(EXTRA_TEMPLATE, MessagingStyle::class.java.name)
	}

	fun conversation(n: Notification): UnreadConversation? {
		val extender = Notification.CarExtender(n)
		return extender.unreadConversation
		// return n.extras.getBundle("android.car.EXTENSIONS") != null
	}

	fun process(id:Int, n: Notification) {
		process_internal(n)
	}

	fun process(id:Int, n: Notification, c: UnreadConversation) {
		// Log.d(TAG, "n.extras ${n.extras}")
		// TODO 如果消息发太快会造成 UnreadConversation 中未读消息来的比 Notification 快，需要更狠的活来修复
		val messages = c.messages.dropLast(1) // 总消息减一个
		// Log.d(TAG, "messages.size ${messages.size}")
		val last_line = c.messages.last().toLine() // 总消息最后一个
		// Log.d(TAG, "last_line $last_line")
		process_internal(n)
		// Log.d(TAG, "c.latestTimestamp ${c.latestTimestamp}")
		val timestamp = c.latestTimestamp
		// Log.d(TAG, "c.participant ${c.participant}")
		val participant = c.participant
		val participantPerson = find_person(participant, { it?.icon == null }, {
			Person.Builder().setIcon(n.getLargeIcon()).setName(participant).build()
		})
		var thread = threadCache.get(id)
		if (thread == null) {
			thread = mutableListOf<Crumb>()
			threadCache.put(id, thread)
		}
		val threadCount = thread.size; val messageCount = messages.size
		if (n.type == MessageType.RECALL) { // 当撤回发生的时候，messages会变得很短
			// val recaller = n.person
			val offset = threadCount - messageCount
			Log.d(TAG, "recall $offset $threadCount $messageCount")
			if (threadCount < messageCount) {
				thread.forEachIndexed { i, crumb ->
					val j = i - offset
					val line = messages[j].toLine()
					// Log.d(TAG, "$i $j ${crumb.person} ${crumb.content} $line")
					if (line.second == "[消息]" && line.first == null || line.first == participant) { // 撤回 TODO
						// Log.d(TAG, "recall? ${line.first} $participant")
						crumb.isRecall = true
					}
				}
			} else {
				messages.forEachIndexed { j, message ->
					val line = message.toLine()
					val i = j + offset
					val crumb = thread[i]
					// Log.d(TAG, "$i $j ${crumb.person} ${crumb.content} $line")
					if (line.second == "[消息]" && line.first == null || line.first == participant) { // 撤回 TODO
						// Log.d(TAG, "recall? ${line.first} $participant")
						crumb.isRecall = true
					}
				}
			}
		}
		// Log.d(TAG, "counts@0: $messageCount, $threadCount")
		synchronized(thread) {
			if (messageCount <= threadCount) return@synchronized
			// 补齐历史
			for (i in 0..(messageCount - threadCount - 1)) {
				val line = messages[i].toLine()
				val crumb = Crumb(timestamp, line, participant)
				// Log.d(TAG, "add($i, ...) $line ${crumb.senderName} ${crumb.content}")
				thread.add(i, crumb)
			}
		}
		if (n.type != MessageType.RECALL) { // 不是撤回，补齐最后一条消息
			var crumb = Crumb(timestamp, n)
			thread.add(crumb) // 添加历史记录
			// Log.d(TAG, "add(...) ${crumb.senderName} ${crumb.content}")
			crumb.senderPerson = find_person(last_line.first ?: participant)
			if (last_line.second != "[消息]") {
				crumb.content = last_line.second
			}
			// Log.d(TAG, "add(...) $last_line ${crumb.senderName} ${crumb.content}")
			// Log.d(TAG, "counts@1: ${messages.size}, ${thread.size}")
			while (thread.size > (messageCount + 1)) thread.removeAt(0)
		}
		// Log.d(TAG, "counts@2: ${messages.size}, ${thread.size}")
		export_conversation(n.extras, participantPerson, thread)
		n.text = n.tickerText // TODO
	}
}

class Crumb {
	var type: MessageType?
	var title: CharSequence?
	var tickerText: CharSequence?
	var text: CharSequence?
	// var pair: Pair<String?, String>?
	var remoteInputHistory: Array<CharSequence>?
	var content: CharSequence?
	var isRecall = false

	var timestamp: Long
	var senderName: CharSequence? = null
	var senderPerson: Person? = null
	var dataMimeType: String? = null
	var dataUri: android.net.Uri? = null
	// var extras: Bundle? = null

	constructor(timestamp: Long, n: Notification) {
		this.timestamp = timestamp
		this.type = n.type
		this.title = n.title
		this.tickerText = n.tickerText
		this.text = n.text
		// this.pair = null
		this.remoteInputHistory = n.remoteInputHistory
		this.senderName = n.person
		this.content = n.content
		// this.message = null
	}

	constructor(timestamp: Long, pair: Pair<String?, String>, name: CharSequence) {
		this.timestamp = timestamp
		this.type = null
		this.title = null
		this.tickerText = null
		this.text = null
		// this.pair = pair
		this.remoteInputHistory = null
		this.senderName = pair.first ?: name
		this.content = pair.second
		// this.message = null
	}

	fun text() = if (isRecall) { "[撤回] $content" } else { content }

	fun export(output: MutableList<Parcelable>) {
		val bundle = Bundle()
		bundle.putCharSequence(KEY_TEXT, text())
		bundle.putLong(KEY_TIMESTAMP, timestamp)		// Must be included even for 0
		if (senderName != null) bundle.putCharSequence(KEY_SENDER, senderName)	// Legacy listeners need this
		// Log.d(TAG, "senderPerson $senderPerson $senderName")
		if (SDK_INT >= P && senderPerson != null) bundle.putParcelable(KEY_SENDER_PERSON, senderPerson)
		if (dataMimeType != null) bundle.putString(KEY_DATA_MIME_TYPE, dataMimeType)
		if (dataUri != null) bundle.putParcelable(KEY_DATA_URI, dataUri)
		// if (SDK_INT >= O && !extras.isEmpty()) bundle.putBundle(KEY_EXTRAS_BUNDLE, extras)
		//if (this.isRemoteInputHistory()) bundle.putBoolean(KEY_REMOTE_INPUT_HISTORY, this.isRemoteInputHistory());
		output.add(bundle)
		remoteInputHistory?.forEach { text ->
			val bundle = Bundle()
			bundle.putCharSequence(KEY_TEXT, text)
			bundle.putLong(KEY_TIMESTAMP, timestamp)		// Must be included even for 0
			bundle.putCharSequence(KEY_SENDER, null)
			output.add(bundle)
		}
	}
}

typealias PersonPredicate = (Person?) -> Boolean
typealias PersonBuilder = () -> Person

enum class MessageType {
	MESSAGE,
	RECALL,
}

// CharSequence extensions
fun CharSequence.startsWith(needle1: String, needle2: String): Boolean {
	// Log.d("Message", "'$this'.startsWith('$needle1', '$needle2')")
	val index1 = this.indexOf(needle1)
	// Log.d("Message", "index1: $index1")
	if (index1 != 0) return false
	val start = needle1.length
	val index2 = this.indexOf(needle2, start)
	// Log.d("Message", "index2: $index2")
	return index2 == start
}
fun CharSequence.strike(): SpannableString {
	val s = SpannableString(this)
	s.setSpan(android.text.style.StrikethroughSpan(), 0, s.length - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
	return s
}
fun CharSequence.underline(): SpannableString {
	val s = SpannableString(this)
	s.setSpan(android.text.style.UnderlineSpan(), 0, s.length - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
	return s
}

// String extensions
fun String.toLine(): Pair<String?, String> {
	val r = this.split(SENDER_SEPARATOR, ignoreCase = false, limit = 2)
	return if (r.size == 2) {
		Pair(r[0], r[1])
	} else {
		Pair(null, this)
	}
}

fun List<Crumb>.toParcelableArray(/*userSelf: Person*/): Array<Parcelable> {
	val result = mutableListOf<Parcelable>()
	for (crumb in this) {
		crumb.export(/*userSelf, */result)
	}
	return result.toTypedArray()
}