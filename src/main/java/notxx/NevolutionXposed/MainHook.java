package notxx.NevolutionXposed;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;

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
	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		if (!"com.android.systemui".equals(loadPackageParam.packageName)) return;
			try {
				final Class<?> clazz = XposedHelpers.findClass("com.android.systemui.ForegroundServiceControllerImpl", loadPackageParam.classLoader);
				XposedBridge.log("FSC clazz: " + clazz);
				XposedHelpers.findAndHookMethod(clazz, "updateNotification", StatusBarNotification.class, int.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						StatusBarNotification sbn = (StatusBarNotification)param.args[0];
						int importance = (int)param.args[1];
					XposedBridge.log("apply pid: " + android.os.Process.myPid());
						apply(sbn, importance);
					}
				});
			} catch (Throwable e) { XposedBridge.log("ForegroundServiceControllerImpl hook failed"); }
			try {
				final Class<?> clazz = XposedHelpers.findClass("android.app.ContextImpl", loadPackageParam.classLoader);
				XposedBridge.log("CI clazz: " + clazz);
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedBridge.hookAllMethods(clazz, "createAppContext", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Context context = (Context)param.getResult();
					if (ref.compareAndSet(null, context)) {
						XposedBridge.log("setAppContext");
						onCreate(context);
					}
					}
				});
			} catch (Throwable e) { XposedBridge.log("ContextImpl hook failed"); }
	}

	private void onCreate(Context context) {
		NevoDecoratorService.setAppContext(context);
		wechat.onCreate();
		for (NevoDecoratorService srv : services)
			srv.onCreate();
	}

	private void apply(StatusBarNotification sbn, int importance) {
		if ("com.tencent.mm".equals(sbn.getPackageName())) {
			wechat.apply(sbn);
		} else {
			for (NevoDecoratorService srv : services)
				srv.apply(sbn);
		}
	}
}
