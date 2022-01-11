package com.example.smartphonetracker;


import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import android.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {
    private MqttHelper mqttHelper;
    private TextView dataReceived;
    private SensorManager mSensorManager;
    private Sensor mProximity, mAccelorometer;
    private static final double SENSOR_SENSITIVITY = 4.5;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Context context;
    private TextView txtLat, txtLong, txtCharging, txtPocket, txtStatus, txtMoving, txtMovingValues;
    private boolean gps_enabled, network_enabled, enableGps, enableStealing, enableRecorder, enableCharging, enableMouvement, isAuthenticated;
    private RelativeLayout recorderBtn, chargingBtn, stealingBtn, gpsBtn, mouvementBtn;
    private ImageView shieldLogo;
    private LinearLayout statusBg;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private MediaPlayer mp;
    private DecimalFormat df = new DecimalFormat();
    private BroadcastReceiver broadcastReceiver;
    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;
    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;
    private float longData, latData, batteryChargeData, xData, yData, zData;
    private String batteryStatusData, inPocketData, mouvementData, brandData, modelData, imeiData;
    private MediaRecorder mRecorder;

    private void checkStatus() {
        if (enableGps && enableRecorder) {
            txtStatus.setText("Your device is protected");
            statusBg.setBackgroundResource(R.drawable.bg_green);
            shieldLogo.setImageResource(R.drawable.shield_green);
        }
        else if (!enableGps && !enableRecorder) {
            txtStatus.setText("Your device is not protected");
            statusBg.setBackgroundResource(R.drawable.bg_red);
            shieldLogo.setImageResource(R.drawable.shield_red);
        }
        else {
            txtStatus.setText("Your device is not fully protected");
            statusBg.setBackgroundResource(R.drawable.bg_yellow);
            shieldLogo.setImageResource(R.drawable.shield_yellow);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        biometricPromptDialog();
        df.setMaximumFractionDigits(3);

        modelData = Build.MODEL;
        brandData = Build.MANUFACTURER;
        imeiData = "332286369648658";
        shieldLogo = (ImageView) findViewById(R.id.shieldLogo);
        statusBg = (LinearLayout) findViewById(R.id.statusBg);
        isAuthenticated = false;
        txtStatus = (TextView) findViewById(R.id.txtStatus);
        txtLat = (TextView) findViewById(R.id.LatitudeTxt);
        txtLong = (TextView) findViewById(R.id.LongtitudeTxt);
        txtCharging = (TextView) findViewById(R.id.ChargingTxt);
        txtPocket = (TextView) findViewById(R.id.PocketTxt);
        txtMoving = (TextView) findViewById(R.id.MovingTxt);
        txtMovingValues = (TextView) findViewById(R.id.MovingValuesTxt);

        recorderBtn = (RelativeLayout) findViewById(R.id.recorderBtn);
        chargingBtn = (RelativeLayout) findViewById(R.id.chargingBtn);
        stealingBtn = (RelativeLayout) findViewById(R.id.stealingBtn);
        gpsBtn = (RelativeLayout) findViewById(R.id.gpsBtn);
        mouvementBtn = (RelativeLayout) findViewById(R.id.movingBtn);

        SharedPreferences setings = getSharedPreferences("SmartphoneTracker", 0);
        enableCharging = setings.getBoolean("enableCharging", true);
        enableGps = setings.getBoolean("enableGps", true);
        enableRecorder = setings.getBoolean("enableRecorder", true);
        enableStealing = setings.getBoolean("enableStealing", true);
        enableMouvement = setings.getBoolean("enableMouvement", true);

        statusBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAuthenticated)
                    stopAlert();
                else {
                    biometricPromptDialog();
                }
            }
        });
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                txtCharging.setText("Battery: " + level);
                batteryChargeData = level;
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status ==
                        BatteryManager.BATTERY_STATUS_FULL;
                if (isCharging) {
                    txtCharging.setText("Battery: Charging");
                    batteryStatusData = "Charging";
                    stopAlert();
                } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING){
                    if (enableCharging) {
                        startAlert("Charger removed", context);
                    }
                    txtCharging.setText("Battery: Charger disconnected");
                    batteryStatusData = "Charger disconnected";

                }
            }
        };
        if (enableCharging)
            chargingBtn.setBackgroundColor(Color.parseColor("#84b76d"));
        else
            chargingBtn.setBackgroundColor(Color.parseColor("#e06666"));

        if (enableGps)
            gpsBtn.setBackgroundColor(Color.parseColor("#84b76d"));
        else
            gpsBtn.setBackgroundColor(Color.parseColor("#e06666"));

        if (enableRecorder)
            recorderBtn.setBackgroundColor(Color.parseColor("#84b76d"));
        else
            recorderBtn.setBackgroundColor(Color.parseColor("#e06666"));

        if (enableStealing)
            stealingBtn.setBackgroundColor(Color.parseColor("#84b76d"));
        else
            stealingBtn.setBackgroundColor(Color.parseColor("#e06666"));
        if (enableMouvement)
            mouvementBtn.setBackgroundColor(Color.parseColor("#84b76d"));
        else
            mouvementBtn.setBackgroundColor(Color.parseColor("#e06666"));
        checkStatus();
        stealingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAuthenticated) {
                    enableStealing = !enableStealing;
                    if (enableStealing)
                        stealingBtn.setBackgroundColor(Color.parseColor("#84b76d"));
                    else
                        stealingBtn.setBackgroundColor(Color.parseColor("#e06666"));
                    checkStatus();
                }
                else {
                    biometricPromptDialog();
                }
            }
        });

        mouvementBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAuthenticated) {
                    enableMouvement = !enableMouvement;
                    if (enableMouvement)
                        mouvementBtn.setBackgroundColor(Color.parseColor("#84b76d"));
                    else
                        mouvementBtn.setBackgroundColor(Color.parseColor("#e06666"));
                    checkStatus();
                }
                else {
                    biometricPromptDialog();
                }
            }
        });

        gpsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAuthenticated) {
                    enableGps = !enableGps;
                    if (enableGps)
                        gpsBtn.setBackgroundColor(Color.parseColor("#84b76d"));
                    else
                        gpsBtn.setBackgroundColor(Color.parseColor("#e06666"));
                    checkStatus();
                }
                else {
                    biometricPromptDialog();
                }

            }
        });

        recorderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAuthenticated) {
                    enableRecorder = !enableRecorder;
                    if (enableRecorder)
                        recorderBtn.setBackgroundColor(Color.parseColor("#84b76d"));
                    else
                        recorderBtn.setBackgroundColor(Color.parseColor("#e06666"));
                    checkStatus();
                }
                else {
                    biometricPromptDialog();
                }
            }
        });

        chargingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAuthenticated) {
                    enableCharging = !enableCharging;
                    if (enableCharging)
                        chargingBtn.setBackgroundColor(Color.parseColor("#84b76d"));
                    else
                        chargingBtn.setBackgroundColor(Color.parseColor("#e06666"));
                    checkStatus();
                }
                else {
                    biometricPromptDialog();
                }
            }
        });

        this.registerReceiver(this.broadcastReceiver, new
                IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mAccelorometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        startMqtt();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO}, 1);

        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO, }, 1);

        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        txtLat.setText("Latitude: " + location.getLatitude());
        txtLong.setText("Longitude: " + location.getLongitude());
        longData = (float)location.getLongitude();
        latData = (float)location.getLatitude();
        JSONObject json = new JSONObject();
        try {
            json.put("messageType", "information");
            json.put("longitude", longData);
            json.put("latitude", latData);
            if (!enableGps) {
                json.put("longitude", 0);
                json.put("latitude", 0);
            }
            json.put("batteryLevel", batteryChargeData);
            json.put("batteryStatus", batteryStatusData);
            json.put("inPocket", inPocketData);
            json.put("mouvement", mouvementData);
            json.put("x", xData);
            json.put("y", yData);
            json.put("z", zData);
            json.put("model", modelData);
            json.put("brand", brandData);
            json.put("imei", imeiData);
            mqttHelper.publishToopic(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("Latitude","disable");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("Latitude","enable");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("Latitude","status");
    }

    private void startMqtt(){
        mqttHelper = new MqttHelper(getApplicationContext());
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {

            }

            @Override
            public void connectionLost(Throwable throwable) {

            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.w("Debug",mqttMessage.toString());
                JSONObject jsonObject = new JSONObject(mqttMessage.toString());
                if (jsonObject.getString("messageType").equals("command") && jsonObject.getString("message").equals("audio")) {
                    if (enableRecorder)
                        recordSound();
                    else {
                        JSONObject json = new JSONObject();
                        json.put("messageType", "audio");
                        json.put("data", "");
                        mqttHelper.publishToopic(json.toString());
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mAccelorometer, SensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }
    @Override
    protected void onStop(){
        super.onStop();
        mSensorManager.unregisterListener(this);
        SharedPreferences setings = getSharedPreferences("SmartphoneTracker", 0);
        SharedPreferences.Editor editor = setings.edit();
        editor.putBoolean("enableCharging", enableCharging);
        editor.putBoolean("enableGps", enableGps);
        editor.putBoolean("enableRecorder", enableRecorder);
        editor.putBoolean("enableStealing", enableStealing);
        editor.putBoolean("enableMouvement", enableMouvement);
        editor.commit();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (event.values[0] >= -SENSOR_SENSITIVITY && event.values[0] <= SENSOR_SENSITIVITY) {
                //near
                txtPocket.setText("In Pocket: YES");
                inPocketData = "YES";
            } else {
                //far
                if (txtPocket.getText().equals("In Pocket: YES") && enableStealing)
                    startAlert("Stolen device", this);
                txtPocket.setText("In Pocket: NO");
                inPocketData = "NO";
            }
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            txtMovingValues.setText("Postion: X: " + df.format(event.values[0]) + ", Y: " + df.format(event.values[1]) + ", Z: " + df.format(event.values[2]));
            deltaX = Math.abs(lastX - event.values[0]);
            deltaY = Math.abs(lastY - event.values[1]);
            deltaZ = Math.abs(lastZ - event.values[2]);
            lastX = event.values[0];
            lastY = event.values[1];
            lastZ = event.values[2];
            xData = Float.parseFloat(df.format(event.values[0]));
            yData = Float.parseFloat(df.format(event.values[1]));
            zData = Float.parseFloat(df.format(event.values[2]));

            if (deltaX > 2 ||  deltaY > 2 || deltaZ >2) {
                txtMoving.setText("Movement: YES");
                mouvementData = "YES";
                if (enableMouvement) {
                    startAlert("Moving device", this);
                }
            }
            else {
                mouvementData = "NO";
                txtMoving.setText("Movement: NO");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void biometricPromptDialog() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                //error authenticating, stop tasks that requires auth
                //authStatusTv.setText("Authentication error: " + errString);
                Toast.makeText(MainActivity.this, "Authentication failed!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                //authentication succeed, continue tasts that requires auth
                //authStatusTv.setText("Authentication succeed...!");
                isAuthenticated = true;
                Toast.makeText(MainActivity.this, "Authentication succeed!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                //failed authenticating, stop tasks that requires auth
                //authStatusTv.setText("Authentication failed...!");
                Toast.makeText(MainActivity.this, "Authentication failed!", Toast.LENGTH_SHORT).show();
            }
        });

        //setup title,description on auth dialog
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Login using fingerprint authentication")
                .setNegativeButtonText("Cancel")
                .build();
        biometricPrompt.authenticate(promptInfo);

    }

    private void startMP(final Context context){
        if(mp == null){
            mp = MediaPlayer.create( context, R.raw.alert);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    stopMP();
                }
            });
        }
        mp.start();
        mp.setLooping(true);
    }

    private void stopMP() {
        if(mp != null) {
            mp.release();
            mp = null;
        }
    }

    private void startAlert(String msg, Context ctx) {
        startMP(ctx);
        txtStatus.setText(msg);
        statusBg.setBackgroundResource(R.drawable.bg_red);
        shieldLogo.setImageResource(R.drawable.shield_red);
    }

    private void stopAlert() {
        stopMP();
        checkStatus();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void recordSound() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ParcelFileDescriptor[] descriptors = new ParcelFileDescriptor[0];
        try {
            descriptors = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ParcelFileDescriptor parcelRead = new ParcelFileDescriptor(descriptors[0]);
        ParcelFileDescriptor parcelWrite = new ParcelFileDescriptor(descriptors[1]);
        InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(parcelRead);

        mRecorder =  new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/recording.mp3");
        mRecorder.prepare();
        mRecorder.start();

        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRecorder.stop();
                        mRecorder.reset();
                        mRecorder.release();
                        String base64 = "";
                        try {/*from   w w w .  ja  va  2s  .  c om*/
                            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/recording.mp3");
                            byte[] buffer = new byte[(int) file.length() + 100];
                            @SuppressWarnings("resource")
                            int length = new FileInputStream(file).read(buffer);
                            base64 = Base64.encodeToString(buffer, 0, length,
                                    Base64.DEFAULT);
                            JSONObject json = new JSONObject();
                            json.put("messageType", "audio");
                            json.put("data", base64);
                            mqttHelper.publishToopic(json.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

        }, 11000);


    }

}