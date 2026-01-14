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
        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly == null) return weatherList;

        JSONArray times = hourly.optJSONArray("time");
        JSONArray temperatures = hourly.optJSONArray("temperature_2m");
        JSONArray apparentTemperatures = hourly.optJSONArray("apparent_temperature");
        JSONArray humidities = hourly.optJSONArray("relativehumidity_2m");
        JSONArray weatherCodes = hourly.optJSONArray("weathercode");
        JSONArray pressures = hourly.optJSONArray("pressure_msl");
        JSONArray windSpeeds = hourly.optJSONArray("windspeed_10m");
        JSONArray windDirections = hourly.optJSONArray("winddirection_10m");
        JSONArray rains = hourly.optJSONArray("rain");
        JSONArray precipProbs = hourly.optJSONArray("precipitation_probability");

        if (times == null) return weatherList;

        double lat = root.optDouble("latitude", 0);
        double lon = root.optDouble("longitude", 0);

        // Daily data for sunrise/sunset
        JSONObject daily = root.optJSONObject("daily");
        JSONArray sunriseArray = null;
        JSONArray sunsetArray = null;
        if (daily != null) {
            sunriseArray = daily.optJSONArray("sunrise");
            sunsetArray = daily.optJSONArray("sunset");
        }

        for (int i = 0; i < times.length(); i++) {
            Weather weather = new Weather();
            weather.setDate(new Date(times.optLong(i, 0) * 1000));
            if (temperatures != null && i < temperatures.length()) weather.setTemperature(temperatures.optDouble(i, 0) + 273.15);
            if (apparentTemperatures != null && i < apparentTemperatures.length()) weather.setFeelsLikeTemperature(apparentTemperatures.optDouble(i, 0) + 273.15);
            if (humidities != null && i < humidities.length()) weather.setHumidity(humidities.optInt(i, 0));
            if (weatherCodes != null && i < weatherCodes.length()) {
                int code = weatherCodes.optInt(i, 0);
                weather.setWeatherId(mapWmoToOwm(code));
                weather.setDescription(mapWmoToDescription(code));
            }
            if (pressures != null && i < pressures.length()) weather.setPressure(pressures.optInt(i, 0));
            if (windSpeeds != null && i < windSpeeds.length()) weather.setWind(windSpeeds.optDouble(i, 0));
            if (windDirections != null && i < windDirections.length()) weather.setWindDirectionDegree(windDirections.optDouble(i, 0));
            weather.setLat(lat);
            weather.setLon(lon);
            if (rains != null && i < rains.length()) weather.setRain(rains.optDouble(i, 0));
            if (precipProbs != null && i < precipProbs.length()) weather.setChanceOfPrecipitation(precipProbs.optDouble(i, 0) / 100.0);
            
            if (sunriseArray != null && sunriseArray.length() > 0) {
                weather.setSunrise(new Date(sunriseArray.optLong(0, 0) * 1000));
            }
            if (sunsetArray != null && sunsetArray.length() > 0) {
                weather.setSunset(new Date(sunsetArray.optLong(0, 0) * 1000));
            }

            weather.setLastUpdated(Calendar.getInstance().getTimeInMillis());
            weatherList.add(weather);
        }

        return weatherList;
    }

    @NonNull
    public static Weather convertJsonToWeather(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        JSONObject current = root.optJSONObject("current_weather");
        
        Weather weather = new Weather();
        if (current != null) {
            weather.setDate(new Date(current.optLong("time", 0) * 1000));
            weather.setTemperature(current.optDouble("temperature", 0) + 273.15);
            int code = current.optInt("weathercode", 0);
            weather.setWeatherId(mapWmoToOwm(code));
            weather.setDescription(mapWmoToDescription(code));
            weather.setWind(current.optDouble("windspeed", 0));
            weather.setWindDirectionDegree(current.optDouble("winddirection", 0));
        } else {
            weather.setDate(new Date());
        }

        weather.setLat(root.optDouble("latitude", 0));
        weather.setLon(root.optDouble("longitude", 0));
        
        // Open-Meteo current_weather doesn't have humidity/pressure, get from hourly if available
        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray apparentTempArray = hourly.optJSONArray("apparent_temperature");
            if (apparentTempArray != null && apparentTempArray.length() > 0) {
                weather.setFeelsLikeTemperature(apparentTempArray.optDouble(0, 0));
            }
            JSONArray humArray = hourly.optJSONArray("relativehumidity_2m");
            if (humArray != null && humArray.length() > 0) {
                weather.setHumidity(humArray.optInt(0, 0));
            }
            JSONArray pressArray = hourly.optJSONArray("pressure_msl");
            if (pressArray != null && pressArray.length() > 0) {
                weather.setPressure(pressArray.optInt(0, 0));
            }
            JSONArray rainArray = hourly.optJSONArray("rain");
            if (rainArray != null && rainArray.length() > 0) {
                weather.setRain(rainArray.optDouble(0, 0));
            }
            JSONArray precipProbArray = hourly.optJSONArray("precipitation_probability");
            if (precipProbArray != null && precipProbArray.length() > 0) {
                weather.setChanceOfPrecipitation(precipProbArray.optDouble(0, 0) / 100.0);
            }
        }

        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray sunriseArray = daily.optJSONArray("sunrise");
            JSONArray sunsetArray = daily.optJSONArray("sunset");
            if (sunriseArray != null && sunriseArray.length() > 0) {
                weather.setSunrise(new Date(sunriseArray.optLong(0, 0) * 1000));
            }
            if (sunsetArray != null && sunsetArray.length() > 0) {
                weather.setSunset(new Date(sunsetArray.optLong(0, 0) * 1000));
            }
        }

        weather.setLastUpdated(Calendar.getInstance().getTimeInMillis());
        return weather;
    }

    public static double convertJsonToUVIndex(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray uviArray = daily.optJSONArray("uv_index_max");
            if (uviArray != null && uviArray.length() > 0) {
                return uviArray.getDouble(0);
            }
        }
        return 0;
    }

    public static String mapWmoToDescription(int wmoCode) {
        switch (wmoCode) {
            case 0: return "Clear sky";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 56:
            case 57: return "Freezing Drizzle";
            case 61:
            case 63:
            case 65: return "Rain";
            case 66:
            case 67: return "Freezing Rain";
            case 71:
            case 73:
            case 75: return "Snow fall";
            case 77: return "Snow grains";
            case 80:
            case 81:
            case 82: return "Rain showers";
            case 85:
            case 86: return "Snow showers";
            case 95:
            case 96:
            case 99: return "Thunderstorm";
            default: return "Unknown";
        }
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
