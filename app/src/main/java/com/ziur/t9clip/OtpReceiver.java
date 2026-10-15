package com.ziur.t9clip;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Runs in the input method. Only the system (which holds WRITE_SECURE_SETTINGS) can send
 * the broadcast, so ordinary apps cannot put text on the clipboard through it.
 */
final class OtpReceiver {
    static final String LABEL = "验证码";
    private static boolean registered;

    private OtpReceiver() {
    }

    static synchronized void register(Context context) {
        if (registered) {
            return;
        }
        try {
            Context app = context.getApplicationContext();
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    try {
                        String code = intent.getStringExtra(OtpNotifier.EXTRA_CODE);
                        if (code == null || !code.matches("[0-9A-Za-z]{4,8}")) {
                            return;
                        }
                        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText(LABEL, code));
                            L.i("验证码已复制到剪贴板");
                        }
                    } catch (Throwable t) {
                        L.e("验证码：写入剪贴板失败", t);
                    }
                }
            };
            app.registerReceiver(receiver, new IntentFilter(OtpNotifier.ACTION), OtpNotifier.SENDER_PERMISSION,
                    ImageSender.MAIN, Context.RECEIVER_EXPORTED);
            registered = true;
        } catch (Throwable t) {
            L.e("验证码：注册接收器失败", t);
        }
    }
}
