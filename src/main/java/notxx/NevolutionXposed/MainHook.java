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
		final XC_MethodHook notifications = new XC_MethodHook() { // 实际抓通知的
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					StatusBarNotification sbn = (StatusBarNotification)param.args[0];
					// RankingMap rankingMap = (RankingMap)param.args[1];
					apply(sbn);
				}
		}, nls = new XC_MethodHook() { // 捕获NotificationListenerService的具体实现
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				// XposedBridge.log("nls constructor " + param.thisObject);
				try {
					final Class<?> clazz = param.thisObject.getClass();
					XposedBridge.log("NL clazz: " + clazz + " " + loadPackageParam.packageName);
					XposedBridge.hookAllMethods(clazz, "onNotificationPosted", notifications);
				} catch (Throwable e) { XposedBridge.log("StatusBar hook failed "); }
			}
		};
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.service.notification.NotificationListenerService", loadPackageParam.classLoader);
			XposedBridge.hookAllConstructors(clazz, nls);
		} catch (Throwable e) { XposedBridge.log("NotificationListenerService hook failed "); }
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
		miui.onCreate();
	}

	private void apply(StatusBarNotification sbn) {
		if ("com.tencent.mm".equals(sbn.getPackageName())) {
			wechat.apply(sbn);
		} else if ("com.xiaomi.xmsf".equals(sbn.getPackageName())) {
			miui.apply(sbn);
		} else { }
	}
}
