package com.ziur.t9clip;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class ScreenshotClip {
    private static final long DEBOUNCE_MS = 300;
    private static final long WINDOW_SECONDS = 60;
    private static boolean installed;
    private static boolean started;
    static final Set<String> TARGETS = new HashSet(Arrays.asList("com.oplus.screenshot", "com.coloros.screenshot", "com.oneplus.screenshot", "com.android.systemui"));
    private static final String[] PROJECTION = {"_id", "mime_type", "owner_package_name", "relative_path", "_display_name", "is_pending"};

    private ScreenshotClip() {
    }

    static synchronized void install(final String str) {
        if (installed) {
            return;
        }
        installed = true;
        Application applicationCurrentApplication = currentApplication();
        if (applicationCurrentApplication != null) {
            start(applicationCurrentApplication, str);
            return;
        }
        try {
            XposedBridge.hookMethod(Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class), new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.args[0];
                    if (obj instanceof Application) {
                        ScreenshotClip.start((Application) obj, str);
                    }
                }
            });
        } catch (Throwable th) {
            L.e("截图复制：挂接失败", th);
        }
    }

    private static synchronized void start(Application application, String str) {
        if (started) {
            return;
        }
        started = true;
        try {
            HandlerThread handlerThread = new HandlerThread("T9Clip-screenshot");
            handlerThread.start();
            application.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, new Watcher(application, new Handler(handlerThread.getLooper())));
            L.i("截图复制已启动：" + str);
        } catch (Throwable th) {
            L.e("截图复制：启动失败", th);
        }
    }

    private static Application currentApplication() {
        try {
            Object objInvoke = Class.forName("android.app.ActivityThread").getMethod("currentApplication", new Class[0]).invoke(null, new Object[0]);
            if (objInvoke instanceof Application) {
                return (Application) objInvoke;
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    private static final class Watcher extends ContentObserver {
        private final Application app;
        private final Handler handler;
        private long lastCopiedId;
        private final Runnable scanTask;
        private final String self;

        Watcher(Application application, Handler handler) {
            super(handler);
            this.lastCopiedId = -1L;
            this.scanTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        Watcher.this.scan();
                    } catch (Throwable th) {
                        L.e("截图复制：处理失败", th);
                    }
                }
            };
            this.app = application;
            this.handler = handler;
            this.self = application.getPackageName();
        }

        @Override
        public void onChange(boolean z) {
            schedule();
        }

        @Override
        public void onChange(boolean z, Uri uri) {
            schedule();
        }

        private void schedule() {
            this.handler.removeCallbacks(this.scanTask);
            this.handler.postDelayed(this.scanTask, ScreenshotClip.DEBOUNCE_MS);
        }

        private void scan() {
            ContentResolver contentResolver = this.app.getContentResolver();
            long jCurrentTimeMillis = System.currentTimeMillis() / 1000;
            Bundle bundle = new Bundle();
            bundle.putString("android:query-arg-sql-selection", "date_added>=?");
            bundle.putStringArray("android:query-arg-sql-selection-args", new String[]{Long.toString(jCurrentTimeMillis - ScreenshotClip.WINDOW_SECONDS)});
            bundle.putString("android:query-arg-sql-sort-order", "date_added DESC, _id DESC");
            Cursor cursorQuery = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ScreenshotClip.PROJECTION, bundle, null);
            if (cursorQuery == null) {
                return;
            }
            try {
                int columnIndexOrThrow = cursorQuery.getColumnIndexOrThrow("_id");
                int columnIndexOrThrow2 = cursorQuery.getColumnIndexOrThrow("mime_type");
                int columnIndexOrThrow3 = cursorQuery.getColumnIndexOrThrow("owner_package_name");
                int columnIndexOrThrow4 = cursorQuery.getColumnIndexOrThrow("relative_path");
                int columnIndexOrThrow5 = cursorQuery.getColumnIndexOrThrow("_display_name");
                int columnIndexOrThrow6 = cursorQuery.getColumnIndexOrThrow("is_pending");
                while (cursorQuery.moveToNext()) {
                    if (cursorQuery.getInt(columnIndexOrThrow6) == 0 && isScreenshotFromThisApp(cursorQuery.getString(columnIndexOrThrow3), cursorQuery.getString(columnIndexOrThrow4), cursorQuery.getString(columnIndexOrThrow5))) {
                        long j = cursorQuery.getLong(columnIndexOrThrow);
                        if (j != this.lastCopiedId && claim(j)) {
                            this.lastCopiedId = j;
                            copy(j, cursorQuery.getString(columnIndexOrThrow2));
                            return;
                        }
                        return;
                    }
                }
            } finally {
                cursorQuery.close();
            }
        }

        private boolean isScreenshotFromThisApp(String str, String str2, String str3) {
            if (str != null && !str.isEmpty()) {
                return str.equals(this.self);
            }
            StringBuilder sb = new StringBuilder();
            if (str2 == null) {
                str2 = "";
            }
            sb.append(str2);
            sb.append('/');
            if (str3 == null) {
                str3 = "";
            }
            sb.append(str3);
            String lowerCase = sb.toString().toLowerCase(Locale.ROOT);
            return lowerCase.contains("screenshot") || lowerCase.contains("截屏") || lowerCase.contains("截图");
        }

        /** Returns true if this process is the first to see screenshot {@code id}. Errors count as a claim. */
        private boolean claim(long id) {
            RandomAccessFile file = null;
            FileLock lock = null;
            try {
                file = new RandomAccessFile(new File(this.app.getCacheDir(), "t9clip_last_screenshot"), "rw");
                lock = file.getChannel().lock();
                String last = file.length() > 0 ? file.readLine() : null;
                String current = Long.toString(id);
                if (current.equals(last)) {
                    return false;
                }
                file.setLength(0L);
                file.seek(0L);
                file.write(current.getBytes(StandardCharsets.US_ASCII));
                return true;
            } catch (Throwable th) {
                return true;
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Throwable ignored) {
                    }
                }
                if (file != null) {
                    try {
                        file.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        private void copy(long j, String str) {
            Uri uriWithAppendedId = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, j);
            ClipboardManager clipboardManager = (ClipboardManager) this.app.getSystemService("clipboard");
            if (clipboardManager == null) {
                L.i("截图复制：拿不到剪贴板服务");
                return;
            }
            if (str == null || str.isEmpty()) {
                str = "image/png";
            }
            try {
                clipboardManager.setPrimaryClip(new ClipData(new ClipDescription("截屏", new String[]{str}), new ClipData.Item(uriWithAppendedId)));
                L.i("截图已复制到剪贴板：" + uriWithAppendedId);
            } catch (SecurityException e) {
                L.e("截图复制：这张图不归本进程，无法交给其他 App（" + uriWithAppendedId + "）", e);
            }
        }
    }
}
