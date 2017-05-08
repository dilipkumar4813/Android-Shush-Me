package com.example.android.shushme;

/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.example.android.shushme.provider.PlaceContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    // Constants
    public static final String TAG = MainActivity.class.getSimpleName();

    // Member variables
    private PlaceListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private GoogleApiClient mGoogleApiClient;
    private boolean mIsEnabled;
    private Geofencing mGeofencing;

    /**
     * Called when the activity is starting
     *
     * @param savedInstanceState The Bundle that contains the data supplied in onSaveInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.places_list_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PlaceListAdapter(this, null);
        mRecyclerView.setAdapter(mAdapter);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .enableAutoManage(this, this)
                .build();

        findViewById(R.id.btn_add_location).setOnClickListener(this);

        mGeofencing = new Geofencing(this, mGoogleApiClient);

        Switch geofencingSwitch = (Switch) findViewById(R.id.enable_switch);
        mIsEnabled = getPreferences(MODE_PRIVATE).getBoolean("GEO", false);
        geofencingSwitch.setChecked(mIsEnabled);
        geofencingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                editor.putBoolean("GEO", isChecked);
                mIsEnabled = isChecked;
                if (isChecked) {
                    mGeofencing.registerAllGeofences();
                } else {
                    mGeofencing.unRegisterAllGeofences();
                }
            }
        });

    }

    private void onAddPlaceButtonClicked() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You have not enabled location permission", Toast.LENGTH_SHORT).show();
            return;
        }

        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
        Intent placeIntent = null;
        try {
            placeIntent = builder.build(this);
        } catch (GooglePlayServicesRepairableException e) {
            e.printStackTrace();
        } catch (GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        }
        startActivityForResult(placeIntent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Place place = PlacePicker.getPlace(this, data);
            if (place == null) {
                Toast.makeText(this, "No place selected", Toast.LENGTH_SHORT).show();
                return;
            }

            String placeName = place.getName().toString();
            String placeAddress = place.getAddress().toString();
            String placeId = place.getId();

            ContentValues cv = new ContentValues();
            cv.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ID, placeId);
            getContentResolver().insert(PlaceContract.PlaceEntry.CONTENT_URI, cv);
            refreshPlacesData();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "Connected");
        refreshPlacesData();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed");
    }

    public void refreshPlacesData() {
        Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
        Cursor data = getContentResolver().query(uri
                , null
                , null
                , null
                , null);

        if (data == null || data.getCount() == 0) return;
        List<String> guids = new ArrayList<>();
        while (data.moveToNext()) {
            guids.add(data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_ID)));
        }

        PendingResult<PlaceBuffer> placesResult = Places.GeoDataApi.getPlaceById(mGoogleApiClient,
                guids.toArray(new String[guids.size()]));
        placesResult.setResultCallback(new ResultCallback<PlaceBuffer>() {
            @Override
            public void onResult(@NonNull PlaceBuffer places) {
                mAdapter.swapPlaces(places);

                if (mIsEnabled) {
                    mGeofencing.updateGeofenceList(places);
                    mGeofencing.registerAllGeofences();
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_add_location:
                onAddPlaceButtonClicked();
                break;
        }
    }
}