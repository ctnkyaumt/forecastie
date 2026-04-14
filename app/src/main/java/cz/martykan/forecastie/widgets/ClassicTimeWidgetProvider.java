package cz.martykan.forecastie.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;

public class ClassicTimeWidgetProvider extends AbstractWidgetProvider {
    private static final String TAG = "ClassicTimeWidget";
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            try {
                RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                        R.layout.time_widget_classic);

                setTheme(context, remoteViews);
                openMainActivity(context, remoteViews);

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                Weather widgetWeather = this.getTodayWeather(context);

                if (widgetWeather == null) {
                    appWidgetManager.updateAppWidget(widgetId, remoteViews);
                    continue;
                }

                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
                String defaultDateFormat = context.getResources().getStringArray(R.array.dateFormatsValues)[0];
                String simpleDateFormat = sp.getString("dateFormat", defaultDateFormat);
                if ("custom".equals(simpleDateFormat)) {
                    simpleDateFormat = sp.getString("dateFormatCustom", defaultDateFormat);
                }
                String dateString;
                try {
                    simpleDateFormat = simpleDateFormat.substring(0, simpleDateFormat.indexOf("-") - 1);
                    try {
                        SimpleDateFormat resultFormat = new SimpleDateFormat(simpleDateFormat);
                        dateString = resultFormat.format(new Date());
                    } catch (IllegalArgumentException e) {
                        dateString = context.getResources().getString(R.string.error_dateFormat);
                    }
                } catch (Exception e) {
                    DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG);
                    dateString = dateFormat.format(new Date());
                }

                remoteViews.setTextViewText(R.id.time, timeFormat.format(new Date()));
                remoteViews.setTextViewText(R.id.date, dateString);
                remoteViews.setTextViewText(R.id.widgetCity, this.getFormattedLocation(widgetWeather));
                remoteViews.setTextViewText(R.id.widgetTemperature, this.getFormattedTemperature(widgetWeather, context, sp));

                String feelsLikeTemperature = this.getFormattedFeelsLikeTemperature(widgetWeather, context, sp);
                if (feelsLikeTemperature != null) {
                    remoteViews.setTextViewText(R.id.widgetFeelsLike, feelsLikeTemperature);
                    remoteViews.setViewVisibility(R.id.widgetFeelsLike, android.view.View.VISIBLE);
                } else {
                    remoteViews.setViewVisibility(R.id.widgetFeelsLike, android.view.View.GONE);
                }

                remoteViews.setTextViewText(R.id.widgetHumidity, widgetWeather.getHumidity() + " %");

                remoteViews.setTextViewText(R.id.widgetDescription, widgetWeather.getDescription());
                remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(widgetWeather, context));

                appWidgetManager.updateAppWidget(widgetId, remoteViews);
            } catch (Exception e) {
                Log.e(TAG, "Error updating widget", e);
            }
        }
        if (appWidgetIds.length > 0) {
            scheduleNextUpdate(context);
        }
    }
}
