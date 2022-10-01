package notxx.xposed.hook

import de.robv.android.xposed.callbacks.XC_LoadPackage

import notxx.xposed.hookAllConstructor
import notxx.xposed.findClassIfExists
import notxx.xposed.hookMethod
import notxx.xposed.Hook
import notxx.xposed.PACKAGE_WECHAT
import notxx.xposed.XLog

class Auto : Hook {
	companion object {
		val TAG = "Hook.Auto"
	}

	private fun hookUiModeManager(cl: ClassLoader) {
		val clazz = cl.findClassIfExists("android.app.UiModeManager")
		if (clazz == null) return
		// XLog.d(TAG, "hookUiModeManager $cl")
		clazz.hookMethod("getCurrentModeType") {
			replace { 3 }
		}
	}

	private fun hookApplicationPackageManager(cl: ClassLoader) {
		val clazz = cl.findClassIfExists("android.app.ApplicationPackageManager")
		if (clazz == null) return
		// XLog.d(TAG, "hookApplicationPackageManager $cl")
		clazz.hookMethod("getPackageInfo", String::class.java, Integer.TYPE) {
			doBefore {
				val pkg = args[0] as String
				if (pkg == "com.google.android.projection.gearhead") {
					this.setResult(null)
				} else if (pkg != PACKAGE_WECHAT) {
					// XLog.d(TAG, "getPackageInfo($pkg)")
				}
			}
		}
	}

	override fun hook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
		hookUiModeManager(loadPackageParam.classLoader)
		hookApplicationPackageManager(loadPackageParam.classLoader)
		ClassLoader::class.java.hookAllConstructor() {
			doAfter {
				val cl = thisObject as ClassLoader
				hookUiModeManager(cl)
				hookApplicationPackageManager(cl)
			}
		}
	}
}