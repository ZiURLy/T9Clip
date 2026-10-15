package com.ziur.t9clip;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class Refl {
    private Refl() {
    }

    static Object field(Object obj, String str) {
        if (obj == null) {
            return null;
        }
        for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
            try {
                Field declaredField = superclass.getDeclaredField(str);
                declaredField.setAccessible(true);
                return declaredField.get(obj);
            } catch (NoSuchFieldException unused) {
            } catch (Throwable unused2) {
                return null;
            }
        }
        return null;
    }

    static boolean setBoolean(Object obj, String str, boolean z) {
        if (obj == null) {
            return false;
        }
        for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
            try {
                Field declaredField = superclass.getDeclaredField(str);
                declaredField.setAccessible(true);
                declaredField.setBoolean(obj, z);
                return true;
            } catch (NoSuchFieldException unused) {
            } catch (Throwable unused2) {
                return false;
            }
        }
        return false;
    }

    static Object valueOfType(Object obj, Class<?> cls) {
        if (obj == null) {
            return null;
        }
        for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
            for (Field field : superclass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    try {
                        field.setAccessible(true);
                        Object obj2 = field.get(obj);
                        if (cls.isInstance(obj2)) {
                            return obj2;
                        }
                    } catch (Throwable unused) {
                        continue;
                    }
                }
            }
        }
        return null;
    }

    static Method method(Class<?> cls, String str, int i) {
        while (cls != null && cls != Object.class) {
            for (Method method : cls.getDeclaredMethods()) {
                if (method.getName().equals(str) && method.getParameterTypes().length == i) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    static Object call(Object obj, String str, Object... objArr) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = method(obj.getClass(), str, objArr.length);
            if (method == null) {
                return null;
            }
            return method.invoke(obj, objArr);
        } catch (Throwable th) {
            L.e("调用 " + str + " 失败", th);
            return null;
        }
    }

    static Object callByReturnType(Object obj, String str, Class<?> cls) {
        boolean zEquals;
        if (obj == null) {
            return null;
        }
        for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
            for (Method method : superclass.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0) {
                    Class<?> returnType = method.getReturnType();
                    if (str != null) {
                        zEquals = returnType.getName().equals(str);
                    } else {
                        zEquals = (cls == null || !cls.isAssignableFrom(returnType) || returnType == Object.class) ? false : true;
                    }
                    if (zEquals) {
                        try {
                            method.setAccessible(true);
                            return method.invoke(obj, new Object[0]);
                        } catch (Throwable unused) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
            }
        }
        return null;
    }
}
