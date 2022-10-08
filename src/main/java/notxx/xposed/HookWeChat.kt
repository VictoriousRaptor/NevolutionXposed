package notxx.xposed

import android.app.Notification
import android.app.Notification.Action
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.content.Context
import android.util.Log
import android.util.LruCache

import java.util.concurrent.atomic.AtomicReference

import android.app.AndroidAppHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage

import notxx.wechat.Messages
import notxx.wechat.Proxy
import notxx.wechat.RecastAction
import notxx.xposed.hook.Auto as ForAuto
import notxx.xposed.hook.FileOutputStream as ForFileOutputStream

object HookWeChat {
	private const val TAG = "HookWeChat"
	private const val PRIMARY_COLOR = 0xFF33B332.toInt()

	private var mNM: NotificationManager? = null
	private val forFOS = ForFileOutputStream()
	private val forAuto = ForAuto()
	private val messages = Messages()
	private val cache = LruCache<Int, Notification>(100)
	private val contextRef = AtomicReference<Context>()
	private var proxyRef = AtomicReference<Proxy>()
	private val proxy: Proxy
		get() {
			return proxyRef.get()!!
		}

	private fun cancel(id: Int) {
		val nm = mNM
		if (nm != null) {
			nm.cancel(null, id)
		} else {
			Log.d(TAG, "cancel($id) with null NM")
		}
	}

	private fun recast(id: Int, n: Notification) {
		val nm = mNM
		if (nm != null) {
			nm.notify(null, id, n)
		} else {
			Log.d(TAG, "recast($id) with null NM")
		}
	}

	private fun recast(id: Int, action: RecastAction) {
		val n = cache.get(id)
		if (n != null) {
			action(n)
			Log.d(TAG, "recast $id ${n.extras.getCharSequence(Notification.EXTRA_TITLE)}")
			recast(id, n)
		} else {
			Log.d(TAG, "can not recast $id, so cancel it")
			cancel(id)
		}
	}

	fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
		val cl = lpparam.classLoader
		var clazz = cl.findClassIfExists("android.app.NotificationManager")
		XLog.d(TAG, "NM clazz: $clazz process: ${lpparam.processName}")
		clazz?.hookMethod("notify", String::class.java, Integer.TYPE, Notification::class.java) {
			doBefore {
				val nm = thisObject as NotificationManager
				if (mNM == null) { mNM = nm }
				val tag = args[0] as String?
				val id = args[1] as Int
				val n = args[2] as Notification
				// Log.d(TAG, "before apply $nm $tag $id $n process: ${lpparam.processName}")
				apply(nm, tag, id, n)
			}
		}
		clazz = cl.findClassIfExists("android.app.ContextImpl")
		XLog.d(TAG, "ContextImpl clazz: $clazz process: ${lpparam.processName}")
		clazz?.hookAllMethods("createAppContext") {
			doAfter {
				val context = result as Context
				if (contextRef.compareAndSet(null, context)) {
					Log.d(TAG, "HookWeChat $context")
					proxyRef.compareAndSet(null, Proxy(context, ::cancel, ::recast))
				}
			}
		}
		forAuto.hook(lpparam)
		forFOS.hook(lpparam)
	}

	fun apply(nm:NotificationManager, tag:String?, id:Int, n:Notification) {
		cache.put(id, n)
		// mWeChatTargetingO
		// cache
		Log.d(TAG, "channel: ${n.channelId}")
		// emoji
		// channel
		n.color = PRIMARY_COLOR
		val actions = mutableListOf<Action>()
		// 更新会话
		val conversation = messages.conversation(n)
		if (conversation != null) {
			messages.process(id, n, conversation)
			val read = conversation.readPendingIntent
			if (read != null) {
				// n.deleteIntent = read
				actions.add(proxy.buildReadAction(id, n, read))
				val remoteInput = conversation.remoteInput
				if (SDK_INT >= N && remoteInput != null) {
					actions.add(proxy.buildReplyAction(id, n, conversation))
				}
			}
		} else {
			messages.process(id, n)
		}
		n.setActions(*actions.toTypedArray())
		// 显示图片
		// 显示会话
		// 选择会话渠道
		// 维护会话渠道
	}
}

fun Notification.setActions(vararg actions: Notification.Action) {
	this.set("actions", actions)
}