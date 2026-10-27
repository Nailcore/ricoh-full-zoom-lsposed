package dev.codex.ricohfullzoom;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Adds the camera's full focal-length set to the Ricoh GR mode.
 *
 * <p>The GR zoom control shows two focal lengths: the left slot is the first configured
 * focal length, and tapping the right slot cycles through the remaining ones (it wraps).
 * The camera normally configures only 28 mm and 40 mm for GR, so only those two are
 * reachable. Replacing both GR lists with the full set makes every lens reachable.</p>
 */
public final class RicohFullZoomHook implements IXposedHookLoadPackage {
    private static final String TAG = "RicohFullZoom";
    private static final String TARGET_PACKAGE = "com.oplus.camera";
    private static final String TARGET_VERSION = "6.106.782";
    private static final int TARGET_VERSION_CODE = 60000;
    private static final String TARGET_MODEL = "RMX5200";

    private static final String CONFIG_CLASS = "com.oplus.camera.configure.CameraConfig";
    private static final String GR_MARKED_ZOOM = "com.oplus.gr.mode.marked.zoomvalues";
    private static final String GR_AVAILABLE_ZOOM = "com.oplus.available.gr.mode.zoomvalues";

    /**
     * The camera's own five-point focal set: 16 mm ultrawide, 24 mm main,
     * 48 mm 2x, 72 mm 3x and 144 mm 6x. Order must stay ascending.
     */
    private static final String FULL_ZOOM_LIST = "0.6(16),1(24),2(48),3(72),6(144)";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName) || !TARGET_PACKAGE.equals(lpparam.processName)) {
            return;
        }

        final AtomicBoolean installed = new AtomicBoolean(false);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!installed.compareAndSet(false, true)) {
                    return;
                }
                try {
                    Context context = (Context) param.args[0];
                    if (!hasTargetVersion(context)) {
                        log("skip: unsupported camera version or device");
                        return;
                    }
                    installZoomHook(lpparam.classLoader);
                } catch (Throwable throwable) {
                    log("skip: setup failed " + throwable.getClass().getSimpleName());
                }
            }
        });
    }

    private static boolean hasTargetVersion(Context context) throws Exception {
        @SuppressWarnings("deprecation")
        PackageInfo info = context.getPackageManager().getPackageInfo(TARGET_PACKAGE, 0);
        @SuppressWarnings("deprecation")
        int versionCode = info.versionCode;
        return Build.MODEL != null && Build.MODEL.startsWith(TARGET_MODEL)
                && TARGET_VERSION.equals(info.versionName)
                && TARGET_VERSION_CODE == versionCode;
    }

    private static void installZoomHook(ClassLoader classLoader) {
        final Class<?> configClass = XposedHelpers.findClass(CONFIG_CLASS, classLoader);
        XposedHelpers.findAndHookMethod(configClass, "t", String.class, new XC_MethodHook() {
            private final AtomicBoolean loggedMarked = new AtomicBoolean(false);
            private final AtomicBoolean loggedAvailable = new AtomicBoolean(false);

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !(param.args[0] instanceof String)) {
                    return;
                }
                String key = (String) param.args[0];
                if (GR_MARKED_ZOOM.equals(key)) {
                    if (loggedMarked.compareAndSet(false, true)) {
                        log("marked zoom " + param.getResult() + " -> " + FULL_ZOOM_LIST);
                    }
                    param.setResult(FULL_ZOOM_LIST);
                } else if (GR_AVAILABLE_ZOOM.equals(key)) {
                    if (loggedAvailable.compareAndSet(false, true)) {
                        log("available zoom " + param.getResult() + " -> " + FULL_ZOOM_LIST);
                    }
                    param.setResult(FULL_ZOOM_LIST);
                }
            }
        });
        log("hook installed");
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
