package notxx.xposed.hook

import android.os.Bundle

import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

import com.oasisfeng.nevo.decorators.wechat.WeChatDecorator.EXTRA_PICTURE_PATH
import com.oasisfeng.nevo.xposed.BuildConfig

import notxx.xposed.hookConstructor
import notxx.xposed.hookMethod
import notxx.xposed.Hook
import notxx.xposed.XLog

class FileOutputStream : Hook {
	companion object {
		private const val TAG = "Hook.FileOutputStream"
		private const val PATH = "path"
		private const val CREATED = "created"
		fun now() = System.currentTimeMillis()
	}

	private var mPath: String? = null
	private var mCreated: Long? = null
	private var mClosed: Long? = null

	fun export(msg: String, extras: Bundle) {
		val closed = mClosed;
		if ("[图片]" == msg && mPath != null && closed != null && closed - now() < 1000) {
			synchronized (this) {
				if (BuildConfig.DEBUG) XLog.d(TAG, "putString $mPath")
				extras.putString(EXTRA_PICTURE_PATH, mPath) // 保存图片地址
				mPath = null
				mClosed = null
			}
		}
	}

	override fun hook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
		val clazz = java.io.FileOutputStream::class.java
		// FileOutputStream(String name, boolean append)
		clazz.hookConstructor(String::class.java, java.lang.Boolean.TYPE) {
			doBefore {
				val path = args[0] as String?
				if (path == null) return@doBefore
				val created = now()
				if (path.endsWith("/test_writable") || path.endsWith("/xlogtest_writable")) return@doBefore
				if (!path.contains("/image2/")) {
					// XLog.d(TAG, "$created (file, append) ? $path")
					return@doBefore
				}
				XposedHelpers.setAdditionalInstanceField(thisObject, PATH, path)
				XposedHelpers.setAdditionalInstanceField(thisObject, CREATED, created)
				XLog.d(TAG, "created: $created path: $path")
			}
		}
		clazz.hookMethod("close") {
			doAfter {
				val path = XposedHelpers.getAdditionalInstanceField(thisObject, PATH) as String?
				if (path == null) return@doAfter
				val created = XposedHelpers.getAdditionalInstanceField(thisObject, CREATED) as Long
				val closed = now()
				if (BuildConfig.DEBUG) XLog.d(TAG, "$created => $closed $path")
				synchronized (this) {
					mPath = path
					mCreated = created
					mClosed = closed
				}
			}
		}
	}
}