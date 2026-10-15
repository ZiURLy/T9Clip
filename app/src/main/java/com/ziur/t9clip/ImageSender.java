package com.ziur.t9clip;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.Toast;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageSender {
    static final String SCREENSHOT_LABEL = "截屏";
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    private static final Set<String> LOGGED_TARGETS = new HashSet();

    interface BitmapCallback {
        void done(Bitmap bitmap);
    }

    private ImageSender() {
    }

    static final class ClipImage {
        final String mime;
        final boolean screenshot;
        final long timestamp;
        final Uri uri;

        ClipImage(Uri uri, String str, boolean z, long j) {
            this.uri = uri;
            this.mime = str;
            this.screenshot = z;
            this.timestamp = j;
        }
    }

    static ClipImage current(Context context) {
        String mimeType;
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService("clipboard");
        ClipData primaryClip = clipboardManager == null ? null : clipboardManager.getPrimaryClip();
        if (primaryClip != null && primaryClip.getItemCount() != 0) {
            boolean z = false;
            Uri uri = primaryClip.getItemAt(0).getUri();
            if (uri != null && "content".equals(uri.getScheme())) {
                ClipDescription description = primaryClip.getDescription();
                for (int i = 0; description != null && i < description.getMimeTypeCount(); i++) {
                    mimeType = description.getMimeType(i);
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        break;
                    }
                }
                mimeType = null;
                if (mimeType == null) {
                    try {
                        String type = context.getContentResolver().getType(uri);
                        if (type != null) {
                            if (type.startsWith("image/")) {
                                mimeType = type;
                            }
                        }
                    } catch (Throwable unused) {
                    }
                }
                if (mimeType == null) {
                    return null;
                }
                if ("image/*".equals(mimeType)) {
                    mimeType = "image/png";
                }
                if (description != null && SCREENSHOT_LABEL.contentEquals(String.valueOf(description.getLabel()))) {
                    z = true;
                }
                return new ClipImage(uri, mimeType, z, description == null ? 0L : description.getTimestamp());
            }
        }
        return null;
    }

    static void loadThumbnail(final Context context, final Uri uri, final int i, final BitmapCallback bitmapCallback) {
        LOADER.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap thumbnail = null;
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    thumbnail = contentResolver.loadThumbnail(uri, new Size(i, i), null);
                } catch (Throwable th) {
                    L.i("读不到剪贴板里的图片（" + th.getClass().getSimpleName() + "）");
                }
                final Bitmap result = thumbnail;
                ImageSender.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        bitmapCallback.done(result);
                    }
                });
            }
        });
    }

    static void send(final InputMethodService inputMethodService, Uri uri, String str) {
        boolean zCommitContent;
        InputConnection currentInputConnection = inputMethodService.getCurrentInputConnection();
        if (currentInputConnection == null) {
            toast(inputMethodService, "现在没有可输入的地方");
            return;
        }
        EditorInfo currentInputEditorInfo = inputMethodService.getCurrentInputEditorInfo();
        String str2 = currentInputEditorInfo == null ? null : currentInputEditorInfo.packageName;
        logTargetOnce(str2, currentInputEditorInfo);
        try {
            zCommitContent = currentInputConnection.commitContent(new InputContentInfo(uri, new ClipDescription(SCREENSHOT_LABEL, new String[]{str})), 1, null);
        } catch (Throwable th) {
            L.e("直接插入图片出错", th);
            zCommitContent = false;
        }
        if (zCommitContent) {
            L.i("图片已直接插入 → " + str2);
            return;
        }
        currentInputConnection.performContextMenuAction(android.R.id.paste);
        L.i(str2 + " 不收直接插入，改为让它粘贴剪贴板");
        final String string = uri.toString();
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                CharSequence textBeforeCursor;
                try {
                    InputConnection currentInputConnection2 = inputMethodService.getCurrentInputConnection();
                    if (currentInputConnection2 == null || (textBeforeCursor = currentInputConnection2.getTextBeforeCursor(string.length(), 0)) == null || !string.contentEquals(textBeforeCursor)) {
                        return;
                    }
                    currentInputConnection2.deleteSurroundingText(string.length(), 0);
                    ImageSender.toast(inputMethodService, "这个输入框只能输入文字，发不了图片");
                } catch (Throwable unused) {
                }
            }
        }, 600L);
    }

    static boolean clearClipboard(Context context) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService("clipboard");
            if (clipboardManager == null) {
                return false;
            }
            clipboardManager.clearPrimaryClip();
            return true;
        } catch (Throwable th) {
            L.e("清除剪贴板失败", th);
            return false;
        }
    }

    private static void logTargetOnce(String target, EditorInfo editorInfo) {
        String key = String.valueOf(target);
        synchronized (LOGGED_TARGETS) {
            if (LOGGED_TARGETS.size() > 200 || !LOGGED_TARGETS.add(key)) {
                return;
            }
        }
        String[] mimeTypes = editorInfo == null ? null : editorInfo.contentMimeTypes;
        L.i(key + " 声明可直接接收的类型 " + (mimeTypes == null ? "无" : Arrays.toString(mimeTypes)));
    }

    static void toast(Context context, String str) {
        try {
            Toast.makeText(context, str, 0).show();
        } catch (Throwable unused) {
        }
    }

    static int dp(Context context, float f) {
        return Math.round(TypedValue.applyDimension(1, f, context.getResources().getDisplayMetrics()));
    }
}
