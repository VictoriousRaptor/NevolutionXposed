package notxx.NevolutionXposed;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Deng on 2018/10/20.
 */
public class MainHook implements IXposedHookLoadPackage {
	private final NevoDecoratorService[] services = new NevoDecoratorService[] {
		new com.oasisfeng.nevo.decorators.BigTextDecorator(),
		new com.notxx.miui.MIUIDecorator()
	};

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		if ("com.android.systemui".equals(loadPackageParam.packageName))
			try {
				final Class<?> clazz = XposedHelpers.findClass("com.android.systemui.ForegroundServiceControllerImpl", loadPackageParam.classLoader);
				XposedBridge.log("FSC clazz: " + clazz);
				XposedHelpers.findAndHookMethod(clazz, "updateNotification", StatusBarNotification.class, int.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						StatusBarNotification sbn = (StatusBarNotification)param.args[0];
						int importance = (int)param.args[1];
						apply(sbn, importance);
					}
				});
			} catch (Throwable e) { XposedBridge.log("ForegroundServiceControllerImpl hook failed"); }
		// if ("com.android.settings".equals(loadPackageParam.packageName))
			try {
				final Class<?> clazz = XposedHelpers.findClass("android.app.ContextImpl", loadPackageParam.classLoader);
				XposedBridge.log("CI clazz: " + clazz);
				XposedHelpers.findAndHookMethod(clazz, "createApplicationContext", android.content.pm.ApplicationInfo.class, int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Context context = (Context)param.getResult();
						Log.d("MainHook", "context: " + context);
						NevoDecoratorService.setAppContext(context);
					}
				});
			} catch (Throwable e) { XposedBridge.log("ContextImpl hook failed"); }
	}

	private void apply(StatusBarNotification sbn, int importance) {
		for (NevoDecoratorService srv : services)
			srv.callApply(sbn);
	}
}
