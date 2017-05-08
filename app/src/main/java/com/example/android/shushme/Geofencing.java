package com.example.android.shushme;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dilipkumar4813 on 08/05/17.
 */

public class Geofencing implements ResultCallback {
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    private PendingIntent mGeofencePendingIntent;
    private List<Geofence> mGeofenceList;
    private static final long GEOFENCE_TIMEOUT = 24l;
    private static final int GEOFENCE_RADIUS = 50;

    private static final String TAG = Geofencing.class.getSimpleName();

    public Geofencing(Context context, GoogleApiClient googleApiClient) {
        this.mContext = context;
        this.mGoogleApiClient = googleApiClient;
        mGeofencePendingIntent = null;
        mGeofenceList = new ArrayList<>();
    }

    public void updateGeofenceList(PlaceBuffer places) {
        mGeofenceList = new ArrayList<>();
        if (places == null || places.getCount() == 0) return;

        for (Place place : places) {
            String placeUId = place.getId();
            double latitude = place.getLatLng().latitude;
            double longitude = place.getLatLng().longitude;

            Geofence geofence = new Geofence.Builder()
                    .setRequestId(placeUId)
                    .setExpirationDuration(GEOFENCE_TIMEOUT)
                    .setCircularRegion(latitude, longitude, GEOFENCE_RADIUS)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();

            mGeofenceList.add(geofence);
        }
    }

    public void registerAllGeofences(){
        if(mGoogleApiClient==null || !mGoogleApiClient.isConnected()
                ||mGeofenceList==null||mGeofenceList.size()==0){
            return;
        }

        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected() ||
                mGeofenceList == null || mGeofenceList.size() == 0) {
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(this);
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }

    public void unRegisterAllGeofences(){
        if(mGoogleApiClient==null || !mGoogleApiClient.isConnected()){
            return;
        }

        try{
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    getGeofencePendingIntent())
                    .setResultCallback(this);
        }catch (SecurityException securityException){
            Log.e(TAG,securityException.getLocalizedMessage().toString());
        }
    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(mContext, GeofenceBroadcastReceiver.class);
        mGeofencePendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }

    @Override
    public void onResult(@NonNull Result result) {
        Log.e(TAG, String.format("Error adding/removing geofence : %s",
                result.getStatus().toString()));
    }
}
