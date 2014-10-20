package edu.uri.egr.spark;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.HashSet;

public class ListenerService extends WearableListenerService {
    private static final String TAG = ListenerService.class.getSimpleName();
    private GoogleApiClient mGoogleApi;
    private sensorSpinOff sensorSpin;
    private Thread sensorSpinThread;

    private final String SPARK_START = "/spark/start/service";
    private final String SPARK_STOP = "/spark/stop/service";
    private final String SPARK_DATA = "/spark/data";
    private final int SPARK_NOTIFY_ID = 100;

    private void constructGoogleAPI() {
        mGoogleApi = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApi.connect();
        Log.v(TAG, "Creating the listener service!");
    }

    // God bless, this actually works!
    private class sensorSpinOff extends Thread implements SensorEventListener {
        private SensorManager mSensorManager;
        private Sensor mSensor;

        private PutDataMapRequest dataMapReq;
        private DataMap dataMap;
        private GoogleApiClient api;

        private long startTime;

        private volatile boolean running = false;

        sensorSpinOff(GoogleApiClient _api) {
            api = _api;
        }

        private void construct() {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            // Create our datamap
            dataMapReq = PutDataMapRequest.create(SPARK_DATA);
            dataMap = dataMapReq.getDataMap();
        }

        @Override
        public final void onAccuracyChanged(Sensor sensor, int acc) {

        }

        @Override
        public final void onSensorChanged(SensorEvent event) {
            //Log.i(TAG, "Sensor event: " + String.valueOf(event.values[0]));
           dataMap.putFloatArray(String.valueOf(SystemClock.uptimeMillis() - startTime), event.values);

            // FINAL OPTION - FIRE AND FORGET.  CROSS YOUR FINGERS.
           // MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(api, node, SPARK_DATA, event.values.)
        }

        @Override
        public void run() {
            running = true;
            startTime = SystemClock.uptimeMillis();

            construct();
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }

        private void kill() {
            running = false;
            mSensorManager.unregisterListener(this);

            Log.i(TAG, "Shutting down and sending!");

            // Prepare the data map for it's long journey through bluetooth.
            PutDataRequest request = dataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(api, request);  // Cya!
            pendingResult.await();
            Log.i(TAG, "Sent data!");
        }
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        Log.v(TAG, "Received message: " + event.getPath());
        if (event.getPath().equals(SPARK_START)) {
            Log.v(TAG, "Received start.  Beginning persistent notification.");

            constructGoogleAPI();

            // Construct the intent for notification!
            Intent nIntent = new Intent(this, LoggerActivity.class);
            nIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            /* TODO: Send over information from the phone
                Patient ##???
             */

            PendingIntent nPIntent = PendingIntent.getActivity(this, 0, nIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder nBuilder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setOngoing(true)
                    .extend(new Notification.WearableExtender()
                                    .setCustomSizePreset(Notification.WearableExtender.SIZE_FULL_SCREEN)
                                    .setDisplayIntent(nPIntent)
                    );

            NotificationManager nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nManager.notify(SPARK_NOTIFY_ID, nBuilder.build());

            // Start the fun.
            sensorSpin = new sensorSpinOff(mGoogleApi);
            sensorSpinThread = new Thread(sensorSpin);
            sensorSpinThread.start();

        } else if (event.getPath().equals(SPARK_STOP)) {
            // Stop the things!  Do some crazy recovery!
            if (sensorSpinThread != null) {
                sensorSpin.kill();
            }

        } else {
            super.onMessageReceived(event);
        }
    }

}
