package cz.martykan.forecastie.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.UnitConvertor;

/**
 * Weather information.
 * <br/>
 * To create pass json into {@link #fromJson(String, long)}. For default value use
 * {@link ImmutableWeather#EMPTY}.
 */
public class ImmutableWeather implements Parcelable {
    /**
     * Value object for unknown weather (like there is no information to parse).
     */
    public static final ImmutableWeather EMPTY = new ImmutableWeather();

    /**
     * Valid numbers of wind directions:
     * <ul>
     * <li>{@link #WIND_DIRECTIONS_SIMPLE} - base four directions: north, east, south and west.</li>
     * <li>{@link #WIND_DIRECTIONS_ARROWS} - eight directions for arrows.</li>
     * <li>{@link #WIND_DIRECTIONS_MAX} - all sixteen directions.</li>
     * </ul>
     */
    @IntDef({WIND_DIRECTIONS_SIMPLE, WIND_DIRECTIONS_ARROWS, WIND_DIRECTIONS_MAX})
    public @interface NumberOfWindDirections {}
    /** Base four directions: north, east, south and west. */
    public static final int WIND_DIRECTIONS_SIMPLE = 4;
    /** Eight directions for arrows. */
    public static final int WIND_DIRECTIONS_ARROWS = 8;
    /** All sixteen directions. */
    public static final int WIND_DIRECTIONS_MAX = 16;

    private float temperature = Float.MIN_VALUE;
    private float feelsLikeTemperature = Float.MIN_VALUE;
    private double pressure = Double.MIN_VALUE;
    private int humidity = -1;
    private double windSpeed = Double.MIN_VALUE;
    private double rain = 0;
    private double chanceOfPrecipitation = -1;
    private Weather.WindDirection windDirection = null;
    private long sunrise = -1L;
    private long sunset = -1L;
    private String city = "";
    private String country = "";
    private String description = "";
    private int weatherIcon = -1;
    private long lastUpdate = -1L;

    private ImmutableWeather() {}

    /**
     * Parse OpenWeatherMap response json and initialize object with weather information.
     * <br/>
     * If {@code json} is empty or has empty object (i.e. {@code "{}"}), {@link #EMPTY} will be
     * returned.
     *
     * @param json json with weather information from OWM.
     * @param lastUpdate time of retrieving response in milliseconds.
     * @return parsed OWM response
     * @throws NullPointerException if {@code json} is null.
     */
    @NonNull
    public static ImmutableWeather fromJson(@NonNull String json, long lastUpdate)
            throws NullPointerException {
        //noinspection ConstantConditions
        if (json == null)
            throw new NullPointerException("json should not be null");

        try {
            final JSONObject reader = new JSONObject(json);
            if (reader.length() == 0)
                return EMPTY;
            else {
                final ImmutableWeather result = new ImmutableWeather();
                result.lastUpdate = lastUpdate;

                final JSONObject main = reader.optJSONObject("main");
                if (main != null) {
                    // OWM format
                    result.temperature = getFloat("temp", Float.MIN_VALUE, main);
                    result.pressure = getDouble("pressure", Double.MIN_VALUE, main);
                    result.humidity = getInt("humidity", -1, main);

                    final JSONObject rain = reader.optJSONObject("rain");
                    if (rain != null) {
                        result.rain = getDouble("3h", 0, rain);
                        if (result.rain == 0) result.rain = getDouble("1h", 0, rain);
                    }
                } else {
                    // Open-Meteo format
                    JSONObject current = reader.optJSONObject("current_weather");
                    if (current != null) {
                        result.temperature = (float) current.optDouble("temperature", Float.MIN_VALUE);
                    }
                    JSONObject hourly = reader.optJSONObject("hourly");
                    if (hourly != null) {
                        JSONArray apparentTempArray = hourly.optJSONArray("apparent_temperature");
                        if (apparentTempArray != null && apparentTempArray.length() > 0) {
                            result.feelsLikeTemperature = (float) apparentTempArray.optDouble(0, Float.MIN_VALUE);
                        }
                        JSONArray humArray = hourly.optJSONArray("relativehumidity_2m");
                        if (humArray != null && humArray.length() > 0) {
                            result.humidity = humArray.optInt(0, -1);
                        }
                        JSONArray pressArray = hourly.optJSONArray("pressure_msl");
                        if (pressArray != null && pressArray.length() > 0) {
                            result.pressure = pressArray.optDouble(0, Double.MIN_VALUE);
                        }
                        JSONArray rainArray = hourly.optJSONArray("rain");
                        if (rainArray != null && rainArray.length() > 0) {
                            result.rain = rainArray.optDouble(0, 0);
                        }
                        JSONArray chanceArray = hourly.optJSONArray("precipitation_probability");
                        if (chanceArray != null && chanceArray.length() > 0) {
                            result.chanceOfPrecipitation = chanceArray.optDouble(0, -1);
                        }
                    }
                }

                final JSONObject wind = reader.optJSONObject("wind");
                if (wind != null) {
                    // wind speed
                    result.windSpeed = getDouble("speed", Double.MIN_VALUE, wind);
                    // wind direction
                    int degree = getInt("deg", Integer.MIN_VALUE, wind);
                    result.windDirection = degree == Integer.MIN_VALUE
                            ? null
                            : Weather.WindDirection.byDegree(degree);
                } else {
                    JSONObject current = reader.optJSONObject("current_weather");
                    if (current != null) {
                        result.windSpeed = current.optDouble("windspeed", Double.MIN_VALUE);
                        int degree = current.optInt("winddirection", Integer.MIN_VALUE);
                        result.windDirection = degree == Integer.MIN_VALUE
                                ? null
                                : Weather.WindDirection.byDegree(degree);
                    }
                }

                final JSONArray weather = reader.optJSONArray("weather");
                final JSONObject todayWeather = weather != null ? weather.optJSONArray(0) != null ? null : weather.optJSONObject(0) : null;
                // description
                if (todayWeather != null) {
                    result.description = todayWeather.optString("description", "");
                    result.weatherIcon = getInt("id", -1, todayWeather);
                } else {
                    JSONObject current = reader.optJSONObject("current_weather");
                    if (current != null) {
                        int code = current.optInt("weathercode", -1);
                        if (code != -1) {
                            result.description = OpenMeteoJsonParser.mapWmoToDescription(code);
                            result.weatherIcon = OpenMeteoJsonParser.mapWmoToOwm(code);
                        }
                    }
                }
                if (result.weatherIcon < -1)
                    result.weatherIcon = -1;

                final JSONObject sys = reader.optJSONObject("sys");
                if (sys != null) {
                    // country
                    result.country = sys.optString("country", "");
                    // sunrise
                    result.sunrise = getTimestamp("sunrise", -1L, sys);
                    // sunset
                    result.sunset = getTimestamp("sunset", -1L, sys);
                } else {
                    JSONObject daily = reader.optJSONObject("daily");
                    if (daily != null) {
                        JSONArray sunriseArray = daily.optJSONArray("sunrise");
                        JSONArray sunsetArray = daily.optJSONArray("sunset");
                        if (sunriseArray != null && sunriseArray.length() > 0) {
                            result.sunrise = sunriseArray.optLong(0, -1L) * 1000;
                        }
                        if (sunsetArray != null && sunsetArray.length() > 0) {
                            result.sunset = sunsetArray.optLong(0, -1L) * 1000;
                        }
                    }
                }

                // city
                result.city = reader.optString("name", "");
                if (result.city.isEmpty()) {
                    // Open-Meteo doesn't have city name in JSON, it's usually set from storage
                }

                return result;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return EMPTY;
        }
    }

    /**
     * Returns temperature in kelvins.
     * <br/>
     * Default value for invalid data: {@link Float#MIN_VALUE}.
     * @return temperature in kelvins
     * @see #getTemperature(String)
     */
    public float getTemperature() {
        return temperature;
    }

    /**
     * Returns "feels like" temperature in kelvins.
     * <br/>
     * Default value for invalid data: {@link Float#MIN_VALUE}.
     * @return "feels like" temperature in kelvins
     */
    public float getFeelsLikeTemperature() {
        return feelsLikeTemperature;
    }

    /**
     * Returns "feels like" temperature in specified [unit].
     * <br/>
     * Default value for invalid data: {@link Float#MIN_VALUE}.
     * @param unit resulted unit
     * @return "feels like" temperature in specified unit
     * @throws NullPointerException if {@code unit} is null
     */
    public float getFeelsLikeTemperature(@NonNull String unit) throws NullPointerException {
        //noinspection ConstantConditions
        if (unit == null)
            throw new NullPointerException("unit should not be null");

        float result;
        if (feelsLikeTemperature == Float.MIN_VALUE)
            result = feelsLikeTemperature;
        else
            result = UnitConvertor.convertTemperature(feelsLikeTemperature, unit);
        return result;
    }

    /**
     * Returns <b>rounded</b> "feels like" temperature in specified [unit].
     * <br/>
     * Default value for invalid data: {@link Integer#MIN_VALUE}.
     * @param unit resulted unit
     * @return rounded "feels like" temperature in specified unit
     * @throws NullPointerException if {@code unit} is null
     */
    public int getRoundedFeelsLikeTemperature(@NonNull String unit) throws NullPointerException {
        //noinspection ConstantConditions
        if (unit == null)
            throw new NullPointerException("unit should not be null");

        int result;
        if (feelsLikeTemperature == Float.MIN_VALUE)
            result = Integer.MIN_VALUE;
        else {
            float convertedTemperature = UnitConvertor.convertTemperature(feelsLikeTemperature, unit);
            result = (int) Math.round(convertedTemperature);
        }
        return result;
    }

    /**
     * Returns temperature in specified [unit].
     * <br/>
     * Default value for invalid data: {@link Float#MIN_VALUE}.
     * @param unit resulted unit
     * @return temperature in specified unit
     * @throws NullPointerException if {@code unit} is null
     */
    // TODO rewrite units as enum
    public float getTemperature(@NonNull String unit) throws NullPointerException {
        //noinspection ConstantConditions
        if (unit == null)
            throw new NullPointerException("unit should not be null");

        float result;
        if (temperature == Float.MIN_VALUE)
            result = temperature;
        else
            result = UnitConvertor.convertTemperature(temperature, unit);
        return result;
    }

    /**
     * Returns <b>rounded</b> temperature in specified [unit].
     * <br/>
     * Default value for invalid data: {@link Integer#MIN_VALUE}.
     * @param unit resulted unit
     * @return rounded temperature in specified unit
     * @throws NullPointerException if {@code unit} is null
     */
    // TODO rewrite units as enum
    public int getRoundedTemperature(@NonNull String unit) throws NullPointerException {
        //noinspection ConstantConditions
        if (unit == null)
            throw new NullPointerException("unit should not be null");

        int result;
        if (temperature == Float.MIN_VALUE)
            result = Integer.MIN_VALUE;
        else {
            float convertedTemperature = UnitConvertor.convertTemperature(temperature, unit);
            result = (int) Math.round(convertedTemperature);
        }
        return result;
    }

    /**
     * Returns pressure in default unit (hPa/mBar).
     * <br/>
     * Default value for invalid data: {@link Double#MIN_VALUE}.
     * @return pressure in hPa/mBar
     * @see #getPressure(String)
     */
    public double getPressure() {
        return pressure;
    }

    /**
     * Returns pressure in specified [unit].
     * <br/>
     * Default value for invalid data: {@link Double#MIN_VALUE}.
     * @param unit resulted unit
     * @return pressure in specified unit
     * @throws NullPointerException if {@code unit} is null
     */
    // TODO rewrite units as enum
    public double getPressure(@NonNull String unit) throws NullPointerException {
        //noinspection ConstantConditions
        if (unit == null)
            throw new NullPointerException("unit should not be null");

        double result;
        if (pressure == Double.MIN_VALUE)
            result = pressure;
        else
            result = UnitConvertor.convertPressure(pressure, unit);
        return result;
    }

    /**
     * Returns humidity in per cents.
     * <br/>
     * Default value for invalid data: -1.
     * @return humidity in per cents
     */
    public int getHumidity() {
        return humidity;
    }

    public double getRain() {
        return rain;
    }

    public double getChanceOfPrecipitation() {
        return chanceOfPrecipitation;
    }

    /**
     * Returns wind speed in meter/sec.
     * <br/>
     * Default value for invalid data: {@link Double#MIN_VALUE}.
     * @return wind speed in meter/sec
     * @see #getWindSpeed(String)
     */
    public double getWindSpeed() {
        return windSpeed;
    }

    /**
     * Returns wind speed in specified {@code unit}.
     * <br/>
     * Default value for invalid data: {@link Double#MIN_VALUE}.
     * @param unit resulted unit
     * @return wind speed in specified unit
     * @throws NullPointerException if {@code unit} is null
     */
    public double getWindSpeed(@NonNull String unit) throws NullPointerException {
        //noinspection ConstantConditions
        if (unit == null)
            throw new NullPointerException("unit should not be null");

        double result;
        if (windSpeed == Double.MIN_VALUE)
            result = windSpeed;
        else
            result = UnitConvertor.convertWind(windSpeed, unit);
        return result;
    }

    /**
     * Returns wind direction.
     * <br/>
     * Default value for invalid data: {@code null}.
     * @return wind direction
     * @see Weather.WindDirection
     */
    @Nullable
    public Weather.WindDirection getWindDirection() {
        return windDirection;
    }

    /**
     * Returns wind direction scaled by specified maximum possible directions.
     * <br/>
     * Default value for invalid data: {@code null}.
     * @param maxDirections maximum possible directions
     * @return wind direction scaled by {@code maxDirections}
     * @see NumberOfWindDirections
     */
    @Nullable
    public Weather.WindDirection getWindDirection(@NumberOfWindDirections int maxDirections) {
        Weather.WindDirection result;
        if (windDirection == null)
            result = null;
        else {
            int diff = Weather.WindDirection.values().length / maxDirections - 1;
            result = Weather.WindDirection.values()[windDirection.ordinal() - diff];
        }
        return result;
    }

    /**
     * Returns sunrise time as UNIX timestamp.
     * <br/>
     * Default value for invalid data: -1.
     * @return sunrise time as UNIX timestamp
     */
    public long getSunrise() {
        return sunrise;
    }

    /**
     * Returns sunset time as UNIX timestamp.
     * <br/>
     * Default value for invalid data: -1.
     * @return sunset time as UNIX timestamp
     */
    public long getSunset() {
        return sunset;
    }

    /**
     * Returns city name.
     * <br/>
     * Default value for invalid data: empty string.
     * @return city name
     */
    @NonNull
    public String getCity() {
        return city;
    }

    /**
     * Returns country code.
     * <br/>
     * Default value for invalid data: empty string.
     * @return country code
     */
    @NonNull
    public String getCountry() {
        return country;
    }

    /**
     * Returns weather description.
     * <br/>
     * Default value for invalid data: empty string.
     * @return weather description.
     */
    @NonNull
    public String getDescription() {
        return description;
    }

    /**
     * Returns weather id for formatting weather icon.
     * <br/>
     * Default value for invalid data: -1.
     * @return weather id
     * @see Formatting#getWeatherIcon(int, boolean)
     */
    public int getWeatherIcon() {
        return weatherIcon;
    }

    /**
     * Returns time when this data has been created as timestamp in milliseconds.
     * <br/>
     * Default value for invalid data: -1.
     * @return data creation timestamp in milliseconds
     */
    public long getLastUpdate() {
        return lastUpdate;
    }

    @SuppressWarnings("SameParameterValue")
    private static float getFloat(@NonNull String key, float def, @Nullable JSONObject jsonObject) {
        float result;
        if (jsonObject != null && jsonObject.has(key)) {
            try {
                result = (float) jsonObject.getDouble(key);
            } catch (JSONException e) {
                e.printStackTrace();
                result = def;
            }
        } else {
            result = def;
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static double getDouble(@NonNull String key,
                                    double def,
                                    @Nullable JSONObject jsonObject
    ) {
        double result;
        if (jsonObject != null && jsonObject.has(key)) {
            try {
                result = jsonObject.getDouble(key);
            } catch (JSONException e) {
                e.printStackTrace();
                result = def;
            }
        } else {
            result = def;
        }
        return result;
    }

    private static int getInt(@NonNull String key, int def, @Nullable JSONObject jsonObject) {
        int result;
        if (jsonObject != null && jsonObject.has(key)) {
            try {
                result = jsonObject.getInt(key);
            } catch (JSONException e) {
                result = def;
            }
        } else {
            result = def;
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static long getTimestamp(String key, long def, @Nullable JSONObject jsonObject) {
        long result;
        if (jsonObject != null && jsonObject.has(key)) {
            try {
                result = jsonObject.getLong(key);
                if (result < 0)
                    result = def;
            } catch (JSONException e) {
                e.printStackTrace();
                result = def;
            }
        } else {
            result = def;
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImmutableWeather that = (ImmutableWeather) o;

        if (Float.compare(that.temperature, temperature) != 0) return false;
        if (Float.compare(that.feelsLikeTemperature, feelsLikeTemperature) != 0) return false;
        if (Double.compare(that.pressure, pressure) != 0) return false;
        if (humidity != that.humidity) return false;
        if (Double.compare(that.windSpeed, windSpeed) != 0) return false;
        if (Double.compare(that.rain, rain) != 0) return false;
        if (Double.compare(that.chanceOfPrecipitation, chanceOfPrecipitation) != 0) return false;
        if (sunrise != that.sunrise) return false;
        if (sunset != that.sunset) return false;
        if (weatherIcon != that.weatherIcon) return false;
        if (lastUpdate != that.lastUpdate) return false;
        if (windDirection != that.windDirection) return false;
        if (!city.equals(that.city)) return false;
        if (!country.equals(that.country)) return false;
        return description.equals(that.description);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (temperature != +0.0f ? Float.floatToIntBits(temperature) : 0);
        result = 31 * result + (feelsLikeTemperature != +0.0f ? Float.floatToIntBits(feelsLikeTemperature) : 0);
        temp = Double.doubleToLongBits(pressure);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + humidity;
        temp = Double.doubleToLongBits(windSpeed);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(rain);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(chanceOfPrecipitation);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (windDirection != null ? windDirection.hashCode() : 0);
        result = 31 * result + (int) (sunrise ^ (sunrise >>> 32));
        result = 31 * result + (int) (sunset ^ (sunset >>> 32));
        result = 31 * result + city.hashCode();
        result = 31 * result + country.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + weatherIcon;
        result = 31 * result + (int) (lastUpdate ^ (lastUpdate >>> 32));
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "ImmutableWeather{" +
                "temperature=" + temperature +
                ", feelsLikeTemperature=" + feelsLikeTemperature +
                ", pressure=" + pressure +
                ", humidity=" + humidity +
                ", windSpeed=" + windSpeed +
                ", windDirection=" + windDirection +
                ", sunrise=" + sunrise +
                ", sunset=" + sunset +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                ", description='" + description + '\'' +
                ", weatherIcon=" + weatherIcon +
                ", lastUpdate=" + lastUpdate +
                '}';
    }

    // Parcelable implementation

    protected ImmutableWeather(Parcel in) {
        temperature = in.readFloat();
        feelsLikeTemperature = in.readFloat();
        pressure = in.readDouble();
        humidity = in.readInt();
        windSpeed = in.readDouble();
        rain = in.readDouble();
        chanceOfPrecipitation = in.readDouble();
        int direction = in.readInt();
        if (direction < 0 || direction >= Weather.WindDirection.values().length)
            windDirection = null;
        else
            windDirection = Weather.WindDirection.values()[direction];
        sunrise = in.readLong();
        sunset = in.readLong();
        city = in.readString();
        country = in.readString();
        description = in.readString();
        weatherIcon = in.readInt();
        lastUpdate = in.readLong();
    }

    public static final Creator<ImmutableWeather> CREATOR = new Creator<ImmutableWeather>() {
        @Override
        public ImmutableWeather createFromParcel(Parcel in) {
            return new ImmutableWeather(in);
        }

        @Override
        public ImmutableWeather[] newArray(int size) {
            return new ImmutableWeather[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(temperature);
        dest.writeFloat(feelsLikeTemperature);
        dest.writeDouble(pressure);
        dest.writeInt(humidity);
        dest.writeDouble(windSpeed);
        dest.writeDouble(rain);
        dest.writeDouble(chanceOfPrecipitation);
        if (windDirection == null)
            dest.writeInt(Integer.MIN_VALUE);
        else
            dest.writeInt(windDirection.ordinal());
        dest.writeLong(sunrise);
        dest.writeLong(sunset);
        dest.writeString(city);
        dest.writeString(country);
        dest.writeString(description);
        dest.writeInt(weatherIcon);
        dest.writeLong(lastUpdate);
    }
}