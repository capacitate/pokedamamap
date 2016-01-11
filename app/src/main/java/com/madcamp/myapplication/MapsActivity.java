package com.madcamp.myapplication;

import android.graphics.Camera;
import android.location.Location;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LatLng current;

    private Socket mSocket;
    {
        try{
            mSocket = IO.socket(Constants.CHAT_SERVER_URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    private static TelephonyManager tm;
    private String TAG = "Unity MapsActivity";
    private GetWildAsyncTask wildPoke;

    private Emitter.Listener onMsg = new Emitter.Listener(){

        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            String retrieved = null;

            Log.i(TAG, "Unity get message" + data.toString());
        }
    };

    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener() {
        return new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                current = new LatLng(location.getLatitude(), location.getLongitude());
                double longitude = location.getLongitude();
                double latitude = location.getLatitude();

                //set the current location
//                Marker marker = mMap.addMarker(new MarkerOptions().position(loc));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 16.0f));

                //get wild poke and show on the map boolean value 
                wildPoke = new GetWildAsyncTask();
                wildPoke.execute();
                Log.i(TAG, "OnMyLocationChange Listener longitude : " + longitude + " latitue : " + latitude);
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();

        //get the IMEI number
        tm = (TelephonyManager) getSystemService(getApplicationContext().TELEPHONY_SERVICE);
        Log.i(TAG, "IMEI number is : " + tm.getDeviceId());

        // enable current location button
        mMap.setMyLocationEnabled(true);

        //set "listener" for changing my location
        mMap.setOnMyLocationChangeListener(myLocationChangeListener());


    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    public class GetWildAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute(){
            JSONObject msg = new JSONObject();
            try {
                msg.put("IMEI", tm.getDeviceId());
                msg.put("latitude", current.latitude);
                msg.put("longitude", current.longitude);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mSocket.connect();
            if(mSocket.connected()){
                Log.w(TAG, "socket is connected");
            }

            Log.w(TAG, "msocket start");
            mSocket.on("new message", onMsg);
            Log.w(TAG, "msocket start 받은 거니?");
            mSocket.emit("MapActivity", msg);
            Log.w(TAG, "msocket start 보내고 있는 거니?");
        }

        @Override
        protected Void doInBackground(Void... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Void params){

        }
    }
}
