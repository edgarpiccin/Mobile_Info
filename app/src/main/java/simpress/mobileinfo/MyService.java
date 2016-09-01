package simpress.mobileinfo;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by edgarp on 30/08/2016.
 */
public class MyService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    public GoogleApiClient mGoogleApiClient;
    public static final String TAG = simpress.mobileinfo.MainActivity.class.getSimpleName();
    private LocationRequest mLocationRequest;
    public String latitudeStr, longitudeStr, imeiString;
    private simpress.mobileinfo.DatabaseHelper helper;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        Worker w = new Worker(startId);
        w.start();

        return(super.onStartCommand(intent, flags, startId));
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        Log.i(TAG, "Location services connected.");

        if (checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (location == null) {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            } else {
                handleNewLocation(location);
            }
        }
    }

    public boolean checkPermission(String permission) {
        int res = this.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    private void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Brazil/East"));
        int hora = c.get(c.HOUR_OF_DAY);
        int diaSemana = c.get(c.DAY_OF_WEEK);
        int minuto = c.get(c.MINUTE);
        int segundo = c.get(c.SECOND);

        latitudeStr = String.valueOf(location.getLatitude());
        longitudeStr = String.valueOf(location.getLongitude());
        getIMEI();

        if (hora > 7 && hora < 23){
            helper = new simpress.mobileinfo.DatabaseHelper(getBaseContext());
            saveLocation();

            if(Conectado(getBaseContext())){
                ArrayList<ImeiLocation> itensImei = getSavedLocation();

                if (itensImei.size() > 0) {
                    String jsonItem = "";
                    for (ImeiLocation item : itensImei) {
                        jsonItem = jsonItem + generateJson(item) + ",";
                    }
                    jsonItem = "[" + jsonItem + "]";
                    new HttpAsyncTask(getResources().getString(R.string.app_token), getResources().getString(R.string.client_id)).execute(getResources().getString(R.string.url_service) + "localizacao/list/", jsonItem, "POST");

                    Log.i("Script", "Enviou dados para a API.");
                }
                dropLocation();
                Log.i("Script", "Conectou");
            } else
                Log.i("Script", "Sem rede");
        }
        Log.i("Script", "IMEI: " + imeiString + " / Latitude: " + latitudeStr + " / Longitude: " + longitudeStr);

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();

            Log.i(TAG, "Desconetando Location: " + location.toString() + " statusApicliente: " + String.valueOf(mGoogleApiClient.isConnected()));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended. Please reconnect.");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private void getIMEI() {
        TelephonyManager tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        /*
        if (tel.getPhoneCount() > 1)
            imeiString = tel.getDeviceId(0);
        else
        */
        imeiString = tel.getDeviceId();
    }

    private String generateJson(ImeiLocation imei) {
        String json = "{" + "\"" + "IMEI" + "\"" + ":" + "\"" + imei._imei + "\"" + "," + "\"" + "Latitude" + "\"" + ":" + "\"" + imei._latitude + "\"" + "," + "\"" + "Longitude" + "\"" + ":" + "\"" + imei._longitude + "\"" + "," + "\"" + "Data_Localizacao" + "\"" + ":" + "\"" + imei._data.toString() + "\"}";
        return json;
    }

    public class ImeiLocation extends ArrayList {
        private int _id;
        private String _imei;
        private String _latitude;
        private String _longitude;
        private String _data;


        public ImeiLocation(int id, String imei, String latitude, String longitude, String data) {
            _id = id;
            _imei = imei;
            _latitude = latitude;
            _longitude = longitude;
            _data = data;
        }
    }

    public static boolean Conectado(Context context) {

        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        String LogSync = null;
        String LogToUserTitle = null;

        NetworkInfo[] a = cm.getAllNetworkInfo();
        NetworkInfo networkInfo;
        boolean x = false;

        for (NetworkInfo myNet : a) {
            //networkInfo = cm.getNetworkInfo(myNet);
            if (myNet.getState().equals(NetworkInfo.State.CONNECTED)) {
                x = true;
                return x;
            } else {
                x = false;
            }
        }
        return x;

    }

    private void saveLocation() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d HH:mm:ss");
        String dataHora = sdf.format(new Date());

        values.put("imei", imeiString);
        values.put("latitude", latitudeStr);
        values.put("longitude", longitudeStr);
        values.put("data", dataHora);//"%Y-%m-%d %H:%M:%S"));

        db.insert("ImeiLocationHistory", null, values);
    }

    private void dropLocation() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("ImeiLocationHistory", "", null);
    }

    private ArrayList<ImeiLocation> getSavedLocation() {
        SQLiteDatabase db1 = helper.getReadableDatabase();

        ArrayList<ImeiLocation> itensImei = new ArrayList<ImeiLocation>();

        Cursor cursor = db1.rawQuery("select _id, imei, latitude, longitude, data from ImeiLocationHistory", null);
        cursor.moveToFirst();

        if (cursor.getCount() >= 1) {

            for (int i = 0; i < cursor.getCount(); i++) {
                ImeiLocation item = new ImeiLocation(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4));

                itensImei.add(item);

                cursor.moveToNext();
            }
        }

        cursor.close();
        return itensImei;
    }

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }

    class Worker extends Thread{
        public int count = 0;
        public int startId;
        public boolean ativo = true;

        public Worker(int startId){
            this.startId = startId;
        }

        public void run(){
            while(true){

                try {
                    mGoogleApiClient.connect();
                    Thread.sleep(120000);

                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                mGoogleApiClient.disconnect();
                count++;
                Log.i("Script", "COUNT: "+count);
            }

            //stopSelf(startId);
        }
    }

    public class HttpAsyncTask extends AsyncTask<String, Void, String> {

        private String _appToken;
        private String _clientID;

        public HttpAsyncTask(String appToken, String clientId) {
            this._appToken = appToken;
            this._clientID = clientId;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            //this method will be running on UI thread

        }

        @Override
        protected String doInBackground(String... params) {

            try {
                URL url = new URL(params[0]);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.addRequestProperty("app_token", _appToken);
                conn.addRequestProperty("client_id", _clientID);
                conn.addRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod(params[2]);
                conn.setConnectTimeout(300000);

                // para activar el metodo post
                if(params[2] == "POST") {
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
                    wr.writeBytes(params[1]);
                    wr.flush();
                    wr.close();
                }

                InputStream is = conn.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                String line;
                StringBuffer response = new StringBuffer();
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                return response.toString();
            } catch (Exception e) {
                Log.e("Erro API", e.toString());

            }

            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            //this method will be running on UI thread

        }
    }

}
