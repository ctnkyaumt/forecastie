package cz.martykan.forecastie.weatherapi;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.List;

import cz.martykan.forecastie.models.ImmutableWeather;
import cz.martykan.forecastie.models.Weather;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 34)
public class OpenMeteoJsonParserTests {
    /** Local midnight, 01:00 and 02:00 as unix seconds, matching Open-Meteo's hourly grid. */
    private static final long HOUR_0 = 1785099600L;
    private static final long HOUR_1 = HOUR_0 + 3600;
    private static final long HOUR_2 = HOUR_0 + 7200;

    /**
     * Response shaped like the live API: {@code current_weather.time} sits on the 15 minute grid
     * (interval 900) so it never equals an hourly timestamp.
     */
    private static String response(long currentTime) {
        return "{"
                + "\"latitude\":41.0,\"longitude\":29.0,\"utc_offset_seconds\":10800,"
                + "\"current_weather\":{\"time\":" + currentTime + ",\"interval\":900,"
                + "\"temperature\":28.5,\"windspeed\":8.9,\"winddirection\":201,\"weathercode\":0},"
                + "\"hourly\":{"
                + "\"time\":[" + HOUR_0 + "," + HOUR_1 + "," + HOUR_2 + "],"
                + "\"temperature_2m\":[23.2,24.0,28.4],"
                + "\"apparent_temperature\":[25.1,26.0,31.7],"
                + "\"relativehumidity_2m\":[80,75,52],"
                + "\"pressure_msl\":[1010,1011,1012],"
                + "\"rain\":[0,0,0],"
                + "\"precipitation_probability\":[0,0,10]"
                + "},"
                + "\"daily\":{\"time\":[" + HOUR_0 + "],\"sunrise\":[" + (HOUR_0 + 21330) + "],"
                + "\"sunset\":[" + (HOUR_0 + 73560) + "],\"uv_index_max\":[7.5]}"
                + "}";
    }

    @Test
    public void hourlyIndexPicksSlotAlreadyStarted() throws JSONException {
        JSONArray times = new JSONArray("[" + HOUR_0 + "," + HOUR_1 + "," + HOUR_2 + "]");

        Assert.assertEquals("time on the hour maps to its own slot",
                1, OpenMeteoJsonParser.hourlyIndexFor(times, HOUR_1));
        Assert.assertEquals("time between slots maps to the earlier one",
                1, OpenMeteoJsonParser.hourlyIndexFor(times, HOUR_1 + 1800));
        Assert.assertEquals("time before the first slot maps to the first one",
                0, OpenMeteoJsonParser.hourlyIndexFor(times, HOUR_0 - 60));
        Assert.assertEquals("time after the last slot maps to the last one",
                2, OpenMeteoJsonParser.hourlyIndexFor(times, HOUR_2 + 9000));
        Assert.assertEquals("missing times fall back to the first slot",
                0, OpenMeteoJsonParser.hourlyIndexFor(null, HOUR_1));
    }

    @Test
    public void currentWeatherUsesCurrentHourNotMidnight() throws JSONException {
        // 02:30 local - between hourly slots, which is why an exact match never worked.
        Weather weather = OpenMeteoJsonParser.convertJsonToWeather(response(HOUR_2 + 1800));

        Assert.assertEquals("temperature comes from current_weather",
                28.5 + 273.15, weather.getTemperature(), 0.001);
        Assert.assertTrue("feels like should be available", weather.isFeelsLikeTemperatureAvailable());
        Assert.assertEquals("feels like should come from the current hour, not index 0",
                31.7 + 273.15, weather.getFeelsLikeTemperature(), 0.001);
        Assert.assertEquals("humidity should come from the current hour", 52, weather.getHumidity());
        Assert.assertEquals("pressure should come from the current hour", 1012, weather.getPressure());
    }

    @Test
    public void notificationWeatherUsesCurrentHourNotMidnight() {
        ImmutableWeather weather = ImmutableWeather.fromJson(response(HOUR_2 + 1800), 1);

        Assert.assertEquals("temperature comes from current_weather",
                28.5f + 273.15f, weather.getTemperature(), 0.001f);
        Assert.assertEquals("feels like should come from the current hour, not index 0",
                31.7f + 273.15f, weather.getFeelsLikeTemperature(), 0.001f);
        Assert.assertEquals("humidity should come from the current hour", 52, weather.getHumidity());
    }

    @Test
    public void missingApparentTemperatureIsReportedAsUnavailable() throws JSONException {
        String json = response(HOUR_2 + 1800).replace("\"apparent_temperature\":[25.1,26.0,31.7],", "");

        Weather weather = OpenMeteoJsonParser.convertJsonToWeather(json);

        Assert.assertFalse("feels like must not fall back to 0 °C when the API omits it",
                weather.isFeelsLikeTemperatureAvailable());
    }

    @Test
    public void longTermListKeepsPerHourValues() throws JSONException {
        List<Weather> list = OpenMeteoJsonParser.convertJsonToWeatherList(response(HOUR_2 + 1800));

        Assert.assertEquals(3, list.size());
        Assert.assertEquals(23.2 + 273.15, list.get(0).getTemperature(), 0.001);
        Assert.assertEquals(31.7 + 273.15, list.get(2).getFeelsLikeTemperature(), 0.001);
    }

    @Test
    public void longTermListReportsMissingApparentTemperature() throws JSONException {
        String json = response(HOUR_2 + 1800).replace("\"apparent_temperature\":[25.1,26.0,31.7],", "");

        List<Weather> list = OpenMeteoJsonParser.convertJsonToWeatherList(json);

        Assert.assertFalse("feels like must not fall back to 0 °C when the API omits it",
                list.get(0).isFeelsLikeTemperatureAvailable());
    }
}
