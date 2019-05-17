package com.example.fancontrol;



import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;



public class MainActivity extends AppCompatActivity {

    // UI elements
    private EditText intensityET;
    private Button setButton;
    private TextView connectedTV;
    // Permission code
    private final int REQUEST_ENABLE_BT = Activity.RESULT_OK;
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 99;
    // BT objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private BluetoothGatt bluetoothGatt;

    // Handlers
    private static final long SCAN_DELAY = 5000;
    private Handler scanHandler = new Handler();
    // Context
    private Context context;
    // BT UUIDs
    UUID fanUUID = UUID.fromString("00000001-0000-0000-FDFD-FDFDFDFDFDFD");
    UUID intensityUUID = UUID.fromString("10000001-0000-0000-FDFD-FDFDFDFDFDFD");
    String TAG="fanControl1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_main);


        connectedTV =findViewById(R.id.connected);
        intensityET = findViewById(R.id.setText);
        setButton=findViewById(R.id.button);
        setButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int value= Integer.valueOf(intensityET.getText().toString());
                boolean bool=writeCharacteristic(value);

                if(bool)
                {
                    Toast.makeText(context, "Value written successfully", Toast.LENGTH_SHORT).show();
                }
                else
                    Toast.makeText(context, "Value wasn't written", Toast.LENGTH_SHORT).show();
            }
        });


        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If BT not supported by device
        if (bluetoothAdapter == null) {
            Log.e("BLUETOOTH ERROR", "Device has no bluetooth capabilities!");
        }

        // If BT disabled
        if (!bluetoothAdapter.isEnabled()) {
            // Request BT
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            permissionCheck();
        }

        // Setup BT
        setUpBluetooth();

    }



    /**
     * This method sets up bluetooth.
     */
    private void setUpBluetooth() {
        // Init BT manager
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        // Init BT scanner
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        // Scan devices
        scanLeDevice(true);
    }

    /**
     * This method is used for scanning the BT devices.
     * @param enable (Used to activate/deactivate scanning)
     */
    private void scanLeDevice(boolean enable) {
        // If scan should be activated
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            scanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothScanner.stopScan(scanCallback);
                }
            }, SCAN_DELAY);

            // create filters for the scan
            ScanFilter weatherFilter = new ScanFilter.Builder()
                    .setDeviceName("IPVS-LIGHT").build();

            List<ScanFilter> scanFilterList = new ArrayList<ScanFilter>();
            scanFilterList.add(weatherFilter);

            // create the settings for the scan
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

            bluetoothScanner.startScan(scanFilterList, settingsBuilder.build(), scanCallback);
            // If scan should be deactivated
        } else {
            bluetoothScanner.stopScan(scanCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from devices
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            connectedTV.setVisibility(View.GONE);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {

        @Override
        public synchronized void onScanResult(int callbackType, ScanResult result) {

            if (bluetoothGatt == null && result.getDevice().getName().equals("IPVS-LIGHT")) {
                bluetoothGatt = result.getDevice().connectGatt(context, false,gattCallback);

            }

            super.onScanResult(callbackType, result);

            //stop scanning
            if (bluetoothGatt != null)
                scanLeDevice(false);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    private BluetoothGattCallback gattCallback= new BluetoothGattCallback() {


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService mBluetoothGattService = bluetoothGatt.getService(fanUUID);
                if (mBluetoothGattService != null) {
                    Toast.makeText(context, "Service found", Toast.LENGTH_LONG).show();
                    connectedTV.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "Service characteristic not found for UUID: " + fanUUID);
                }
            }
        }
    };

    public boolean writeCharacteristic(int value){

        //check bluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            return false;
        }
        //check if service is available
        BluetoothGattService service = bluetoothGatt.getService(fanUUID);
        if (service == null) {
            Log.e(TAG, "service not found!");
            return false;
        }
        //check is characteristic is available
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(intensityUUID);
        if (characteristic == null) {
            Log.e(TAG, "characteristic not found!");
            return false;
        }

        //set fan speed
        characteristic.setValue(value,BluetoothGattCharacteristic.FORMAT_UINT16,0);

        return bluetoothGatt.writeCharacteristic(characteristic);
    }






    //code for runtime permission
    @TargetApi(Build.VERSION_CODES.M)
    private void permissionCheck() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location");

        if (permissionsList.size() > 0) {
            if (permissionsNeeded.size() > 0) {

                // Need Rationale
                String message = "App need access to " + permissionsNeeded.get(0);

                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                showMessageOKCancel(message,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                return;
            }
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return;
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);


                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                // Check for ACCESS_FINE_LOCATION
                if (perms.get(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, "One or More Permissions are DENIED ", Toast.LENGTH_SHORT)
                            .show();


                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


}

