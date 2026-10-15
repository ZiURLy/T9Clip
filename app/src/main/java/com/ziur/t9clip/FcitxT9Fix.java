package com.ziur.t9clip;

import android.os.Parcel;
import android.view.inputmethod.EditorInfo;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

final class FcitxT9Fix {
    private static final String KEYBOARD_WINDOW = "org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow";
    static final String PACKAGE = "org.fcitx.fcitx5.android";
    private static boolean installed;
    private static final Set<String> LOGGED = new HashSet();
    private static final XC_MethodHook LAYOUT_HOOK = new XC_MethodHook() {
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Object obj = methodHookParam.args[1];
            if (obj instanceof EditorInfo) {
                EditorInfo editorInfo = (EditorInfo) obj;
                int iFixedInputType = InputTypeFix.fixedInputType(editorInfo.inputType);
                if (iFixedInputType < 0) {
                    return;
                }
                methodHookParam.args[1] = FcitxT9Fix.copyWithInputType(editorInfo, iFixedInputType);
                FcitxT9Fix.logOnce("已纠正，保持九键", editorInfo);
            }
        }

        protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Object obj = methodHookParam.args[1];
            if ((obj instanceof EditorInfo) && "Text".equals(methodHookParam.getResult())) {
                EditorInfo editorInfo = (EditorInfo) obj;
                if (InputTypeFix.isRealPassword(editorInfo.inputType)) {
                    FcitxT9Fix.logOnce("密码框，保持26键", editorInfo);
                }
            }
        }
    };

    private FcitxT9Fix() {
    }

    static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            int i = 0;
            for (Method method : Class.forName(KEYBOARD_WINDOW, false, classLoader).getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if ("preferredTextLayout".equals(method.getName()) && parameterTypes.length == 2 && parameterTypes[1] == EditorInfo.class) {
                    XposedBridge.hookMethod(method, LAYOUT_HOOK);
                    i++;
                }
            }
            if (i == 0) {
                L.i("没找到 preferredTextLayout，可能不是 fcitx5-android-t9 v0.1.0，九键修复未生效");
            } else {
                L.i("九键修复已生效（preferredTextLayout ×" + i + "）");
            }
        } catch (Throwable th) {
            L.i("输入法里找不到 KeyboardWindow，九键修复跳过（" + th + "）");
        }
    }

    static EditorInfo copyWithInputType(EditorInfo editorInfo, int i) {
        EditorInfo editorInfo2;
        Parcel parcelObtain = Parcel.obtain();
        try {
            try {
                editorInfo.writeToParcel(parcelObtain, 0);
                parcelObtain.setDataPosition(0);
                editorInfo2 = (EditorInfo) EditorInfo.CREATOR.createFromParcel(parcelObtain);
            } finally {
                parcelObtain.recycle();
            }
        } catch (Throwable unused) {
            editorInfo2 = new EditorInfo();
            editorInfo2.imeOptions = editorInfo.imeOptions;
            editorInfo2.privateImeOptions = editorInfo.privateImeOptions;
            editorInfo2.actionLabel = editorInfo.actionLabel;
            editorInfo2.actionId = editorInfo.actionId;
            editorInfo2.initialSelStart = editorInfo.initialSelStart;
            editorInfo2.initialSelEnd = editorInfo.initialSelEnd;
            editorInfo2.initialCapsMode = editorInfo.initialCapsMode;
            editorInfo2.hintText = editorInfo.hintText;
            editorInfo2.label = editorInfo.label;
            editorInfo2.packageName = editorInfo.packageName;
            editorInfo2.fieldId = editorInfo.fieldId;
            editorInfo2.fieldName = editorInfo.fieldName;
            editorInfo2.extras = editorInfo.extras;
        }
        editorInfo2.inputType = i;
        return editorInfo2;
    }

    private static void logOnce(String what, EditorInfo editorInfo) {
        String key = what + '|' + editorInfo.packageName + '|' + editorInfo.inputType;
        synchronized (LOGGED) {
            if (LOGGED.size() > 300 || !LOGGED.add(key)) {
                return;
            }
        }
        L.i(what + "：" + editorInfo.packageName + " inputType=0x" + Integer.toHexString(editorInfo.inputType));
    }
}
