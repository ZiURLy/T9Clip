package com.ziur.t9clip;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Entry implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        String str = loadPackageParam.packageName;
        try {
            if ("org.fcitx.fcitx5.android".equals(str)) {
                FcitxT9Fix.install(loadPackageParam.classLoader);
                ClipboardImageCard.install(loadPackageParam.classLoader);
                ToolbarImageChip.install(loadPackageParam.classLoader);
                ToolbarCustomizer.install(loadPackageParam.classLoader);
                return;
            }
            if (ScreenshotClip.TARGETS.contains(str) && loadPackageParam.isFirstApplication) {
                ScreenshotClip.install(loadPackageParam.processName);
            }
        } catch (Throwable th) {
            L.e("初始化失败（" + str + "）", th);
        }
    }
}
