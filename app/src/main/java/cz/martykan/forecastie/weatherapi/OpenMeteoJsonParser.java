package cz.martykan.forecastie.weatherapi;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import cz.martykan.forecastie.models.Weather;

public class OpenMeteoJsonParser {

    @NonNull
    public static List<Weather> convertJsonToWeatherList(String jsonString) throws JSONException {
        List<Weather> weatherList = new ArrayList<>();
        JSONObject root = new JSONObject(jsonString);
        JSONObject hourly = root.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temperatures = hourly.getJSONArray("temperature_2m");
        JSONArray humidities = hourly.getJSONArray("relativehumidity_2m");
        JSONArray weatherCodes = hourly.getJSONArray("weathercode");
        JSONArray pressures = hourly.getJSONArray("pressure_msl");
        JSONArray windSpeeds = hourly.getJSONArray("windspeed_10m");
        JSONArray windDirections = hourly.getJSONArray("winddirection_10m");
        JSONArray rains = hourly.optJSONArray("rain");
        JSONArray precipProbs = hourly.optJSONArray("precipitation_probability");

        double lat = root.getDouble("latitude");
        double lon = root.getDouble("longitude");

        // Daily data for sunrise/sunset (only first day used for all hourly entries in OWM logic usually, but we can be more precise)
        JSONObject daily = root.optJSONObject("daily");
        long sunrise = 0;
        long sunset = 0;
        if (daily != null) {
            sunrise = daily.getJSONArray("sunrise").getLong(0);
            sunset = daily.getJSONArray("sunset").getLong(0);
        }

        for (int i = 0; i < times.length(); i++) {
            Weather weather = new Weather();
            weather.setDate(new Date(times.getLong(i) * 1000));
            weather.setTemperature(temperatures.getDouble(i));
            weather.setHumidity(humidities.getInt(i));
            weather.setWeatherId(mapWmoToOwm(weatherCodes.getInt(i)));
            weather.setPressure(pressures.getInt(i));
            weather.setWind(windSpeeds.getDouble(i));
            weather.setWindDirectionDegree(windDirections.getDouble(i));
            weather.setLat(lat);
            weather.setLon(lon);
            if (rains != null) weather.setRain(rains.getDouble(i));
            if (precipProbs != null) weather.setChanceOfPrecipitation(precipProbs.getDouble(i) / 100.0);
            
            if (sunrise != 0) {
                weather.setSunrise(new Date(sunrise * 1000));
                weather.setSunset(new Date(sunset * 1000));
            }

            weather.setLastUpdated(Calendar.getInstance().getTimeInMillis());
            weatherList.add(weather);
        }

        return weatherList;
    }

    @NonNull
    public static Weather convertJsonToWeather(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        JSONObject current = root.getJSONObject("current_weather");
        
        Weather weather = new Weather();
        weather.setDate(new Date(current.getLong("time") * 1000));
        weather.setTemperature(current.getDouble("temperature"));
        weather.setWeatherId(mapWmoToOwm(current.getInt("weathercode")));
        weather.setWind(current.getDouble("windspeed"));
        weather.setWindDirectionDegree(current.getDouble("winddirection"));
        weather.setLat(root.getDouble("latitude"));
        weather.setLon(root.getDouble("longitude"));
        
        // Open-Meteo current_weather doesn't have humidity/pressure, get from hourly if available
        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            weather.setHumidity(hourly.getJSONArray("relativehumidity_2m").getInt(0));
            weather.setPressure(hourly.getJSONArray("pressure_msl").getInt(0));
        }

        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            weather.setSunrise(new Date(daily.getJSONArray("sunrise").getLong(0) * 1000));
            weather.setSunset(new Date(daily.getJSONArray("sunset").getLong(0) * 1000));
        }

        weather.setLastUpdated(Calendar.getInstance().getTimeInMillis());
        return weather;
    }

    public static double convertJsonToUVIndex(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            return daily.getJSONArray("uv_index_max").getDouble(0);
        }
        return 0;
    }

    public static int mapWmoToOwm(int wmoCode) {
        switch (wmoCode) {
            case 0: return 800; // Clear sky
            case 1: return 801; // Mainly clear
            case 2: return 802; // Partly cloudy
            case 3: return 804; // Overcast
            case 45:
            case 48: return 741; // Fog
            case 51:
            case 53:
            case 55: return 301; // Drizzle
            case 56:
            case 57: return 302; // Freezing Drizzle
            case 61:
            case 63:
            case 65: return 501; // Rain
            case 66:
            case 67: return 511; // Freezing Rain
            case 71:
            case 73:
            case 75: return 601; // Snow fall
            case 77: return 611; // Snow grains
            case 80:
            case 81:
            case 82: return 521; // Rain showers
            case 85:
            case 86: return 621; // Snow showers
            case 95:
            case 96:
            case 99: return 211; // Thunderstorm
            default: return 800;
        }
    }
}
