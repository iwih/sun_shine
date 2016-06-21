package com.wordpress.iwih.sunshine;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import com.wordpress.iwih.sunshine.data.WeatherContract.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by iwih on 02/06/2016.
 */
public class FetchWeatherAsync extends AsyncTask<String, Void, Void> {

    Logger log = new Logger(this.getClass().getSimpleName());
    private Context mContext;

    private String cityName;
    private String dataMode;
    private String dataUnits;
    private String daysCount;

    public FetchWeatherAsync(Context context) {
        mContext = context;
    }

    @Override
    protected Void doInBackground(String... params) {

        log.v("Checking network availability");

        log.i(params.length + "x object(s) passed to FetchWeatherAsync worker.");

        cityName = params[0];
        dataMode = params[1];
        dataUnits = params[2];
        daysCount = params[3];

        log.v("Data passed to internal variables successfully!");

        fetchWeatherJsonData();

        return null;
    }

    private String forecastJsonStr;

    public void fetchWeatherJsonData() {

        HttpURLConnection urlConnection = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        try {
            String weatherFetchingUrl = buildWeatherFetchingUrl();

            URL url = new URL(weatherFetchingUrl);

            log.v("URL: \"" + weatherFetchingUrl + "\"");

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            log.v("Connection opened..");

            //read url stream into String
            forecastJsonStr = getForecastJsonStringFromUrlConnection(urlConnection);

        } catch (IOException e) {
            log.e("Error :" + e);
            // If the code didn't successfully get the weather data, there's no point in attemping
            // to parse it.
            forecastJsonStr = null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        this.forecastJsonStr = forecastJsonStr;
    }

    private String getForecastJsonStringFromUrlConnection
            (HttpURLConnection urlConnection) {

        StringBuilder jsonStrBuffer = new StringBuilder();
        BufferedReader buffReader = null;

        try {
            InputStream inputStream = urlConnection.getInputStream();
            if (inputStream == null) return null;

            buffReader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = buffReader.readLine()) != null) {
                jsonStrBuffer.append(line).append("\n");
            }
        } catch (IOException e) {
            log.e(e.getMessage());

            //try closing non-null buffReader
            if (buffReader != null)
                try {
                    buffReader.close();
                } catch (IOException e1) {
                    log.e("Error closing buffered reader: " + e1.getMessage());
                }
        }

        return jsonStrBuffer.toString();
    }

    private String buildWeatherFetchingUrl() {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("http");
        uriBuilder.authority("api.openweathermap.org");

        uriBuilder
                .appendPath("data")
                .appendPath("2.5")
                .appendPath("forecast")
                .appendPath("daily");
        uriBuilder
                .appendQueryParameter("q", cityName)
                .appendQueryParameter("mode", dataMode)
                .appendQueryParameter("units", dataUnits)
                .appendQueryParameter("cnt", daysCount)
                .appendQueryParameter("appid", mContext.getString(R.string.owm_appid));

        return uriBuilder.build().toString();
    }

    @Override
    protected void onPostExecute(Void aVoid) {

        try {
            log.v("Start parsing weather data...");
            parseForecastJsonStr();
        } catch (Exception e) {
            log.e("Couldn't parse Weather JSON Data " + e.getMessage());
        }

        if (forecastJsonStr.equals("") || (forecastJsonStr == null))
            return;
        log.v("Start populating weather data to UI...");

        //populateWeatherArrayToUI();
    }

    private void parseForecastJsonStr() {

        if (forecastJsonStr.equals(null) || forecastJsonStr == "") {
            log.i("Empty or null forecastJsonStr. Nothing to parse..");
            return;
        }

        try {
            JSONObject mainJson = new JSONObject(forecastJsonStr);

            parseCityAndCountryName(mainJson.getJSONObject("city"));

            parseWeatherData(mainJson.getJSONArray("list"));
        } catch (JSONException e) {
            log.e("Couldn't parse forecastJsonStr or/and display its content: " + e.getMessage());
        }

    }

    private String activityTitle;

    private void parseCityAndCountryName(JSONObject cityJsonObj) {
        try {
            String cityName = cityJsonObj.getString("name");
            String cityId = cityJsonObj.getString("id");
            String countryName = cityJsonObj.getString("country");

            JSONObject coordModule = cityJsonObj.getJSONObject("coord");
            String coord_lat = coordModule.getString("lat");
            String coord_long = coordModule.getString("lon");

            String activityTitle = cityName + ", " + countryName;
            log.i("City name title: " + activityTitle);

            long id = addLocation(cityId, cityName, coord_long, coord_lat);
        } catch (JSONException e) {
            log.e("Couldn't parse city/country name(s): " + e.getMessage());
        }
    }

    private ArrayList<String> forecastAListStr;

    private void parseWeatherData(JSONArray weatherDataJsonArray) {

        ArrayList<String> forecastAListStr = new ArrayList<>();
        try {
            for (int i = 0; i < weatherDataJsonArray.length(); i++) {

                Date weatherDate = getWeatherDayDate(weatherDataJsonArray.getJSONObject(i));

                JSONObject dayTempJsonObj = weatherDataJsonArray.getJSONObject(i).getJSONObject("temp");
                Long maxTemp = getDayMaxTemp(dayTempJsonObj);
                Long minTemp = getDayMinTemp(dayTempJsonObj);
                Long dayTemp = getDayTemp(dayTempJsonObj);

                DateFormat dateFormat = new SimpleDateFormat(mContext.getString(R.string.date_format_main));
                String dayForecastRow = dateFormat.format(weatherDate) + " \n" +
                        "Day: " + dayTemp + ", " +
                        "Max: " + maxTemp + ", " +
                        "Min: " + minTemp;

                log.i("Finished parsing day weather: " + dayForecastRow);

                forecastAListStr.add(dayForecastRow);

            }

        } catch (JSONException e) {
            log.e("Couldn't parse weather data: " + e.getMessage());
        }

        this.forecastAListStr = forecastAListStr;
    }

    private Date getWeatherDayDate(JSONObject dayWeatherObj) {
        Date weatherDate = null;
        try {

            weatherDate = new Date(dayWeatherObj.getLong("dt") * 1000);

        } catch (JSONException e) {
            log.e("Couldn't parse weather date: " + e.getMessage());
        }

        return weatherDate;
    }

    private Long getDayMaxTemp(JSONObject dayTempJsonObj) {
        Long dayMaxTemp = null;
        try {
            dayMaxTemp = dayTempJsonObj.getLong("max");
        } catch (JSONException e) {
            log.e("Couldn't parse maximum temp of the day: " + e.getMessage());
        }

        return dayMaxTemp;
    }

    private Long getDayMinTemp(JSONObject dayTempJsonObj) {
        Long dayMinTemp = null;
        try {
            dayMinTemp = dayTempJsonObj.getLong("min");
        } catch (JSONException e) {
            log.e("Couldn't parse minimum temp of the day: " + e.getMessage());
        }

        return dayMinTemp;
    }

    private Long getDayTemp(JSONObject dayTempJsonObj) {
        Long dayTemp = null;
        try {
            dayTemp = dayTempJsonObj.getLong("day");
        } catch (JSONException e) {
            log.e("Couldn't parse mid-day temp of the day: " + e.getMessage());
        }

        return dayTemp;
    }

    private long addLocation(String locationSetting, String cityName, String longitude, String latitude) {
        //checking for previously recorded location
        long previousLocationId = getLocationId(locationSetting);
        if (previousLocationId == -1)
            return previousLocationId;

        ContentValues valuesLocation = new ContentValues();
        valuesLocation.put(LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
        valuesLocation.put(LocationEntry.COLUMN_CITY_NAME, cityName);
        valuesLocation.put(LocationEntry.COLUMN_COORD_LONG, longitude);
        valuesLocation.put(LocationEntry.COLUMN_COORD_LAT, latitude);

        Uri uriInsertedRow = mContext.getContentResolver().insert(LocationEntry.CONTENT_URI, valuesLocation);
        return (ContentUris.parseId(uriInsertedRow));
    }

    private long getLocationId(String locationSetting) {
        long _id = -1;

        Cursor cursorLocation = mContext.getContentResolver()
                .query(LocationEntry.CONTENT_URI,
                        new String[]{LocationEntry._ID},
                        LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                        new String[]{locationSetting},
                        null);

        if (cursorLocation != null) {
            if (cursorLocation.moveToNext()) {
                int indexColumn = cursorLocation.getColumnIndex(LocationEntry._ID);
                _id = cursorLocation.getLong(indexColumn);
            }
            cursorLocation.close();
        }

        return _id;
    }

    /*private void populateWeatherArrayToUI() {
        forecastArrayAdapter.clear();
        for (String dayWeather : forecastAListStr) {
            forecastArrayAdapter.add(dayWeather);
        }
    }*/
}

