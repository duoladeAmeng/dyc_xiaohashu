package com.dyc.framework.common.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class NumberUtils {

    private NumberUtils() {
    }

    public static String formatNumberString(long number) {
        if (number < 10000) {
            return String.valueOf(number);
        }
        if (number < 100000000) {
            double result = number / 10000.0;
            DecimalFormat decimalFormat = new DecimalFormat("#.#");
            decimalFormat.setRoundingMode(RoundingMode.DOWN);
            return decimalFormat.format(result) + "万";
        }
        return "9999万";
    }
}
