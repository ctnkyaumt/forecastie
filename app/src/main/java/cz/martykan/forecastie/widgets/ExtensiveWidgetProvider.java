package cz.martykan.forecastie.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.models.Weather;

public class ExtensiveWidgetProvider extends AbstractWidgetProvider {
    @Override
    protected int getLayoutId() {
        return R.layout.extensive_widget;
    }

    @Override
    protected void bindWeather(Context context, RemoteViews remoteViews, Weather weather,
                               SharedPreferences sp) {
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);

        remoteViews.setTextViewText(R.id.widgetCity, getFormattedLocation(weather));
        remoteViews.setTextViewText(R.id.widgetTemperature, getFormattedTemperature(weather, context, sp));
        remoteViews.setTextViewText(R.id.widgetDescription, weather.getDescription());

        String feelsLikeTemperature = getFormattedFeelsLikeTemperature(weather, context, sp);
        if (feelsLikeTemperature != null) {
            remoteViews.setTextViewText(R.id.widgetFeelsLike, context.getString(R.string.feels_like) + ": " + feelsLikeTemperature);
            remoteViews.setViewVisibility(R.id.widgetFeelsLike, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.widgetFeelsLike, View.GONE);
        }

        remoteViews.setTextViewText(R.id.widgetWind, context.getString(R.string.wind) + ": " + getFormattedWind(weather, context, sp));
        remoteViews.setTextViewText(R.id.widgetPressure, context.getString(R.string.pressure) + ": " + getFormattedPressure(weather, context, sp));
        remoteViews.setTextViewText(R.id.widgetHumidity, context.getString(R.string.humidity) + ": " + weather.getHumidity() + " %");
        remoteViews.setTextViewText(R.id.widgetSunrise, context.getString(R.string.sunrise) + ": " + timeFormat.format(weather.getSunrise()));
        remoteViews.setTextViewText(R.id.widgetSunset, context.getString(R.string.sunset) + ": " + timeFormat.format(weather.getSunset()));
        remoteViews.setTextViewText(R.id.widgetLastUpdate, context.getString(R.string.last_update_widget,
                MainActivity.formatTimeWithDayIfNotToday(context, weather.getLastUpdated())));
        remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(weather, context));
    }
}
