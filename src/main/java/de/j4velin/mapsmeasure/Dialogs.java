/*
 * Copyright 2014 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.mapsmeasure;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Stack;

abstract class Dialogs {

    /**
     * @param m        the Map
     * @param distance the current distance
     * @param area     the current area
     * @return the units dialog
     */
    public static Dialog getUnits(final Map m, float distance, double area) {
        final Dialog d = new Dialog(m);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_unit);
        CheckBox metricCb = d.findViewById(R.id.metric);
        metricCb.setChecked(Map.metric);
        metricCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Map.metric = !Map.metric;
            m.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putBoolean("metric", isChecked).apply();
            m.updateValueText();
        });
        ((TextView) d.findViewById(R.id.distance)).setText(
                Map.formatter_two_dec.format(Math.max(0, distance)) + " m\n" +
                        Map.formatter_two_dec.format(distance / 1000) + " km\n\n" +
                        Map.formatter_two_dec.format(Math.max(0, distance / 0.3048f)) + " ft\n" +
                        Map.formatter_two_dec.format(Math.max(0, distance / 0.9144)) + " yd\n" +
                        Map.formatter_two_dec.format(distance / 1609.344f) + " mi\n" +
                        Map.formatter_two_dec.format(distance / 1852f) + " nautical miles");

        ((TextView) d.findViewById(R.id.area)).setText(
                Map.formatter_two_dec.format(Math.max(0, area)) + " m²\n" +
                        Map.formatter_two_dec.format(area / 10000) + " ha\n" +
                        Map.formatter_two_dec.format(area / 1000000) + " km²\n\n" +
                        Map.formatter_two_dec.format(Math.max(0, area / 0.09290304d)) + " ft²\n" +
                        Map.formatter_two_dec.format(area / 4046.8726099d) + " ac (U.S. Survey)\n" +
                        Map.formatter_two_dec.format(area / 2589988.110336d) + " mi²");
        d.findViewById(R.id.close).setOnClickListener(v -> d.dismiss());
        return d;
    }

    /**
     * @param c the Context
     * @return a dialog informing the user about an issue with getting altitude
     * data from the Google API
     */
    public static Dialog getElevationErrorDialog(final Context c) {
        return getShowErrorDialog(c, c.getString(Util.checkInternetConnection(c) ? R.string.elevation_error :
                R.string.elevation_error_no_connection));
    }

    public static Dialog getShowErrorDialog(final Context c, final String msg) {
        if (BuildConfig.DEBUG) Logger.log("showing error: " + msg);
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setMessage(msg);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
        return builder.create();
    }

    /**
     * Shows the dialog to unlock the elevation feature
     *
     * @param c               the map activity
     * @param purchaseHandler a lambda which is called when the user wants to start the purchase flow
     */
    public static void showElevationAccessDialog(final Map c, final Runnable purchaseHandler) {
        final ProgressDialog pg = new ProgressDialog(c);
        pg.setMessage("Loading...");
        pg.show();
        final Handler h = new Handler();
        isGoogleAvailable(available -> h.post(() -> {
            pg.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(c);
            if (!available) {
                builder.setMessage(R.string.no_google_connection);
            } else {
                builder.setMessage(R.string.buy_pro);
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> dialog.dismiss());
            }
            try {
                builder.create().show();
            } catch (Exception e) {
                Toast.makeText(c, R.string.no_google_connection, Toast.LENGTH_SHORT)
                        .show();
            }
        }));
    }

    /**
     * Workaround if the edittext search doesnt work
     *
     * @param map the map activity
     * @return a search dialog
     */
    public static Dialog getSearchDialog(final Map map) {
        AlertDialog.Builder builder = new AlertDialog.Builder(map);
        final EditText search = new EditText(map);
        search.setHint(android.R.string.search_go);
        builder.setView(search);
        builder.setPositiveButton(android.R.string.search_go,
                (dialog, which) -> {
                    new GeocoderTask(map).execute(search.getText().toString());
                    // hide softinput keyboard
                    InputMethodManager inputManager = (InputMethodManager) map
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(search.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
                    dialog.dismiss();
                });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            // hide softinput keyboard
            InputMethodManager inputManager =
                    (InputMethodManager) map.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(search.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
            dialog.dismiss();
        });
        return builder.create();
    }

    private static void isGoogleAvailable(final Callback callback) {
        new Thread(() -> {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("https://maps.googleapis.com");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(5000);
                urlConnection.connect();
                callback.result(true);
            } catch (IOException e) {
                e.printStackTrace();
                if (BuildConfig.DEBUG) Logger.log(e);
                callback.result(false);
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }
        }).start();
    }

    private interface Callback {
        void result(boolean available);
    }
}
