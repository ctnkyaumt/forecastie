package cz.martykan.forecastie.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;

public class SimpleWidgetProvider extends AbstractWidgetProvider {
    @Override
    protected int getLayoutId() {
        return R.layout.simple_widget;
    }

    @Override
    protected void bindWeather(Context context, RemoteViews remoteViews, Weather weather,
                               SharedPreferences sp) {
        remoteViews.setTextViewText(R.id.widgetCity, getFormattedLocation(weather));
        remoteViews.setTextViewText(R.id.widgetTemperature, getFormattedTemperature(weather, context, sp));

        String feelsLikeTemperature = getFormattedFeelsLikeTemperature(weather, context, sp);
        if (feelsLikeTemperature != null) {
            remoteViews.setTextViewText(R.id.widgetFeelsLike, context.getString(R.string.feels_like) + ": " + feelsLikeTemperature);
            remoteViews.setViewVisibility(R.id.widgetFeelsLike, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.widgetFeelsLike, View.GONE);
        }

        remoteViews.setTextViewText(R.id.widgetHumidity, context.getString(R.string.humidity) + ": " + weather.getHumidity() + " %");
        remoteViews.setTextViewText(R.id.widgetDescription, weather.getDescription());
        remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(weather, context));
    }
}
