package cz.martykan.forecastie.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import cz.martykan.forecastie.AlarmReceiver;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.TimeUtils;
import cz.martykan.forecastie.utils.UnitConvertor;
import cz.martykan.forecastie.utils.formatters.WeatherFormatter;
import cz.martykan.forecastie.weatherapi.WeatherStorage;

public abstract class AbstractWidgetProvider extends AppWidgetProvider {
    // Widget updates always run on the main thread, so sharing the (non thread safe) formats is fine.
    private static final DecimalFormat TEMPERATURE_FORMAT = new DecimalFormat("#.#");
    private static final DecimalFormat MEASUREMENT_FORMAT = new DecimalFormat("0.0");

    /** Layout inflated for this widget flavour. */
    @LayoutRes
    protected abstract int getLayoutId();

    /** Fill the layout with the current weather. Called only when weather data is available. */
    protected abstract void bindWeather(Context context, RemoteViews remoteViews, Weather weather,
                                        SharedPreferences sp);

    /** Weather-independent setup (clocks, …). Always called, even without weather data. */
    protected void bindStatic(Context context, RemoteViews remoteViews, SharedPreferences sp) {
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        AlarmReceiver.setRecurringAlarm(context.getApplicationContext());
    }

    @Override
    public final void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        // Every instance of a given widget renders identical content, so build the views once
        // and push them to all ids in a single call instead of once per widget.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), getLayoutId());
        setTheme(sp, remoteViews);
        openMainActivity(context, remoteViews);
        setRefreshButton(context, remoteViews);

        try {
            bindStatic(context, remoteViews, sp);
            Weather weather = getTodayWeather(context);
            if (weather != null) {
                bindWeather(context, remoteViews, weather, sp);
            }
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Error updating widget", e);
        }

        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews);
    }

    protected Bitmap getWeatherIcon(Weather weather, Context context) {
        Formatting formatting = new Formatting(context);
        String weatherIcon = formatting.getWeatherIcon(weather.getWeatherId(), TimeUtils.isDayTime(weather, Calendar.getInstance()));
        return WeatherFormatter.getWeatherIconAsBitmap(context, weatherIcon, Color.WHITE);
    }

    @Nullable
    protected Weather getTodayWeather(Context context) {
        return new WeatherStorage(context).getLastToday();
    }

    protected void openMainActivity(Context context, RemoteViews remoteViews) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent);
    }

    protected void setRefreshButton(Context context, RemoteViews remoteViews) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_REFRESH);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.widgetButtonRefresh, pendingIntent);
    }

    private static int pendingIntentFlags(int flags) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? flags | PendingIntent.FLAG_IMMUTABLE
                : flags;
    }

    protected String getFormattedLocation(Weather weather) {
        String city = weather.getCity();
        String country = weather.getCountry();
        if (city == null) city = "";
        if (country == null) country = "";

        if (!city.isEmpty() && !country.isEmpty()) {
            return city + ", " + country;
        }
        return city.isEmpty() ? country : city;
    }

    protected String localize(SharedPreferences sp, Context context, String preferenceKey,
                              String defaultValueKey) {
        MainActivity.initMappings();
        return MainActivity.localize(sp, context, preferenceKey, defaultValueKey);
    }

    public static void updateWidgets(Context context) {
        updateWidgets(context, ExtensiveWidgetProvider.class);
        updateWidgets(context, TimeWidgetProvider.class);
        updateWidgets(context, SimpleWidgetProvider.class);
        updateWidgets(context, ClassicTimeWidgetProvider.class);
    }

    private static void updateWidgets(Context context, Class<? extends AbstractWidgetProvider> widgetClass) {
        Context appContext = context.getApplicationContext();
        int[] ids = AppWidgetManager.getInstance(appContext)
                .getAppWidgetIds(new ComponentName(appContext, widgetClass));
        if (ids.length == 0) {
            return;
        }
        Intent intent = new Intent(appContext, widgetClass)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        appContext.sendBroadcast(intent);
    }

    protected void setTheme(SharedPreferences sp, RemoteViews remoteViews) {
        int background;
        if (sp.getBoolean("transparentWidget", false)) {
            background = R.drawable.widget_card_transparent;
        } else {
            switch (sp.getString("theme", "fresh")) {
                case "dark":
                case "classicdark":
                    background = R.drawable.widget_card_dark;
                    break;
                case "black":
                case "classicblack":
                    background = R.drawable.widget_card_black;
                    break;
                case "classic":
                    background = R.drawable.widget_card_classic;
                    break;
                default:
                    background = R.drawable.widget_card;
                    break;
            }
        }
        remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", background);
    }

    /**
     * Date-only pattern taken from the user preference. The stored patterns look like
     * {@code "d.M.yyyy - HH:mm"}, so the time part after the {@code -} is dropped. Missing or
     * malformed patterns fall back to the locale's long date format. The result is validated
     * because it is handed to a {@link android.widget.TextClock} living in the launcher process,
     * where an invalid pattern would crash the widget host instead of us.
     */
    @NonNull
    protected String getDatePattern(Context context, SharedPreferences sp) {
        String defaultDateFormat = context.getResources().getStringArray(R.array.dateFormatsValues)[0];
        String pattern = sp.getString("dateFormat", defaultDateFormat);
        if ("custom".equals(pattern)) {
            pattern = sp.getString("dateFormatCustom", defaultDateFormat);
        }
        int separator = pattern.indexOf('-');
        if (separator > 0) {
            pattern = pattern.substring(0, separator - 1);
            try {
                new SimpleDateFormat(pattern, Locale.getDefault());
                return pattern;
            } catch (IllegalArgumentException ignored) {
                // Fall through to the locale default below.
            }
        }
        return android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMMy");
    }

    /**
     * Binds a {@link android.widget.TextClock}, which then keeps itself ticking in the widget host
     * without us scheduling any alarms. A {@code null} pattern makes it show the time in the
     * user's preferred 12/24 hour format.
     */
    protected void setClockFormat(RemoteViews remoteViews, int viewId, @Nullable String pattern) {
        remoteViews.setCharSequence(viewId, "setFormat12Hour", pattern);
        remoteViews.setCharSequence(viewId, "setFormat24Hour", pattern);
    }

    protected String getFormattedTemperature(Weather weather, Context context, SharedPreferences sp) {
        return formatTemperature(UnitConvertor.convertTemperature((float) weather.getTemperature(), sp), context, sp);
    }

    @Nullable
    protected String getFormattedFeelsLikeTemperature(Weather weather, Context context, SharedPreferences sp) {
        if (!weather.isFeelsLikeTemperatureAvailable()) {
            return null;
        }
        return formatTemperature(UnitConvertor.convertTemperature(weather.getFeelsLikeTemperature().floatValue(), sp), context, sp);
    }

    private String formatTemperature(float temperature, Context context, SharedPreferences sp) {
        if (sp.getBoolean("temperatureInteger", false)) {
            temperature = Math.round(temperature);
        }
        return TEMPERATURE_FORMAT.format(temperature) + localize(sp, context, "unit", "C");
    }

    protected String getFormattedPressure(Weather weather, Context context, SharedPreferences sp) {
        double pressure = UnitConvertor.convertPressure((float) weather.getPressure(), sp);

        return MEASUREMENT_FORMAT.format(pressure) + " " + localize(sp, context, "pressureUnit", "hPa");
    }

    protected String getFormattedWind(Weather weather, Context context, SharedPreferences sp) {
        double wind = UnitConvertor.convertWind(weather.getWind(), sp);

        return MEASUREMENT_FORMAT.format(wind) + " " + localize(sp, context, "speedUnit", "m/s")
                    + (weather.isWindDirectionAvailable() ? " " + MainActivity.getWindDirectionString(sp, context, weather) : "");
    }
}
