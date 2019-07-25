package notxx.NevolutionXposed;

import android.service.notification.StatusBarNotification;

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
	private final NevoDecoratorService srv = new com.oasisfeng.nevo.decorators.BigTextDecorator();

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
	}

	private void apply(StatusBarNotification sbn, int importance) {
		srv.callApply(sbn);
	}
}
