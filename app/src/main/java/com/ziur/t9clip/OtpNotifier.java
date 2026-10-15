package com.ziur.t9clip;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs in the system process. When Google Messages posts a notification that holds a
 * verification code, the code is handed to the input method, which puts it on the
 * clipboard (the input method is allowed to, even in the background).
 */
final class OtpNotifier {
    static final String ACTION = "com.ziur.t9clip.OTP";
    static final String EXTRA_CODE = "code";
    static final String IME = "org.fcitx.fcitx5.android";
    static final String SENDER_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String MESSAGES = "com.google.android.apps.messaging";
    private static final long MAX_AGE_MS = 120_000L;
    private static final Pattern KEY = Pattern.compile("验证码|校验码|动态码|动态密码|安全码|确认码|随机码|验证|口令|code|otp|passcode|pin", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUM = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");
    private static boolean installed;
    private static String lastKey = "";

    private OtpNotifier() {
    }

    static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Class<?> service = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", classLoader);
            int n = XposedBridge.hookAllMethods(service, "enqueueNotificationInternal", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam p) {
                    try {
                        if (onNotification(p.args)) {
                            p.setResult(null); // verification-code message: do not show the notification
                        }
                    } catch (Throwable t) {
                        L.e("验证码：处理通知失败", t);
                    }
                }
            }).size();
            L.i("验证码自动复制已生效（挂接 " + n + " 处）");
        } catch (Throwable t) {
            L.e("验证码：挂接通知服务失败", t);
        }
    }

    /** Returns true when the notification held a verification code and has been handled. */
    private static boolean onNotification(Object[] args) throws Throwable {
        if (args == null || args.length == 0 || !MESSAGES.equals(args[0])) {
            return false;
        }
        Notification notification = null;
        for (Object a : args) {
            if (a instanceof Notification) {
                notification = (Notification) a;
                break;
            }
        }
        if (notification == null || notification.extras == null) {
            return false;
        }
        long[] when = new long[1];
        String text = latestText(notification, when);
        if (text == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (when[0] > 0 && now - when[0] > MAX_AGE_MS) {
            return false;
        }
        String code = extractCode(text);
        if (code == null) {
            return false;
        }
        String key = code + "@" + when[0];
        synchronized (OtpNotifier.class) {
            if (key.equals(lastKey)) {
                return true; // the same message posted again
            }
            lastKey = key;
        }
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Intent intent = new Intent(ACTION).setPackage(IME).putExtra(EXTRA_CODE, code);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // We are on a binder thread serving Google Messages; without this the broadcast
        // would be sent with that app's identity and the permission check would reject it.
        long token = android.os.Binder.clearCallingIdentity();
        try {
            context.sendBroadcast(intent);
        } finally {
            android.os.Binder.restoreCallingIdentity(token);
        }
        L.i("收到验证码短信，已交给输入法，通知不再显示");
        return true;
    }

    /** The newest message in the notification, or null. */
    private static String latestText(Notification notification, long[] when) {
        Bundle extras = notification.extras;
        try {
            android.os.Parcelable[] messages = extras.getParcelableArray("android.messages");
            if (messages != null && messages.length > 0 && messages[messages.length - 1] instanceof Bundle) {
                Bundle last = (Bundle) messages[messages.length - 1];
                CharSequence t = last.getCharSequence("text");
                when[0] = last.getLong("time", 0L);
                if (t != null) {
                    return t.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        when[0] = notification.when;
        CharSequence big = extras.getCharSequence("android.bigText");
        if (big != null) {
            return big.toString();
        }
        CharSequence text = extras.getCharSequence("android.text");
        return text == null ? null : text.toString();
    }

    static String extractCode(String text) {
        Matcher key = KEY.matcher(text);
        if (!key.find()) {
            return null;
        }
        Matcher num = NUM.matcher(text);
        if (num.find(key.end()) && num.start() - key.end() <= 24) {
            return num.group();
        }
        return num.find(0) ? num.group() : null;
    }
}
