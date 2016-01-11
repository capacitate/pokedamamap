package com.madcamp.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import java.util.logging.LogRecord;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LatLng current;
    private Location current_loc;

    //information for wild pokemon
    private LatLng[] wild_loc;
    private int wild_num = 0;

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

    private SendMsgHandler mHandler;

    private final int DRAW_MARKER = 0;
    private final int START_WORK = 1;
    private final int ZOOM_CURRENT = 2;

    private boolean is_first = true;

    private Context mContext;
    private AlertDialog mDialog;

    private final double DISTANCE = 100;

    //get "new message" from server and make LatLng for wild pocketmon
    private Emitter.Listener onMsg = new Emitter.Listener(){

        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            JSONArray wild_arr = null;

            try {
                wild_arr = data.getJSONArray("wild");
                wild_num = data.getInt("wild_num");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            wild_loc = new LatLng[wild_num];

            for(int i = 0; i < wild_num; i++){
                try {
                    wild_loc[i] = new LatLng(wild_arr.getJSONObject(i).getDouble("latitude"), wild_arr.getJSONObject(i).getDouble("longitude"));
                    Log.i(TAG, i + " 번째 : " + wild_arr.getJSONObject(i).toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            mHandler.sendEmptyMessage(DRAW_MARKER);
        }
    };

    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener() {
        return new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                current = new LatLng(location.getLatitude(), location.getLongitude());
                double longitude = location.getLongitude();
                double latitude = location.getLatitude();

                current_loc = new Location("current");
                current_loc.setLongitude(longitude);
                current_loc.setLatitude(latitude);

                mMap.clear();
                mHandler.sendEmptyMessage(DRAW_MARKER);

                if(is_first){
                    mHandler.sendEmptyMessage(START_WORK);
                    mHandler.sendEmptyMessage(ZOOM_CURRENT);
                    is_first = false;
                }
            }
        };
    }

    private AlertDialog createDialog(LatLng marker_loc){
        Location mar_loc = new Location("marker");
        mar_loc.setLatitude(marker_loc.latitude);
        mar_loc.setLongitude(marker_loc.longitude);

        if(current_loc.distanceTo(mar_loc) < DISTANCE){
            AlertDialog.Builder ab = new AlertDialog.Builder(this);
            ab.setTitle("FIGHT");
            ab.setMessage("FIGHT WITH WILD");
            ab.setCancelable(true);

            ab.setPositiveButton("FIGHT", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //might be send something to the server
                    Log.i(TAG, "click FIGHT");
                    mDialog.dismiss();
                }
            });

            ab.setNegativeButton("RUN", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "click RUN");
                    mDialog.dismiss();
                }
            });

            return ab.create();
        } else {
            AlertDialog.Builder ab = new AlertDialog.Builder(this);
            ab.setTitle("FAR");
            ab.setMessage("TOO FAR AWAY to fight");
            ab.setCancelable(true);

            ab.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //might be send something to the server
                    Log.i(TAG, "click FIGHT");
                    mDialog.dismiss();
                }
            });

            return ab.create();
        }
    }

    private GoogleMap.OnMarkerClickListener clickDialog(){
        return new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                mDialog = createDialog(marker.getPosition());
                mDialog.show();
                return false;
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;//return true so that the menu pop up is opened
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "item.getItemId() : " + item.getItemId());
        switch(item.getItemId()){
            case 2131558548:
                mHandler.sendEmptyMessage(START_WORK);
                break;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();

        mContext = getApplicationContext();

        //get the IMEI number
        tm = (TelephonyManager) getSystemService(getApplicationContext().TELEPHONY_SERVICE);
        Log.i(TAG, "IMEI number is : " + tm.getDeviceId());

        // enable current location button
        mMap.setMyLocationEnabled(true);

        //set "listener" for changing my location
        mMap.setOnMyLocationChangeListener(myLocationChangeListener());

        //start asynctask after 3 seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                //get wild poke and show on the map boolean value

            }
        }, 5000);

        //handler to manage the UI thread
        mHandler = new SendMsgHandler();

        //set marker click event
        mMap.setOnMarkerClickListener(clickDialog());
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mSocket.off("new message", onMsg);
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

    class SendMsgHandler extends Handler {
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);

            switch(msg.what){
                case DRAW_MARKER:
                    if(null == wild_loc){
                        Log.i(TAG, "wild_loc array is null");
                    }

                    for(int i = 0; i < wild_num; i++){
                        Location temp_loc = new Location("wild");
                        temp_loc.setLatitude(wild_loc[i].latitude);
                        temp_loc.setLongitude(wild_loc[i].longitude);

                        Log.i(TAG, "insdide of for onPostExecute distance[" + i + " : " + current_loc.distanceTo(temp_loc));
                        if (current_loc.distanceTo(temp_loc) < DISTANCE){
                            mMap.addMarker(new MarkerOptions().position(wild_loc[i]).title("gonna fight? wild")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.karate)));
                        }else{
                            mMap.addMarker(new MarkerOptions().position(wild_loc[i]).title("sleeping wild")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.sleep)));
                        }
                    }
                    break;

                case START_WORK:
                    mMap.clear();
                    wildPoke = new GetWildAsyncTask();
                    wildPoke.execute();
                    break;

                case ZOOM_CURRENT:
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 16.0f));
                    break;
            }
        }
    }
}
