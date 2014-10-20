package edu.uri.egr.spark;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;


public class SPARKMain extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {
    private static final String TAG = SPARKMain.class.getSimpleName();
    private GoogleApiClient mGoogleApi;
    private Button controlButton;

    private final String SPARK_START = "/spark/start/service";
    private final String SPARK_STOP = "/spark/stop/service";
    private final String SPARK_DATA = "/spark/data";
    private final int SPARK_NOTIFY_ID = 100;

    private final int SPARK_STATE_LOGGING = 1;
    private final int SPARK_STATE_IDLE = 2;
    private int SPARK_STATE_CURRENT;

    // Message sender spin-off thread!
    private class messageSender extends Thread {
        String id;
        messageSender(String s) {
            id = s;
        }

        private Node getNode() {
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApi).await(); // Wait for our node list!
            if (!nodes.getNodes().isEmpty()) {
                return nodes.getNodes().get(0);
            }
            Log.e(TAG, "We couldn't find any nodes!");
            return null;
        }

        public void run() {
            Node n = getNode();
            if (n != null) {
                MessageApi.SendMessageResult res = Wearable.MessageApi.sendMessage(mGoogleApi, n.getId(), id, null).await();
                if (res.getStatus().isSuccess()) {
                    Log.i(TAG, "Message: " + id + " sent to (" + n.getDisplayName() + ")");
                } else {
                    Log.e(TAG, "Failed to send message: " + res.getStatus());
                }
            }
        }
    }

    public void onButtonPress(View button) {
        if (SPARK_STATE_CURRENT == SPARK_STATE_IDLE) {
            new messageSender(SPARK_START).start();
            controlButton.setText("Stop");
            SPARK_STATE_CURRENT = SPARK_STATE_LOGGING;
        } else {
            new messageSender(SPARK_STOP).start();
            controlButton.setText("Start");
            SPARK_STATE_CURRENT = SPARK_STATE_IDLE;
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer events) {
        for (DataEvent event : events) {
            // We should have just gotten a METRIC BOATLOAD of data.  Take it out!!!!
            Log.i(TAG, "DataEvent: " + event.getDataItem().toString());
            if (event.getDataItem().getUri().getPath().equals(SPARK_DATA)) {

            }
        }
    }

    @Override
    public void onConnected(Bundle hint) {
        Log.i(TAG, "Connected to Google.");
        Wearable.DataApi.addListener(mGoogleApi, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.e(TAG, "Google connection suspended: " + String.valueOf(cause));
    }

    @Override
    public void onConnectionFailed(ConnectionResult res) {
        Log.e(TAG, "Google connection error: " + res.toString());
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApi.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.DataApi.removeListener(mGoogleApi, this);
        mGoogleApi.disconnect();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sparkmain);

        controlButton = (Button) findViewById(R.id.controlButton);

        SPARK_STATE_CURRENT = SPARK_STATE_IDLE;

        // Connect to Google
        mGoogleApi = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.sparkmain, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
