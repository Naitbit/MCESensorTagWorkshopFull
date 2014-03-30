package sample.ble.sensortag;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import sample.ble.sensortag.adapters.TiServicesAdapter;
import sample.ble.sensortag.fusion.SensorFusionActivity;
import sample.ble.sensortag.sensor.TiSensor;
import sample.ble.sensortag.sensor.TiSensors;

import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BleService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceServicesActivity extends BleServiceBindingActivity
                                    implements ExpandableListView.OnChildClickListener,
                                               TiServicesAdapter.OnServiceItemClickListener {
    @SuppressWarnings("UnusedDeclaration")
    private final static String TAG = DeviceServicesActivity.class.getSimpleName();

    private TextView connectionState;
    private TextView dataField;
    private ExpandableListView gattServicesList;
    private TiServicesAdapter gattServiceAdapter;

    private TiSensor<?> activeSensor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_services_activity);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(getDeviceAddress());
        gattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        gattServicesList.setOnChildClickListener(this);
        connectionState = (TextView) findViewById(R.id.connection_state);
        dataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(getDeviceName());
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_demo:
                final Intent demoIntent = new Intent();
                demoIntent.setClass(DeviceServicesActivity.this, SensorFusionActivity.class);
                demoIntent.putExtra(SensorFusionActivity.EXTRA_DEVICE_ADDRESS, getDeviceAddress());
                startActivity(demoIntent);
                break;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected() {
        connectionState.setText(R.string.connected);
    }

    @Override
    public void onDisconnected() {
        connectionState.setText(R.string.disconnected);
        clearUI();
        finish();
    }

    @Override
    public void onServiceDiscovered() {
        // Show all the supported services and characteristics on the user interface.
        displayGattServices(getBleService().getSupportedGattServices());
    }

    @Override
    public void onDataAvailable(String serviceUuid, String characteristicUUid, String text, byte[] data) {
        dataField.setText(text);
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                int childPosition, long id) {
        if (gattServiceAdapter == null)
            return false;

        final BluetoothGattCharacteristic characteristic = gattServiceAdapter.getChild(groupPosition, childPosition);
        final TiSensor<?> sensor = TiSensors.getSensor(characteristic.getService().getUuid().toString());

        if (activeSensor != null)
            getBleService().enableSensor(activeSensor, false);

        if (sensor == null) {
            getBleService().getBleManager().readCharacteristic(characteristic);
            return true;
        }

        if (sensor == activeSensor)
            return true;

        activeSensor = sensor;
        getBleService().enableSensor(sensor, true);
        return true;
    }

    @Override
    public void onServiceUpdated(BluetoothGattService service) {
        final TiSensor<?> sensor = TiSensors.getSensor(service.getUuid().toString());
        if (sensor == null)
            return;

        getBleService().updateSensor(sensor);
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;

        gattServiceAdapter = new TiServicesAdapter(this, gattServices);
        gattServiceAdapter.setServiceListener(this);
        gattServicesList.setAdapter(gattServiceAdapter);
    }

    private void clearUI() {
        gattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        dataField.setText(R.string.no_data);
    }
}
