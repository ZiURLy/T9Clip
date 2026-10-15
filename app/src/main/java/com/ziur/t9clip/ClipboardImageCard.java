package com.ziur.t9clip;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;
import com.ziur.t9clip.ImageSender;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class ClipboardImageCard {
    private static final Map<Object, Card> CARDS = Collections.synchronizedMap(new WeakHashMap());
    private static final String CARD_TAG = "t9clip.clipboard-image-card";
    private static final String THEME = "org.fcitx.fcitx5.android.data.theme.Theme";
    private static final String UI = "org.fcitx.fcitx5.android.input.clipboard.ClipboardUi";
    private static final String WINDOW = "org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow";
    private static boolean installed;

    private ClipboardImageCard() {
    }

    static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Class<?> cls = Class.forName(WINDOW, false, classLoader);
            try {
                XposedBridge.hookMethod(cls.getDeclaredMethod("onCreateView", new Class[0]), new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        try {
                            ClipboardImageCard.inject(methodHookParam.thisObject, methodHookParam.getResult());
                        } catch (Throwable th) {
                            L.e("截图卡片：插入面板失败", th);
                        }
                    }
                });
                XposedBridge.hookMethod(cls.getDeclaredMethod("onAttached", new Class[0]), new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Card card = (Card) ClipboardImageCard.CARDS.get(methodHookParam.thisObject);
                        if (card != null) {
                            card.attach();
                        }
                    }
                });
                XposedBridge.hookMethod(cls.getDeclaredMethod("onDetached", new Class[0]), new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Card card = (Card) ClipboardImageCard.CARDS.get(methodHookParam.thisObject);
                        if (card != null) {
                            card.detach();
                        }
                    }
                });
                L.i("剪贴板截图卡片已生效");
            } catch (Throwable th) {
                L.e("截图卡片：挂接失败", th);
            }
        } catch (Throwable th2) {
            L.i("找不到剪贴板面板，截图卡片跳过（" + th2 + "）");
        }
    }

    private static void inject(Object obj, Object obj2) {
        if (obj2 instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) obj2;
            if (viewGroup.findViewWithTag(CARD_TAG) != null) {
                return;
            }
            Object objValueOfType = Refl.valueOfType(Refl.callByReturnType(obj, UI, null), ViewAnimator.class);
            if (objValueOfType instanceof ViewAnimator) {
                View view = (View) objValueOfType;
                if (view.getParent() == viewGroup) {
                    Object objCallByReturnType = Refl.callByReturnType(obj, null, InputMethodService.class);
                    if (!(objCallByReturnType instanceof InputMethodService)) {
                        L.i("截图卡片：拿不到输入法服务，跳过");
                        return;
                    }
                    Context context = viewGroup.getContext();
                    Card card = new Card(context, (InputMethodService) objCallByReturnType, Refl.callByReturnType(obj, THEME, null), adapterRadius(obj, ImageSender.dp(context, 8.0f)));
                    int iIndexOfChild = viewGroup.indexOfChild(view);
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    viewGroup.removeView(view);
                    LinearLayout linearLayout = new LinearLayout(context);
                    linearLayout.setOrientation(1);
                    linearLayout.addView(card.view, card.layoutParams());
                    linearLayout.addView(view, new LinearLayout.LayoutParams(-1, 0, 1.0f));
                    viewGroup.addView(linearLayout, iIndexOfChild, layoutParams);
                    CARDS.put(obj, card);
                    return;
                }
            }
            L.i("截图卡片：剪贴板面板结构和预期不同，跳过");
        }
    }

    private static final class Card {
        final ClipboardManager clipboard;
        final Context ctx;
        int generation;
        ImageSender.ClipImage image;
        final InputMethodService ims;
        ClipboardManager.OnPrimaryClipChangedListener listener;
        final ImageView thumb;
        final int thumbPx;
        final TextView title;
        final FrameLayout view;

        Card(Context context, InputMethodService inputMethodService, Object obj, float f) {
            this.ctx = context;
            this.ims = inputMethodService;
            this.clipboard = (ClipboardManager) context.getSystemService("clipboard");
            int iColor = ClipboardImageCard.color(obj, "getClipboardEntryColor", -12829636);
            int iColor2 = ClipboardImageCard.color(obj, "getKeyTextColor", -1);
            int iColor3 = ClipboardImageCard.color(obj, "getAltKeyTextColor", -5197648);
            int iColor4 = ClipboardImageCard.color(obj, "getKeyPressHighlightColor", 872415231);
            int iDp = ImageSender.dp(context, 56.0f);
            this.thumbPx = iDp;
            final float fDp = ImageSender.dp(context, 6.0f);
            FrameLayout frameLayout = new FrameLayout(context);
            this.view = frameLayout;
            frameLayout.setTag(ClipboardImageCard.CARD_TAG);
            frameLayout.setVisibility(8);
            frameLayout.setClickable(true);
            frameLayout.setBackground(ClipboardImageCard.rounded(iColor, f));
            frameLayout.setForeground(new RippleDrawable(ColorStateList.valueOf(iColor4), null, ClipboardImageCard.rounded(-1, f)));
            frameLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ImageSender.ClipImage clipImage = Card.this.image;
                    if (clipImage != null) {
                        ImageSender.send(Card.this.ims, clipImage.uri, clipImage.mime);
                    }
                }
            });
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(16);
            int iDp2 = ImageSender.dp(context, 6.0f);
            linearLayout.setPadding(iDp2, iDp2, 0, iDp2);
            ImageView imageView = new ImageView(context);
            this.thumb = imageView;
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), fDp);
                }
            });
            imageView.setClipToOutline(true);
            linearLayout.addView(imageView, new LinearLayout.LayoutParams(iDp, iDp));
            LinearLayout linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(1);
            TextView textViewLabel = ClipboardImageCard.label(context, 14, iColor2);
            this.title = textViewLabel;
            textViewLabel.setTypeface(Typeface.DEFAULT_BOLD);
            TextView textViewLabel2 = ClipboardImageCard.label(context, 12, iColor3);
            textViewLabel2.setText("点一下发到输入框");
            linearLayout2.addView(textViewLabel);
            linearLayout2.addView(textViewLabel2);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams.setMarginStart(ImageSender.dp(context, 10.0f));
            linearLayout.addView(linearLayout2, layoutParams);
            TextView textViewLabel3 = ClipboardImageCard.label(context, 22, iColor3);
            textViewLabel3.setText("×");
            textViewLabel3.setGravity(17);
            textViewLabel3.setContentDescription("从剪贴板清除这张图");
            textViewLabel3.setBackground(new RippleDrawable(ColorStateList.valueOf(iColor4), null, null));
            textViewLabel3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (ImageSender.clearClipboard(Card.this.ctx)) {
                        Card.this.hide();
                        ImageSender.toast(Card.this.ctx, "已从剪贴板清除");
                    }
                }
            });
            linearLayout.addView(textViewLabel3, new LinearLayout.LayoutParams(ImageSender.dp(context, 48.0f), ImageSender.dp(context, 48.0f)));
            frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        }

        LinearLayout.LayoutParams layoutParams() {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            int iDp = ImageSender.dp(this.ctx, 4.0f);
            layoutParams.setMargins(iDp, iDp, iDp, 0);
            return layoutParams;
        }

        void attach() {
            safeRefresh();
            if (this.listener != null || this.clipboard == null) {
                return;
            }
            ClipboardManager.OnPrimaryClipChangedListener onPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    ImageSender.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            Card.this.safeRefresh();
                        }
                    });
                }
            };
            this.listener = onPrimaryClipChangedListener;
            try {
                this.clipboard.addPrimaryClipChangedListener(onPrimaryClipChangedListener);
            } catch (Throwable unused) {
                this.listener = null;
            }
        }

        void detach() {
            ClipboardManager clipboardManager;
            try {
                ClipboardManager.OnPrimaryClipChangedListener onPrimaryClipChangedListener = this.listener;
                if (onPrimaryClipChangedListener != null && (clipboardManager = this.clipboard) != null) {
                    clipboardManager.removePrimaryClipChangedListener(onPrimaryClipChangedListener);
                }
            } catch (Throwable unused) {
            }
            this.listener = null;
        }

        void safeRefresh() {
            try {
                refresh();
            } catch (Throwable th) {
                L.e("截图卡片：刷新失败", th);
                hide();
            }
        }

        private void refresh() {
            ImageSender.ClipImage clipImageCurrent = ImageSender.current(this.ctx);
            if (clipImageCurrent == null) {
                hide();
                return;
            }
            if (this.image != null && clipImageCurrent.uri.equals(this.image.uri) && this.view.getVisibility() == 0) {
                return;
            }
            this.image = clipImageCurrent;
            this.title.setText(clipImageCurrent.screenshot ? "最新截图" : "剪贴板图片");
            this.thumb.setImageDrawable(null);
            this.view.setVisibility(0);
            final int i = this.generation + 1;
            this.generation = i;
            ImageSender.loadThumbnail(this.ctx, clipImageCurrent.uri, this.thumbPx * 2, new ImageSender.BitmapCallback() {
                @Override
                public void done(Bitmap bitmap) {
                    if (i != Card.this.generation) {
                        return;
                    }
                    if (bitmap == null) {
                        Card.this.hide();
                    } else {
                        Card.this.thumb.setImageBitmap(bitmap);
                    }
                }
            });
        }

        void hide() {
            this.generation++;
            this.image = null;
            this.thumb.setImageDrawable(null);
            this.view.setVisibility(8);
        }
    }

    private static float adapterRadius(Object obj, float f) {
        try {
            for (Method method : obj.getClass().getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0 && method.getReturnType().getName().endsWith(".ClipboardAdapter")) {
                    method.setAccessible(true);
                    Object objInvoke = method.invoke(obj, new Object[0]);
                    for (Class<?> superclass = objInvoke.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
                        try {
                            Field declaredField = superclass.getDeclaredField("entryRadius");
                            declaredField.setAccessible(true);
                            return declaredField.getFloat(objInvoke);
                        } catch (NoSuchFieldException unused) {
                        }
                    }
                }
            }
        } catch (Throwable unused2) {
        }
        return f;
    }

    static int color(Object obj, String str, int i) {
        if (obj != null) {
            try {
                Object objInvoke = obj.getClass().getMethod(str, new Class[0]).invoke(obj, new Object[0]);
                if (objInvoke instanceof Integer) {
                    return ((Integer) objInvoke).intValue();
                }
            } catch (Throwable unused) {
            }
        }
        return i;
    }

    private static GradientDrawable rounded(int i, float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(f);
        gradientDrawable.setColor(i);
        return gradientDrawable;
    }

    private static TextView label(Context context, int i, int i2) {
        TextView textView = new TextView(context);
        textView.setTextSize(2, i);
        textView.setTextColor(i2);
        textView.setMaxLines(1);
        return textView;
    }
}
