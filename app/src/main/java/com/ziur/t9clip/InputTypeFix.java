package com.ziur.t9clip;

final class InputTypeFix {
    static final int TYPE_CLASS_NUMBER = 2;
    static final int TYPE_CLASS_TEXT = 1;
    static final int TYPE_MASK_CLASS = 15;
    static final int TYPE_MASK_VARIATION = 4080;
    static final int TYPE_NUMBER_VARIATION_PASSWORD = 16;
    static final int TYPE_TEXT_VARIATION_PASSWORD = 128;
    static final int TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 144;
    static final int TYPE_TEXT_VARIATION_WEB_PASSWORD = 224;

    static boolean isRealPassword(int i) {
        int i2 = i & TYPE_MASK_CLASS;
        int i3 = i & TYPE_MASK_VARIATION;
        return i2 == TYPE_CLASS_TEXT ? i3 == TYPE_TEXT_VARIATION_PASSWORD || i3 == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || i3 == TYPE_TEXT_VARIATION_WEB_PASSWORD : i2 == TYPE_CLASS_NUMBER && i3 == TYPE_NUMBER_VARIATION_PASSWORD;
    }

    static boolean modThinksPassword(int i) {
        return (i & TYPE_TEXT_VARIATION_PASSWORD) == TYPE_TEXT_VARIATION_PASSWORD || (i & TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || (i & TYPE_TEXT_VARIATION_WEB_PASSWORD) == TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private InputTypeFix() {
    }

    static int fixedInputType(int i) {
        if (!modThinksPassword(i) || isRealPassword(i)) {
            return -1;
        }
        return i & (-4081);
    }
}
