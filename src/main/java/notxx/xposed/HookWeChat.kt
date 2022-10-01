package notxx.xposed

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.util.Log

import de.robv.android.xposed.callbacks.XC_LoadPackage

import notxx.wechat.Messages
import notxx.xposed.hook.Auto as ForAuto
import notxx.xposed.hook.FileOutputStream as ForFileOutputStream

object HookWeChat {
	private const val TAG = "HookWeChat"
	private const val PRIMARY_COLOR = 0xFF33B332.toInt()

	private var mNM: NotificationManager? = null
	private val forFOS = ForFileOutputStream()
	private val forAuto = ForAuto()
	private val messages = Messages()

	fun cancel(id:Int) {
		val nm = mNM
		if (nm != null) {
			nm.cancel(null, id)
		} else {
			Log.d(TAG, "cancel($id) with null NM")
		}
	}

	fun recast(id:Int, n:Notification) {
		val nm = mNM
		if (nm != null) {
			nm.notify(null, id, n)
		} else {
			Log.d(TAG, "recast($id) with null NM")
		}
	}

	fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
		val cl = lpparam.classLoader
		val clazz = cl.findClassIfExists("android.app.NotificationManager")
		XLog.d(TAG, "NM clazz: $clazz process: ${lpparam.processName}")
		if (clazz != null) {
			clazz.hookMethod("notify", String::class.java, Integer.TYPE, Notification::class.java) {
				doBefore {
					val nm = thisObject as NotificationManager
					if (mNM == null) { mNM = nm }
					val tag = args[0] as String?
					val id = args[1] as Int
					val n = args[2] as Notification
					Log.d(TAG, "before apply $nm $tag $id $n process: ${lpparam.processName}")
					apply(nm, tag, id, n)
				}
			}
		}
		forAuto.hook(lpparam)
		forFOS.hook(lpparam)
	}

	fun apply(nm:NotificationManager, tag:String?, id:Int, n:Notification) {
		// mWeChatTargetingO
		// cache
		val conversation = messages.conversation(n)
		if (conversation != null) {
			messages.process(id, n, conversation)
		} else {
			messages.process(id, n)
		}
		Log.d(TAG, "${n.channelId}")
		// deleteIntent
		val extras = n.extras
		// val extensions = extras.getBundle("android.car.EXTENSIONS")
		// if (extensions != null) {
		// 	val conversation = extensions.getBundle("car_conversation")
		// 	// Log.d(TAG, "$conversation")
		// 	val participants = conversation?.get("participants") as? Array<String>
		// 	if (participants != null) {
		// 		for (participant in participants) {
		// 			Log.d(TAG, "participant $participant")
		// 		}
		// 	}
		// 	val messages = conversation?.get("messages") as? Array<android.os.Parcelable>
		// 	if (messages != null) {
		// 		for (message in messages) {
		// 			Log.d(TAG, "message $message")
		// 		}
		// 	}
		// 	n.deleteIntent = conversation?.get("on_read") as PendingIntent?
		// }
		// title
		val title = extras.getCharSequence(Notification.EXTRA_TITLE)
		if (title == null || title.length == 0) return
		// emoji
		n.color = PRIMARY_COLOR
		// channel
		// text
		val text = extras.getCharSequence(Notification.EXTRA_TEXT)
		if (text != null && text.length > 0) {
			// [2条]...
			// 撤回
			// 发送者
			// 群消息
		}
		// 更新会话
		// 显示图片
		// 显示会话
		// 选择会话渠道
		// 维护会话渠道
	}
}