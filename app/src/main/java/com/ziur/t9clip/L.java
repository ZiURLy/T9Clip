package com.ziur.t9clip;

import android.util.Log;
import de.robv.android.xposed.XposedBridge;

final class L {
    private static final String TAG = "[T9Clip] ";

    private L() {
    }

    static void i(String str) {
        try {
            XposedBridge.log(TAG + str);
        } catch (Throwable unused) {
        }
    }

    static void e(String str, Throwable th) {
        i(str + ": " + Log.getStackTraceString(th));
    }
}
