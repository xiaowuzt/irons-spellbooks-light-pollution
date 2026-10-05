package com.gang.lightpollution;

import java.util.Locale;

/** The selector is local to this installation; it is never synchronized from a server. */
public enum ConfigCommentLanguage {
    AUTO, EN_US, ZH_CN, BILINGUAL;

    public ConfigCommentLanguage resolve(boolean physicalClient, String gameLanguage) {
        if (this != AUTO) return this;
        if (!physicalClient) return BILINGUAL;
        String normalized = gameLanguage == null ? "" : gameLanguage.toLowerCase(Locale.ROOT);
        return normalized.equals("lzh") || normalized.startsWith("zh_") ? ZH_CN : EN_US;
    }
}
