package iupui.com.mygps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import it.sauronsoftware.ftp4j.FTPAbortedException;
import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferException;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    //server ip where data will be uploaded
    private String ftpHost = //your ftp ip
    private Button button;
    private Button stopButton;
    private TextView textView, magText, bufferText;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Sensor mySensor;
    private SensorManager sensorManager;
    private EditText sample;
    private CheckBox data;
    private CheckBox wifi;
    private String sampling;
    private Spinner staticSpinner;
    private float[] mag = new float[3];
    private StringBuffer buff;
    private int count = 0;
    String bufferSize = "";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/ddzHH:mm:ss:SS");
    private String fileName="";
    private  File root = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        staticSpinner = (Spinner) findViewById(R.id.static_spinner);
        ArrayAdapter<CharSequence> staticAdapter = ArrayAdapter.createFromResource(this, R.array.buffer_list, android.R.layout.simple_spinner_item);
        staticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        staticSpinner.setAdapter(staticAdapter);
        buff = new StringBuffer("Data updated as on: " + new Date());
        sample = (EditText) findViewById(R.id.sample);
        button = (Button) findViewById(R.id.button);
        stopButton = (Button) findViewById(R.id.stopButton);
        textView = (TextView) findViewById(R.id.textView);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, mySensor, SensorManager.SENSOR_DELAY_FASTEST);
        magText = (TextView) findViewById(R.id.magText);
        wifi = (CheckBox) findViewById(R.id.wifi);
        data = (CheckBox) findViewById(R.id.data);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                root = new File(Environment.getExternalStorageDirectory(), "");
                if (!root.exists()) {
                    root.mkdirs();
                }
                magText.setText("xMag: " + mag[0] + "\nyMag: " + mag[1] + "\nzMag: " + mag[2]);
                textView.setText("Current Latitude: " + location.getLatitude() + "\nCurrent Longitude: " + location.getLongitude() + "\nCurrent Altitude: " + location.getAltitude());
                Date date = new Date();
                buff.append("\n" + sdf.format(date) + " G [" + mag[0] + "," + mag[1] + "," + mag[2] + "]");
                buff.append("\n" + sdf.format(date) + " A [" + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "]");
                TextView bufferStatus = (TextView) findViewById(R.id.bufferTextView);
                bufferStatus.setText("Current buffer status: " + count + "%");
                count++;
                File f = new File(root, "GPS_LOG.txt");
                    try {
                        FileWriter writer = new FileWriter(f);
                        writer.append(buff.toString());
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                //if location is disabled show popup to let user turn on location.
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Location Settings");
                builder.setMessage("Location service is disabled. Please enable it in the location settings.")
                        .setPositiveButton("Location Settings", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        }).create().show();
            }
        };
        //check if user has given location storage and internet permissions
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PermissionChecker.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 10);
            return;
        } else {
            configureButton();
        }

    }

    public void uploadFile(final File fileName) {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    FTPClient client = new FTPClient();
                    try {
                        client.connect(ftpHost, 21);
                        client.login(getString(R.string.USERNAME), getString(R.string.PASSWORD));//store your ftp username and password in the strings file.
                        client.setType(FTPClient.TYPE_BINARY);
                        client.changeDirectory("/htdocs/uploads/");
                        client.upload(fileName);
                        client.disconnect(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (FTPIllegalReplyException e) {
                        e.printStackTrace();
                    } catch (FTPException e) {
                        e.printStackTrace();
                    } catch (FTPAbortedException e) {
                        e.printStackTrace();
                    } catch (FTPDataTransferException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        mag[0] = event.values[0];
        mag[1] = event.values[1];
        mag[2] = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 10:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    configureButton();
                }
                return;
        }
    }

    private void configureButton() {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //get sampling rate from user
                sampling = sample.getText().toString();
                if (TextUtils.isEmpty(sampling) || sampling.equals("0")) {
                    Toast.makeText(getApplicationContext(), "No sampling set or used 0.\nUsing default (1s)", Toast.LENGTH_LONG).show();
                    //if sampling rate is not set or sampling rate is set to 0. use 1
                    sampling = "1";
                } else {
                    Toast.makeText(getApplicationContext(), "Using " + sampling + " seconds as sampling time.", Toast.LENGTH_LONG).show();
                }
                int sampleToUse = Integer.parseInt(sampling) * 1000;
                bufferSize = "" + staticSpinner.getItemAtPosition(staticSpinner.getSelectedItemPosition());
                bufferSize = bufferSize.substring(0, bufferSize.length() - 1);
                //request location updates for the user defined sampling rate.
                locationManager.requestLocationUpdates("gps", sampleToUse, 0, locationListener);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                locationManager.removeUpdates(locationListener);
                //if buffer full create new file and save data in new file.
                fileName = new String("GPS_LOG_" + new Date() + ".txt");
                File fileToUpload = new File(root, fileName);
                FileWriter writer = null;
                try {
                    writer = new FileWriter(fileToUpload);
                    writer.append(buff.toString());
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //check if data is allowed through 3g or wifi
                if (data.isChecked() || wifi.isChecked()) {
                    Toast.makeText(getApplicationContext(), "Uploading file...", Toast.LENGTH_LONG).show();
                    ConnectivityManager cm =
                            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo ni = cm.getActiveNetworkInfo();
                    //check if wifi/3g network connectivity is available
                    if (ni != null && ni.isConnectedOrConnecting()) {
                        //if connected to internet upload the file to the server
                        uploadFile(fileToUpload);
                        Toast.makeText(getApplicationContext(), "File upload complete.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Not connected to the internet.", Toast.LENGTH_LONG).show();
                    }


                } else {
                    Toast.makeText(getApplicationContext(), "No internet allowed.\nFile saved locally in root folder.", Toast.LENGTH_LONG).show();
                }

                buff.delete(0, buff.length());
                buff = new StringBuffer("Data updated as on: " + new Date());
                count = 0;
            }

        });
    }


}
