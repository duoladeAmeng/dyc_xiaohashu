package com.dyc.framework.common.util;

import java.util.regex.Pattern;

public final class ParamUtils {

    private static final int NICK_NAME_MIN_LENGTH = 2;
    private static final int NICK_NAME_MAX_LENGTH = 24;
    private static final Pattern NICK_NAME_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");

    private static final int ID_MIN_LENGTH = 6;
    private static final int ID_MAX_LENGTH = 15;
    private static final Pattern XIAOHASHU_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private ParamUtils() {
    }

    public static boolean checkNickname(String nickname) {
        if (nickname == null || nickname.length() < NICK_NAME_MIN_LENGTH || nickname.length() > NICK_NAME_MAX_LENGTH) {
            return false;
        }
        return !NICK_NAME_PATTERN.matcher(nickname).find();
    }

    public static boolean checkXiaohashuId(String xiaohashuId) {
        if (xiaohashuId == null || xiaohashuId.length() < ID_MIN_LENGTH || xiaohashuId.length() > ID_MAX_LENGTH) {
            return false;
        }
        return XIAOHASHU_ID_PATTERN.matcher(xiaohashuId).matches();
    }

    public static boolean checkLength(String str, int length) {
        return str != null && !str.isEmpty() && str.length() <= length;
    }
}
