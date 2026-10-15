package com.ziur.t9clip;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import com.ziur.t9clip.ImageSender;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

final class ToolbarImageChip {
    private static final String BAR = "org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent";
    private static final String SERVICE = "org.fcitx.fcitx5.android.input.FcitxInputMethodService";
    private static ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private static boolean installed;
    private static int originalHeight;
    private static Drawable originalIcon;
    private static ImageView.ScaleType originalScale;
    private static int originalWidth;
    private static int requestGeneration;
    private static ImageSender.ClipImage shown;
    private static Views views;
    private static WeakReference<InputMethodService> serviceRef = new WeakReference<>(null);
    private static WeakReference<Object> barRef = new WeakReference<>(null);
    private static final View.OnTouchListener TOUCH = new View.OnTouchListener() {
        private boolean longFired;
        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                longFired = true;
                if (pressed != null) {
                    pressed.setPressed(false);
                    pressed.performHapticFeedback(0);
                }
                ToolbarImageChip.onLongPress();
            }
        };
        private View pressed;

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.longFired = false;
                this.pressed = view;
                view.setPressed(true);
                ImageSender.MAIN.postDelayed(this.longPress, ViewConfiguration.getLongPressTimeout());
            } else if (actionMasked == 1) {
                ImageSender.MAIN.removeCallbacks(this.longPress);
                boolean zIsPressed = view.isPressed();
                view.setPressed(false);
                if (!this.longFired && zIsPressed) {
                    view.performHapticFeedback(3);
                    ToolbarImageChip.onTap();
                }
            } else if (actionMasked != 2) {
                if (actionMasked == 3) {
                    ImageSender.MAIN.removeCallbacks(this.longPress);
                    view.setPressed(false);
                }
            } else if (motionEvent.getX() < 0.0f || motionEvent.getY() < 0.0f || motionEvent.getX() > view.getWidth() || motionEvent.getY() > view.getHeight()) {
                ImageSender.MAIN.removeCallbacks(this.longPress);
                view.setPressed(false);
            }
            return true;
        }
    };

    private ToolbarImageChip() {
    }

    static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            XposedBridge.hookMethod(Class.forName(SERVICE, false, classLoader).getDeclaredMethod("onCreate", new Class[0]), new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        ToolbarImageChip.onServiceCreated((InputMethodService) methodHookParam.thisObject);
                    } catch (Throwable th) {
                        L.e("工具栏截图提示：初始化失败", th);
                    }
                }
            });
            Method method = Refl.method(Class.forName(BAR, false, classLoader), "onScopeSetupFinished", 1);
            if (method == null) {
                L.i("工具栏截图提示：找不到 onScopeSetupFinished，跳过");
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ToolbarImageChip.onBarReady(methodHookParam.thisObject);
                }
            });
            L.i("工具栏截图提示已生效");
        } catch (Throwable th) {
            L.e("工具栏截图提示：挂接失败", th);
        }
    }

    static InputMethodService service() {
        return serviceRef.get();
    }

    private static void onServiceCreated(InputMethodService inputMethodService) {
        ClipboardManager clipboardManager;
        serviceRef = new WeakReference<>(inputMethodService);
        if (clipListener == null && (clipboardManager = (ClipboardManager) inputMethodService.getApplicationContext().getSystemService("clipboard")) != null) {
            ClipboardManager.OnPrimaryClipChangedListener onPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    ImageSender.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            ToolbarImageChip.onClipChanged();
                        }
                    });
                }
            };
            clipListener = onPrimaryClipChangedListener;
            clipboardManager.addPrimaryClipChangedListener(onPrimaryClipChangedListener);
        }
    }

    private static void onBarReady(Object obj) {
        barRef = new WeakReference<>(obj);
        views = null;
        shown = null;
        originalIcon = null;
        ImageSender.MAIN.post(new Runnable() {
            @Override
            public void run() {
                ToolbarImageChip.onClipChanged();
            }
        });
    }

    private static void onClipChanged() {
        try {
            InputMethodService inputMethodServiceService = service();
            if (inputMethodServiceService == null) {
                return;
            }
            final ImageSender.ClipImage clipImageCurrent = ImageSender.current(inputMethodServiceService);
            if (clipImageCurrent == null) {
                restore(!clipHasText(inputMethodServiceService));
                return;
            }
            if (shown == null || !clipImageCurrent.uri.equals(shown.uri)) {
                long jTimeoutMillis = timeoutMillis();
                if ((jTimeoutMillis < 0 || clipImageCurrent.timestamp <= 0 || System.currentTimeMillis() - clipImageCurrent.timestamp <= jTimeoutMillis) && suggestionEnabled()) {
                    final int i = requestGeneration + 1;
                    requestGeneration = i;
                    ImageSender.loadThumbnail(inputMethodServiceService, clipImageCurrent.uri, ImageSender.dp(inputMethodServiceService, 32.0f) * 2, new ImageSender.BitmapCallback() {
                        @Override
                        public void done(Bitmap bitmap) {
                            if (i != ToolbarImageChip.requestGeneration || bitmap == null) {
                                return;
                            }
                            ToolbarImageChip.show(clipImageCurrent, bitmap);
                        }
                    });
                }
            }
        } catch (Throwable th) {
            L.e("工具栏截图提示：处理剪贴板变化失败", th);
        }
    }

    private static void show(ImageSender.ClipImage clipImage, Bitmap bitmap) {
        Views views2;
        Object obj = barRef.get();
        if (obj == null || (views2 = views(obj)) == null) {
            return;
        }
        Context context = views2.icon.getContext();
        if (shown == null) {
            originalIcon = views2.icon.getDrawable();
            ViewGroup.LayoutParams layoutParams = views2.icon.getLayoutParams();
            originalWidth = layoutParams.width;
            originalHeight = layoutParams.height;
            originalScale = views2.icon.getScaleType();
        }
        shown = clipImage;
        int iDp = ImageSender.dp(context, 30.0f);
        final float fDp = ImageSender.dp(context, 5.0f);
        ViewGroup.LayoutParams layoutParams2 = views2.icon.getLayoutParams();
        layoutParams2.width = iDp;
        layoutParams2.height = iDp;
        views2.icon.setLayoutParams(layoutParams2);
        views2.icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        views2.icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), fDp);
            }
        });
        views2.icon.setClipToOutline(true);
        views2.icon.setImageBitmap(bitmap);
        views2.text.setText(clipImage.screenshot ? "截图" : "图片");
        views2.suggestion.setOnTouchListener(TOUCH);
        Refl.setBoolean(obj, "isClipboardFresh", true);
        Refl.call(obj, "launchClipboardTimeoutJob", new Object[0]);
        Refl.call(obj, "evalIdleUiState", Boolean.FALSE);
    }

    private static void restore(boolean z) {
        if (shown == null) {
            return;
        }
        shown = null;
        Views views2 = views;
        if (views2 != null) {
            views2.icon.setImageDrawable(originalIcon);
            ViewGroup.LayoutParams layoutParams = views2.icon.getLayoutParams();
            layoutParams.width = originalWidth;
            layoutParams.height = originalHeight;
            views2.icon.setLayoutParams(layoutParams);
            if (originalScale != null) {
                views2.icon.setScaleType(originalScale);
            }
            views2.icon.setClipToOutline(false);
            views2.icon.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            views2.suggestion.setOnTouchListener(null);
        }
        Object obj = barRef.get();
        if (!z || obj == null) {
            return;
        }
        Refl.setBoolean(obj, "isClipboardFresh", false);
        Refl.call(obj, "evalIdleUiState", Boolean.FALSE);
    }

    private static void onTap() {
        ImageSender.ClipImage clipImage = shown;
        InputMethodService inputMethodServiceService = service();
        if (clipImage != null && inputMethodServiceService != null) {
            ImageSender.send(inputMethodServiceService, clipImage.uri, clipImage.mime);
        }
        restore(true);
    }

    private static void onLongPress() {
        InputMethodService inputMethodServiceService = service();
        if (inputMethodServiceService != null && ImageSender.clearClipboard(inputMethodServiceService)) {
            ImageSender.toast(inputMethodServiceService, "已从剪贴板清除");
        }
        restore(true);
    }

    private static final class Views {
        final Object bar;
        final ImageView icon;
        final View suggestion;
        final TextView text;

        Views(Object obj, TextView textView, ImageView imageView, View view) {
            this.bar = obj;
            this.text = textView;
            this.icon = imageView;
            this.suggestion = view;
        }
    }

    private static Views views(Object obj) {
        ImageView imageView;
        Views views2 = views;
        if (views2 != null && views2.bar == obj) {
            return views;
        }
        int i = 0;
        Object objValueOfType = Refl.valueOfType(Refl.field(Refl.call(obj, "getIdleUi", new Object[0]), "clipboardUi"), TextView.class);
        if (!(objValueOfType instanceof TextView)) {
            L.i("工具栏截图提示：找不到剪贴板建议控件，跳过");
            return null;
        }
        TextView textView = (TextView) objValueOfType;
        ViewParent parent = textView.getParent();
        Object parent2 = parent == null ? null : parent.getParent();
        if (!(parent instanceof ViewGroup) || !(parent2 instanceof View)) {
            return null;
        }
        ViewGroup viewGroup = (ViewGroup) parent;
        while (true) {
            if (i >= viewGroup.getChildCount()) {
                imageView = null;
                break;
            }
            if (viewGroup.getChildAt(i) instanceof ImageView) {
                imageView = (ImageView) viewGroup.getChildAt(i);
                break;
            }
            i++;
        }
        if (imageView == null) {
            return null;
        }
        Views views3 = new Views(obj, textView, imageView, (View) parent2);
        views = views3;
        return views3;
    }

    private static boolean clipHasText(Context context) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService("clipboard");
            ClipData primaryClip = clipboardManager == null ? null : clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() > 0) {
                if (primaryClip.getItemAt(0).getText() != null) {
                    return true;
                }
            }
        } catch (Throwable unused) {
        }
        return false;
    }

    private static boolean suggestionEnabled() {
        Object objCall = Refl.call(Refl.field(barRef.get(), "clipboardSuggestion"), "getValue", new Object[0]);
        return !(objCall instanceof Boolean) || ((Boolean) objCall).booleanValue();
    }

    private static long timeoutMillis() {
        Object objCall = Refl.call(Refl.field(barRef.get(), "clipboardItemTimeout"), "getValue", new Object[0]);
        if (objCall instanceof Integer) {
            return ((long) ((Integer) objCall).intValue()) * 1000;
        }
        return 30000L;
    }
}
