package cz.martykan.forecastie.utils;

import java.util.Locale;

public class Language {

    /**
     * Returns the language code corresponding to the system's default
     * language.
     * @return language code
     */
    public static String getLanguageCode() {
        String language = Locale.getDefault().getLanguage();

        if (language.equals(new Locale("cs").getLanguage())) { // Czech
            return "cs";
        } else if (language.equals(new Locale("ko").getLanguage())) { // Korean
            return "ko";
        } else if (language.equals(new Locale("lv").getLanguage())) { // Latvian
            return "lv";
        } else {
            return language;
        }
    }

}
