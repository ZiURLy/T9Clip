package com.ziur.t9clip;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolbarCustomizer {
    private static final String ACTION = "a:";
    private static final String BROADCASTER = "org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster";
    private static final String BUTTONS_BAR = "org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi";
    private static final String PREFS = "t9clip_toolbar";
    private static final String SEP = "\n";
    private static final String STATUS_ADAPTER = "org.fcitx.fcitx5.android.input.status.StatusAreaAdapter";
    private static boolean actionsKnown;
    private static boolean installed;
    private static final String[] KEYS = {"undo", "redo", "cursor", "clipboard", "more"};
    private static final String[] FIELDS = {"undoButton", "redoButton", "cursorMoveButton", "clipboardButton", "moreButton"};
    private static final List<String> DEFAULT_ORDER = Arrays.asList("clipboard", "undo", "redo", "cursor", "more");
    private static final List<WeakReference<Bar>> BARS = new ArrayList();
    private static final Map<String, Object> ACTIONS = new HashMap();
    private static String lastActionLog = "";

    private interface Edit {
        void run(Config config);
    }

    private ToolbarCustomizer() {
    }

    static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            for (Constructor<?> constructor : Class.forName(BUTTONS_BAR, false, classLoader).getDeclaredConstructors()) {
                XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        try {
                            ToolbarCustomizer.onButtonsBarCreated(methodHookParam.thisObject);
                        } catch (Throwable th) {
                            L.e("工具栏自定义：应用失败", th);
                        }
                    }
                });
            }
            try {
                Method method = Refl.method(Class.forName(BROADCASTER, false, classLoader), "onStatusAreaUpdate", 1);
                if (method != null) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            try {
                                ToolbarCustomizer.onActions((Object[]) methodHookParam.args[0]);
                            } catch (Throwable th) {
                                L.e("工具栏自定义：读取状态选项失败", th);
                            }
                        }
                    });
                }
                Method method2 = Refl.method(Class.forName(STATUS_ADAPTER, false, classLoader), "onBindViewHolder", 2);
                if (method2 != null) {
                    XposedBridge.hookMethod(method2, new XC_MethodHook() {
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            try {
                                ToolbarCustomizer.onStatusEntryBound(methodHookParam.thisObject, methodHookParam.args[0], ((Integer) methodHookParam.args[1]).intValue());
                            } catch (Throwable th) {
                                L.e("工具栏自定义：设置长按失败", th);
                            }
                        }
                    });
                }
                L.i("工具栏自定义已生效");
            } catch (Throwable th) {
                L.e("工具栏自定义：挂接「更多」面板失败", th);
            }
        } catch (Throwable th2) {
            L.e("工具栏自定义：挂接工具栏失败", th2);
        }
    }

    private static void onButtonsBarCreated(Object obj) {
        Object objField = Refl.field(obj, "root");
        if (!(objField instanceof ViewGroup)) {
            L.i("工具栏自定义：工具栏结构和预期不同，跳过");
            return;
        }
        Bar bar = new Bar((ViewGroup) objField, Refl.field(obj, "theme"));
        int i = 0;
        while (true) {
            String[] strArr = KEYS;
            if (i >= strArr.length) {
                break;
            }
            Object objField2 = Refl.field(obj, FIELDS[i]);
            if (objField2 instanceof View) {
                bar.builtins.put(strArr[i], (View) objField2);
            }
            i++;
        }
        if (bar.builtins.isEmpty()) {
            return;
        }
        List<WeakReference<Bar>> list = BARS;
        synchronized (list) {
            prune();
            list.add(new WeakReference<>(bar));
        }
        bar.apply();
    }

    private static void applyAll() {
        Iterator<Bar> it = liveBars().iterator();
        while (it.hasNext()) {
            try {
                it.next().apply();
            } catch (Throwable th) {
                L.e("工具栏自定义：刷新失败", th);
            }
        }
    }

    private static List<Bar> liveBars() {
        ArrayList arrayList = new ArrayList();
        List<WeakReference<Bar>> list = BARS;
        synchronized (list) {
            prune();
            Iterator<WeakReference<Bar>> it = list.iterator();
            while (it.hasNext()) {
                Bar bar = it.next().get();
                if (bar != null) {
                    arrayList.add(bar);
                }
            }
        }
        return arrayList;
    }

    private static void prune() {
        Iterator<WeakReference<Bar>> it = BARS.iterator();
        while (it.hasNext()) {
            if (it.next().get() == null) {
                it.remove();
            }
        }
    }

    private static final class Bar {
        final Context ctx;
        final ViewGroup root;
        final Object theme;
        final Map<String, View> builtins = new LinkedHashMap();
        final Map<String, TextView> pinned = new HashMap();

        Bar(ViewGroup viewGroup, Object obj) {
            this.root = viewGroup;
            this.ctx = viewGroup.getContext();
            this.theme = obj;
        }

        void apply() {
            Config configLoad = Config.load(this.ctx);
            this.root.removeAllViews();
            for (final String str : configLoad.order) {
                View viewPinnedButton;
                if (this.builtins.containsKey(str)) {
                    viewPinnedButton = this.builtins.get(str);
                    viewPinnedButton.setVisibility(configLoad.hidden.contains(str) ? View.GONE : View.VISIBLE);
                } else if (str.startsWith(ToolbarCustomizer.ACTION)) {
                    viewPinnedButton = pinnedButton(str);
                } else {
                    continue;
                }
                if (viewPinnedButton.getParent() instanceof ViewGroup) {
                    ((ViewGroup) viewPinnedButton.getParent()).removeView(viewPinnedButton);
                }
                ViewGroup.LayoutParams layoutParams = viewPinnedButton.getLayoutParams();
                if (layoutParams == null) {
                    int iDp = ImageSender.dp(this.ctx, 40.0f);
                    layoutParams = new ViewGroup.LayoutParams(iDp, iDp);
                }
                this.root.addView(viewPinnedButton, layoutParams);
                viewPinnedButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        ToolbarCustomizer.showToolbarMenu(Bar.this, view, str);
                        return true;
                    }
                });
            }
            this.pinned.keySet().retainAll(configLoad.order);
            refreshPinned(configLoad);
        }

        TextView pinnedButton(final String str) {
            TextView textView = this.pinned.get(str);
            if (textView != null) {
                return textView;
            }
            TextView textView2 = new TextView(this.ctx);
            textView2.setGravity(17);
            textView2.setTextSize(1, 17.0f);
            textView2.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
            textView2.setIncludeFontPadding(false);
            textView2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ToolbarCustomizer.activate(str, view);
                }
            });
            this.pinned.put(str, textView2);
            return textView2;
        }

        void refreshPinned(Config config) {
            InsetDrawable insetDrawable;
            int iColor = ClipboardImageCard.color(this.theme, "getKeyPressHighlightColor", 872415231);
            int iColor2 = ClipboardImageCard.color(this.theme, "getAltKeyTextColor", -5197648);
            int iColor3 = ClipboardImageCard.color(this.theme, "getGenericActiveForegroundColor", -1);
            int iColor4 = ClipboardImageCard.color(this.theme, "getGenericActiveBackgroundColor", -10720320);
            for (Map.Entry<String, TextView> entry : this.pinned.entrySet()) {
                TextView value = entry.getValue();
                Object obj = ToolbarCustomizer.ACTIONS.get(entry.getKey().substring(2));
                if (obj == null && ToolbarCustomizer.actionsKnown) {
                    value.setVisibility(8);
                } else {
                    boolean z = false;
                    value.setVisibility(0);
                    String str = obj != null ? ToolbarCustomizer.str(Refl.call(obj, "getShortText", new Object[0])) : null;
                    if (TextUtils.isEmpty(str)) {
                        str = config.labels.get(entry.getKey());
                    }
                    if (obj != null && ToolbarCustomizer.isActive(obj)) {
                        z = true;
                    }
                    value.setText(ToolbarCustomizer.firstChar(str == null ? "?" : str));
                    value.setContentDescription(str);
                    value.setTextColor(z ? iColor3 : iColor2);
                    if (z) {
                        GradientDrawable gradientDrawable = new GradientDrawable();
                        gradientDrawable.setShape(1);
                        gradientDrawable.setColor(iColor4);
                        insetDrawable = new InsetDrawable((Drawable) gradientDrawable, ImageSender.dp(this.ctx, 5.0f));
                    } else {
                        insetDrawable = null;
                    }
                    value.setBackground(new RippleDrawable(ColorStateList.valueOf(iColor), insetDrawable, null));
                }
            }
        }
    }

    private static void showToolbarMenu(Bar bar, View view, final String str) {
        Config configLoad = Config.load(bar.ctx);
        ArrayList arrayList = new ArrayList();
        for (String str2 : configLoad.order) {
            if (!configLoad.hidden.contains(str2) && (bar.builtins.containsKey(str2) || str2.startsWith(ACTION))) {
                arrayList.add(str2);
            }
        }
        int iIndexOf = arrayList.indexOf(str);
        PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
        Menu menu = popupMenu.getMenu();
        if (iIndexOf > 0) {
            menu.add("移到最左").setOnMenuItemClickListener(edit(bar, new Edit() {
                @Override
                public void run(Config config) {
                    config.order.remove(str);
                    config.order.add(0, str);
                }
            }));
            menu.add("左移").setOnMenuItemClickListener(edit(bar, new Edit() {
                @Override
                public void run(Config config) {
                    config.swapWithNeighbor(str, -1);
                }
            }));
        }
        if (iIndexOf >= 0 && iIndexOf < arrayList.size() - 1) {
            menu.add("右移").setOnMenuItemClickListener(edit(bar, new Edit() {
                @Override
                public void run(Config config) {
                    config.swapWithNeighbor(str, 1);
                }
            }));
        }
        if (str.startsWith(ACTION)) {
            menu.add("从工具栏移除").setOnMenuItemClickListener(edit(bar, new Edit() {
                @Override
                public void run(Config config) {
                    config.order.remove(str);
                    config.labels.remove(str);
                }
            }));
        } else if (arrayList.size() > 1) {
            menu.add("隐藏这个按钮").setOnMenuItemClickListener(edit(bar, new Edit() {
                @Override
                public void run(Config config) {
                    config.hidden.add(str);
                }
            }));
        }
        if (!configLoad.hidden.isEmpty()) {
            menu.add("显示隐藏的按钮").setOnMenuItemClickListener(edit(bar, new Edit() {
                @Override
                public void run(Config config) {
                    config.hidden.clear();
                }
            }));
        }
        menu.add("恢复默认工具栏").setOnMenuItemClickListener(edit(bar, new Edit() {
            @Override
            public void run(Config config) {
                config.order.clear();
                config.order.addAll(ToolbarCustomizer.DEFAULT_ORDER);
                config.hidden.clear();
                config.labels.clear();
            }
        }));
        popupMenu.show();
    }

    private static MenuItem.OnMenuItemClickListener edit(final Bar bar, final Edit edit) {
        return new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Config configLoad = Config.load(bar.ctx);
                edit.run(configLoad);
                configLoad.save(bar.ctx);
                ToolbarCustomizer.applyAll();
                return true;
            }
        };
    }

    private static void onActions(Object[] objArr) {
        ACTIONS.clear();
        StringBuilder sb = new StringBuilder();
        if (objArr != null) {
            for (Object obj : objArr) {
                String str = str(Refl.call(obj, "getName", new Object[0]));
                if (!str.isEmpty()) {
                    ACTIONS.put(str, obj);
                    sb.append(str);
                    sb.append('=');
                    sb.append(str(Refl.call(obj, "getShortText", new Object[0])));
                    sb.append("; ");
                }
            }
        }
        actionsKnown = true;
        String string = sb.toString();
        if (!string.equals(lastActionLog)) {
            lastActionLog = string;
            L.i("「更多」面板选项：" + string);
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (Bar bar : ToolbarCustomizer.liveBars()) {
                    bar.refreshPinned(Config.load(bar.ctx));
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            ImageSender.MAIN.post(runnable);
        }
    }

    private static void onStatusEntryBound(Object adapter, Object holder, int position) {
        Object objField = Refl.field(holder, "itemView");
        if (objField instanceof View) {
            View view = (View) objField;
            Object entry = null;
            Object entries = Refl.field(adapter, "entries");
            if (entries instanceof Object[]) {
                Object[] objArr = (Object[]) entries;
                if (position >= 0 && position < objArr.length) {
                    entry = objArr[position];
                }
            }
            Object objField3 = Refl.field(entry, "action");
            final String str = objField3 == null ? "" : str(Refl.call(objField3, "getName", new Object[0]));
            if (str.isEmpty()) {
                view.setOnLongClickListener(null);
                return;
            }
            ACTIONS.put(str, objField3);
            final String str2 = str(Refl.call(objField3, "getShortText", new Object[0]));
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view2) {
                    ToolbarCustomizer.showPinMenu(view2, str, str2);
                    return true;
                }
            });
        }
    }

    private static void showPinMenu(View view, String str, final String str2) {
        final Context context = view.getContext();
        final String str3 = ACTION + str;
        final boolean zContains = Config.load(context).order.contains(str3);
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.getMenu().add(zContains ? "从工具栏移除" : "固定到工具栏").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Config configLoad = Config.load(context);
                if (zContains) {
                    configLoad.order.remove(str3);
                    configLoad.labels.remove(str3);
                } else if (!configLoad.order.contains(str3)) {
                    int iIndexOf = configLoad.order.indexOf("more");
                    List<String> list = configLoad.order;
                    if (iIndexOf < 0) {
                        iIndexOf = configLoad.order.size();
                    }
                    list.add(iIndexOf, str3);
                    configLoad.labels.put(str3, str2);
                }
                configLoad.save(context);
                ToolbarCustomizer.applyAll();
                ImageSender.toast(context, zContains ? "已从工具栏移除" : "已固定到工具栏");
                return true;
            }
        });
        popupMenu.show();
    }

    private static void activate(String str, View view) {
        Object obj = ACTIONS.get(str.substring(2));
        if (obj == null) {
            ImageSender.toast(view.getContext(), "这个选项现在不可用");
            return;
        }
        Object objCall = Refl.call(obj, "getMenu", new Object[0]);
        if (objCall instanceof Object[]) {
            Object[] objArr = (Object[]) objCall;
            if (objArr.length > 0) {
                PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
                for (final Object obj2 : objArr) {
                    if (!Boolean.TRUE.equals(Refl.call(obj2, "isSeparator", new Object[0]))) {
                        popupMenu.getMenu().add(str(Refl.call(obj2, "getShortText", new Object[0]))).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                ToolbarCustomizer.activateId(Refl.call(obj2, "getId", new Object[0]));
                                return true;
                            }
                        });
                    }
                }
                popupMenu.show();
                return;
            }
        }
        activateId(Refl.call(obj, "getId", new Object[0]));
    }

    private static void activateId(Object obj) {
        if (obj instanceof Integer) {
            final int iIntValue = ((Integer) obj).intValue();
            InputMethodService inputMethodServiceService = ToolbarImageChip.service();
            if (inputMethodServiceService == null) {
                return;
            }
            try {
                ClassLoader classLoader = inputMethodServiceService.getClass().getClassLoader();
                Class<?> cls = Class.forName("kotlin.jvm.functions.Function2", false, classLoader);
                final Class<?> cls2 = Class.forName("kotlin.coroutines.Continuation", false, classLoader);
                final Object obj2 = Class.forName("kotlin.Unit", false, classLoader).getField("INSTANCE").get(null);
                Object objNewProxyInstance = Proxy.newProxyInstance(classLoader, new Class[]{cls}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object obj3, Method method, Object[] objArr) {
                        String name = method.getName();
                        if ("invoke".equals(name) && objArr != null && objArr.length == 2) {
                            try {
                                return objArr[0].getClass().getMethod("activateAction", Integer.TYPE, cls2).invoke(objArr[0], Integer.valueOf(iIntValue), objArr[1]);
                            } catch (Throwable th) {
                                L.e("切换选项失败", th);
                                return obj2;
                            }
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(obj3));
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(objArr != null && objArr.length == 1 && obj3 == objArr[0]);
                        }
                        if (!"toString".equals(name)) {
                            return null;
                        }
                        return "T9ClipActivate(" + iIntValue + ")";
                    }
                });
                Method method = Refl.method(inputMethodServiceService.getClass(), "postFcitxJob", 1);
                if (method == null) {
                    L.i("找不到 postFcitxJob，无法切换选项");
                } else {
                    method.invoke(inputMethodServiceService, objNewProxyInstance);
                }
            } catch (Throwable th) {
                L.e("切换选项失败", th);
            }
        }
    }

    private static final class Config {
        final List<String> order = new ArrayList();
        final Set<String> hidden = new HashSet();
        final Map<String, String> labels = new HashMap();

        private Config() {
        }

        static Config load(Context context) {
            Config config = new Config();
            SharedPreferences sharedPreferencesPrefs = ToolbarCustomizer.prefs(context);
            String string = sharedPreferencesPrefs != null ? sharedPreferencesPrefs.getString("order", null) : null;
            if (string == null) {
                config.order.addAll(ToolbarCustomizer.DEFAULT_ORDER);
            } else {
                for (String str : string.split(ToolbarCustomizer.SEP)) {
                    if (!str.isEmpty() && !config.order.contains(str)) {
                        config.order.add(str);
                    }
                }
            }
            if (sharedPreferencesPrefs != null) {
                for (String str2 : sharedPreferencesPrefs.getString("hidden", "").split(ToolbarCustomizer.SEP)) {
                    if (!str2.isEmpty()) {
                        config.hidden.add(str2);
                    }
                }
                for (String str3 : config.order) {
                    if (str3.startsWith(ToolbarCustomizer.ACTION)) {
                        config.labels.put(str3, sharedPreferencesPrefs.getString("label:" + str3, ""));
                    }
                }
            }
            for (String str4 : ToolbarCustomizer.KEYS) {
                if (!config.order.contains(str4)) {
                    config.order.add(str4);
                }
            }
            return config;
        }

        void save(Context context) {
            SharedPreferences sharedPreferencesPrefs = ToolbarCustomizer.prefs(context);
            if (sharedPreferencesPrefs == null) {
                return;
            }
            SharedPreferences.Editor editorClear = sharedPreferencesPrefs.edit().clear();
            editorClear.putString("order", TextUtils.join(ToolbarCustomizer.SEP, this.order));
            editorClear.putString("hidden", TextUtils.join(ToolbarCustomizer.SEP, this.hidden));
            for (Map.Entry<String, String> entry : this.labels.entrySet()) {
                editorClear.putString("label:" + entry.getKey(), entry.getValue());
            }
            editorClear.apply();
        }

        void swapWithNeighbor(String str, int i) {
            int iIndexOf = this.order.indexOf(str);
            if (iIndexOf < 0) {
                return;
            }
            int i2 = iIndexOf + i;
            while (i2 >= 0 && i2 < this.order.size()) {
                if (!this.hidden.contains(this.order.get(i2))) {
                    List<String> list = this.order;
                    list.set(iIndexOf, list.get(i2));
                    this.order.set(i2, str);
                    return;
                }
                i2 += i;
            }
        }
    }

    private static SharedPreferences prefs(Context context) {
        try {
            return context.getApplicationContext().getSharedPreferences(PREFS, 0);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static boolean isActive(Object obj) {
        return str(Refl.call(obj, "getIcon", new Object[0])).endsWith("-active") || Boolean.TRUE.equals(Refl.call(obj, "isChecked", new Object[0]));
    }

    private static String firstChar(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        BreakIterator characterInstance = BreakIterator.getCharacterInstance();
        characterInstance.setText(str);
        return str.substring(characterInstance.first(), characterInstance.next());
    }

    private static String str(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
