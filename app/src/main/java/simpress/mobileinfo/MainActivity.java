package simpress.mobileinfo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkPermission(android.Manifest.permission.READ_PHONE_STATE)) {
            showInfo();
        } else{
            requestAllPermissions();
        }

        if (checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)){
            startUpService();
        } else{
            requestAllPermissions();
        }
    }

    public void showInfo(){
        TelephonyManager tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String imeiString = tel.getDeviceId();
        String retornoGet;

        TextView imei = (TextView) findViewById(R.id.imeiString);
        imei.setText(imeiString);

        new HttpAsyncTask().execute(getResources().getString(R.string.url_service) + imeiString, "", "GET");
    }

    public void startUpService(){
        Intent it = new Intent(MainActivity.this, simpress.mobileinfo.MyService.class);
        startService(it);
    }

    public void startService(View view){
        Intent it = new Intent(MainActivity.this, simpress.mobileinfo.MyService.class);
        startService(it);
    }

    public void stopService(View view){
        Intent it = new Intent(MainActivity.this, simpress.mobileinfo.MyService.class);
        stopService(it);
    }

    private void requestAllPermissions() {
        if (!checkPermission(android.Manifest.permission.READ_PHONE_STATE))
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_PHONE_STATE}, 1);
        if (!checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION))
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);
    }

    public boolean checkPermission(String permission) {
        int res = this.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showInfo();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            case 2: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startUpService();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public class HttpAsyncTask extends AsyncTask<String, Void, String> {

        private String retornaString = "";

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
                conn.addRequestProperty("app_token", getResources().getString(R.string.app_token));
                conn.addRequestProperty("client_id", getResources().getString(R.string.client_id));
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
                retornaString = response.toString();
                return retornaString;
            } catch (Exception e) {
                Log.e("Erro API", e.toString());
                return retornaString;
            }


        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            try {
                if (result != null) {
                    JSONObject jobj = new JSONObject(result);

                    String nome = jobj.getString("FullName");
                    String matricula = jobj.getString("Registry");

                    TextView nomeString = (TextView) findViewById(R.id.nomeString);
                    nomeString.setText(nome);

                    TextView matriculaStr = (TextView) findViewById(R.id.matriculaString);
                    matriculaStr.setText(matricula);

                    requestAllPermissions();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            //this method will be running on UI thread

        }
    }
}
