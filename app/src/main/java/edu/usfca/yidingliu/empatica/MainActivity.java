package edu.usfca.yidingliu.empatica;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import org.w3c.dom.Text;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION=2;
    private static final long STREAMING_TIME = 3600000; // Stops streaming 3600 seconds(1 hour) after connection
    private static final long SCANNING_TIME = 10000;//Stop scanning after 10 sec
    //yiding APIKey e4ffcddcb6fe4432939c700cbd9e4efc
    //Beste APIKey fa39f8576b1841fd9f219d6a9f777381
    private static final String EMPATICA_API_KEY = "fa39f8576b1841fd9f219d6a9f777381";

    private EmpaDeviceManager deviceManager;
    private EmpaStatus status;

    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView hrLabel;
    private TextView hrvLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView statusLabel;
    private Button searchDeviceBtn;
    private TextView deviceNameLabel;
    private RelativeLayout dataCnt;

    /****************************Variable of calculating HRV*******************************/
    private int time=10;//the unit is seconds
    class IBIdata{
        public float IBI;
        public long timestamp;
        public IBIdata(float IBI,long timestamp){
            this.IBI=IBI;
            this.timestamp=timestamp;
        }
    }
    private Queue<IBIdata> queue;
    private float IBIsum;

    /**************************Android APP Status************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (RelativeLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        hrLabel = (TextView) findViewById(R.id.hr);
        hrvLabel = (TextView) findViewById(R.id.hrv);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);
        searchDeviceBtn=(Button) findViewById(R.id.search_device);
        searchDeviceBtn.setEnabled(false);

        //Initialize vars that reference to HRV
        queue=new LinkedList<>();
        IBIsum=0;

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        }, PERMISSION_REQUEST_COARSE_LOCATION);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //deviceManager.stopScanning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deviceManager.cleanUp();
    }

    /**************************Android Service************************/
    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**************************Empatica Status Delegate************************/
    @Override
    public void didDiscoverDevice(BluetoothDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        Toast.makeText(this,"Found a device",Toast.LENGTH_LONG).show();
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus status, EmpaSensorType type) {
        // No need to implement this right now
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        this.status=status;
        // Update the UI
        updateLabel(statusLabel, status.name());
        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            searchDeviceBtn.setEnabled(true);
            updateLabel(statusLabel, status.name() + " - Turn on your device,and press the scan button");
        }
        else if (status == EmpaStatus.CONNECTED) {
            // Stop streaming after STREAMING_TIME
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataCnt.setVisibility(View.VISIBLE);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Disconnect device
                            deviceManager.disconnect();
                        }
                    }, STREAMING_TIME);
                }
            });
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
        }
    }
    /**************************Empatica Data Delegate************************/
    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, "" + ibi);
        float hr=60/ibi;
        updateLabel(hrLabel,""+ Math.round(hr));
        double HRV=CalculateSD(new IBIdata(ibi,(long)timestamp));
        updateLabel(hrvLabel,""+ HRV);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
    }

    /**************************Click Event************************/
    public void scan(View v){
        searchDeviceBtn.setEnabled(false);
        deviceManager.startScanning();
        // Stop scanning after SCANNING_TIME
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(status==EmpaStatus.CONNECTED||status==EmpaStatus.CONNECTING) return;
                        deviceManager.stopScanning();
                        Toast.makeText(MainActivity.this,"Didn't find any device",Toast.LENGTH_LONG).show();
                        searchDeviceBtn.setEnabled(true);
                    }
                }, SCANNING_TIME);
            }
        });
    }
    /**************************Private Methods************************/
    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    // Calculate SD
    private double CalculateSD(IBIdata data){
        IBIsum+=data.IBI;
        queue.add(data);
        if(data.timestamp-queue.peek().timestamp<time){
            return 0;
        }
        else{
            while(data.timestamp-queue.peek().timestamp>time){
                IBIsum-=queue.poll().IBI;
            }

            float avg=IBIsum/queue.size();

            double SDsum=0;
            Iterator<IBIdata> iterator=queue.iterator();
            while(iterator.hasNext()){
                SDsum+=Math.pow(iterator.next().IBI-avg,2);
            }
            return Math.sqrt(SDsum/queue.size());
        }
    }
}
