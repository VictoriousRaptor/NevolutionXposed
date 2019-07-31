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
 * hook and manupinate notifications.
 * 
 * @author notXX
 */
public class MainHook implements IXposedHookLoadPackage {
	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();
	private final NevoDecoratorService miui = new com.notxx.miui.MIUIDecorator();

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		if (!"com.android.systemui".equals(loadPackageParam.packageName)) return;
		try {
			final Class<?> clazz = XposedHelpers.findClass("com.android.systemui.statusbar.NotificationListener", loadPackageParam.classLoader);
			XposedBridge.log("NS clazz: " + clazz + " " + loadPackageParam.packageName);
			XposedBridge.hookAllMethods(clazz, "onNotificationPosted", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					StatusBarNotification sbn = (StatusBarNotification)param.args[0];
					// RankingMap rankingMap = (RankingMap)param.args[1];
					// XposedBridge.log("ns " + sbn.getId());
					apply(sbn);
					// param.setResult(null);
				}
			});
		} catch (Throwable e) { XposedBridge.log("NotificationListener hook failed"); }
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

	private void apply(StatusBarNotification sbn) {
		if ("com.tencent.mm".equals(sbn.getPackageName())) {
			wechat.apply(sbn);
		} else if ("com.xiaomi.xmsf".equals(sbn.getPackageName())) {
			miui.apply(sbn);
		} else { }
	}
}
