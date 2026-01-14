package cz.martykan.forecastie.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

import cz.martykan.forecastie.AlarmReceiver;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;

public class SimpleWidgetProvider extends AbstractWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.simple_widget);

            setTheme(context, remoteViews);
            openMainActivity(context, remoteViews);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            Weather widgetWeather = this.getTodayWeather(context);

            if (widgetWeather == null) {
                appWidgetManager.updateAppWidget(widgetId, remoteViews);
                continue;
            }

            remoteViews.setTextViewText(R.id.widgetCity, this.getFormattedLocation(widgetWeather));
            remoteViews.setTextViewText(R.id.widgetTemperature, this.getFormattedTemperature(widgetWeather, context, sp));

            String feelsLikeTemperature = this.getFormattedFeelsLikeTemperature(widgetWeather, context, sp);
            if (feelsLikeTemperature != null) {
                remoteViews.setTextViewText(R.id.widgetFeelsLike, context.getString(R.string.feels_like) + ": " + feelsLikeTemperature);
                remoteViews.setViewVisibility(R.id.widgetFeelsLike, android.view.View.VISIBLE);
            } else {
                remoteViews.setViewVisibility(R.id.widgetFeelsLike, android.view.View.GONE);
            }

            remoteViews.setTextViewText(R.id.widgetHumidity, context.getString(R.string.humidity) + ": " + widgetWeather.getHumidity() + " %");

            remoteViews.setTextViewText(R.id.widgetDescription, widgetWeather.getDescription());
            remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(widgetWeather, context));

            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
        if (appWidgetIds.length > 0) {
            scheduleNextUpdate(context);
        }
    }
}
