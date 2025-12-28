package cz.martykan.forecastie.tasks;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import cz.martykan.forecastie.Constants;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.utils.Language;
import cz.martykan.forecastie.weatherapi.WeatherStorage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public abstract class GenericRequestTask extends AsyncTask<String, String, TaskOutput> {

    ProgressDialog progressDialog;
    protected Context context;
    protected MainActivity activity;
    protected WeatherStorage weatherStorage;

    
    private OkHttpClient okHttpClient;

    public GenericRequestTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
        this.context = context;
        this.activity = activity;
        this.progressDialog = progressDialog;
        this.weatherStorage = new WeatherStorage(activity);
        this.okHttpClient = new OkHttpClient();
    }

    @Override
    protected void onPreExecute() {
        if (!progressDialog.isShowing()) {
            progressDialog.setMessage(context.getString(R.string.downloading_data));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }
    }

    @Override
    protected TaskOutput doInBackground(String... params) {
        String[] reqParams = new String[]{};

        if (params != null && params.length > 0) {
            final String zeroParam = params[0];
            if ("coords".equals(zeroParam)) {
                String lat = params[1];
                String lon = params[2];
                reqParams = new String[]{"coords", lat, lon};
            } else if ("city".equals(zeroParam)) {
                reqParams = new String[]{"city", params[1]};
            }
        }

        TaskOutput requestOutput = makeRequest(reqParams);

        if (TaskResult.SUCCESS.equals(requestOutput .taskResult)) {
            requestOutput.parseResult = parseResponse(requestOutput.response);
        }

        return requestOutput ;
    }

    private TaskOutput makeRequest(String[] reqParams) {
        TaskOutput output = new TaskOutput();
        try {
            URL url = provideURL(reqParams);
            Log.i("URL", url.toString());
            
            Request request = new Request.Builder()
                    .url(url.toString())
                    .build();
            
            Response responseObj = okHttpClient.newCall(request).execute();
            if (responseObj.isSuccessful()) {
                String responseBody = responseObj.body().string();
                output.response = responseBody;
                // Background work finished successfully
                Log.i("Task", "done successfully");
                output.taskResult = TaskResult.SUCCESS;
                // Save date/time for latest successful result
                MainActivity.saveLastUpdateTime(PreferenceManager.getDefaultSharedPreferences(context));
            } else if (responseObj.code() == 401) {
                // Invalid API key
                Log.w("Task", "invalid API key");
                output.taskResult = TaskResult.INVALID_API_KEY;
            } else if (responseObj.code() == 429) {
                // Too many requests
                Log.w("Task", "too many requests");
                output.taskResult = TaskResult.TOO_MANY_REQUESTS;
            } else {
                // Bad response from server
                Log.w("Task", "http error " + responseObj.code());
                output.taskResult = TaskResult.HTTP_ERROR;
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Exception while reading data from url connection
            output.taskResult = TaskResult.IO_EXCEPTION;
            output.taskError = e;
        }
        
        return output;
    }

    @Override
    protected void onPostExecute(TaskOutput output) {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        updateMainUI();

        handleTaskOutput(output);
    }

    protected final void handleTaskOutput(TaskOutput output) {
        switch (output.taskResult) {
            case SUCCESS:
                ParseResult parseResult = output.parseResult;
                if (ParseResult.CITY_NOT_FOUND.equals(parseResult)) {
                    Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_city_not_found), Snackbar.LENGTH_LONG).show();
                } else if (ParseResult.JSON_EXCEPTION.equals(parseResult)) {
                    Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_err_parsing_json), Snackbar.LENGTH_LONG).show();
                }
                break;
            case TOO_MANY_REQUESTS:
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_too_many_requests), Snackbar.LENGTH_LONG).show();
                break;
            case INVALID_API_KEY:
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_invalid_api_key), Snackbar.LENGTH_LONG).show();
                break;
            case HTTP_ERROR:
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_http_error), Snackbar.LENGTH_LONG).show();
                break;
            case IO_EXCEPTION:
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    private URL provideURL(String[] reqParams) throws MalformedURLException {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        Uri.Builder uriBuilder = new Uri.Builder().scheme("https");

        String apiName = getAPIName();
        if ("find".equals(apiName)) {
            uriBuilder.authority("geocoding-api.open-meteo.com")
                    .path("/v1/search");
            if (reqParams.length > 0 && "city".equals(reqParams[0])) {
                uriBuilder.appendQueryParameter("name", reqParams[1]);
            }
            uriBuilder.appendQueryParameter("count", "10");
            uriBuilder.appendQueryParameter("language", Language.getLanguageCode());
            uriBuilder.appendQueryParameter("format", "json");
        } else {
            uriBuilder.authority("api.open-meteo.com")
                    .path("/v1/forecast");

            String lat, lon;
            if (reqParams.length > 0 && "coords".equals(reqParams[0])) {
                lat = reqParams[1];
                lon = reqParams[2];
            } else {
                lat = Double.toString(weatherStorage.getLatitude(Constants.DEFAULT_LAT));
                lon = Double.toString(weatherStorage.getLongitude(Constants.DEFAULT_LON));
            }

            uriBuilder.appendQueryParameter("latitude", lat);
            uriBuilder.appendQueryParameter("longitude", lon);
            uriBuilder.appendQueryParameter("current_weather", "true");
            uriBuilder.appendQueryParameter("hourly", "temperature_2m,relativehumidity_2m,weathercode,pressure_msl,windspeed_10m,winddirection_10m,rain,precipitation_probability");
            uriBuilder.appendQueryParameter("daily", "sunrise,sunset");
                uriBuilder.appendQueryParameter("timezone", "auto");
                uriBuilder.appendQueryParameter("timeformat", "unixtime");
        }

        return new URL(uriBuilder.build().toString());
    }

    protected void updateMainUI() {
    }

    protected abstract ParseResult parseResponse(String response);

    protected abstract String getAPIName();
}
