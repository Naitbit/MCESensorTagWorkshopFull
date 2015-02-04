package com.zinno.mceconf.samples;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;

import com.zinno.sensortag.BleService;
import com.zinno.sensortag.BleServiceBindingActivity;
import com.zinno.sensortag.sensor.TiMagnetometerSensor;
import com.zinno.sensortag.sensor.TiPeriodicalSensor;
import com.zinno.sensortag.sensor.TiSensor;
import com.zinno.sensortag.sensor.TiSensors;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class MagnetometerActivity extends BleServiceBindingActivity {


    // originally from http://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html
    // and modified by Steve Pomeroy <steve@staticfree.info>
    private final int duration = 1; // seconds
    private final int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private final double sample[] = new double[numSamples];
    private double freqOfTone = 300; // hz
    private double MAX_FREQ = 3000;
    private double MIN_FREQ = 500;

    private final byte generatedSnd[] = new byte[2 * numSamples];

    Handler handler = new Handler();



    AudioTrack audioTrack;
    
    
    
    
    
    
    
    
    private static final String TAG = MagnetometerActivity.class.getSimpleName();
    private TiSensor<?> magnetometerSensor;
    private boolean sensorEnabled;

    @InjectView(R.id.sw_high_low)

    Switch switchHighLow;
    @InjectView(R.id.b_calibrate)
    Button calibrateButton;

    @InjectView(R.id.action_bar)
    Toolbar toolbar;

    private static final float OFFSET = 20;
    private double lastValue;

    private enum State {
        LOW,
        HIGH
    }

    private State state = State.LOW;

    private ArrayList<float[]> calibrateValues;
    private float[] environmentCalibrateValue = new float[]{0, 0, 0};
    boolean calibrateEnv;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_magnetometer);
        ButterKnife.inject(this);

        magnetometerSensor = TiSensors.getSensor(TiMagnetometerSensor.UUID_SERVICE);
        calibrateValues = new ArrayList<>();

        calibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calibrateValues = new ArrayList<>();
                calibrateEnv = true;
            }
        });

        toolbar.setNavigationIcon(getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MagnetometerActivity.this.finish();
            }
        });
        toolbar.setTitle(R.string.magnetometer_sample_name);


        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STREAM);
    }


    @Override
    protected void onPause() {
        super.onPause();

        BleService bleService = getBleService();
        if (bleService != null && sensorEnabled) {
            for (String address : getDeviceAddresses()) {
                getBleService().enableSensor(address, magnetometerSensor, false);
            }
        }
    }

    @Override
    public void onServiceDiscovered(String deviceAddress) {
        sensorEnabled = true;
        Log.d(TAG, "onServiceDiscovered");

        getBleService().enableSensor(deviceAddress, magnetometerSensor, true);
        if (magnetometerSensor instanceof TiPeriodicalSensor) {
            TiPeriodicalSensor periodicalSensor = (TiPeriodicalSensor) magnetometerSensor;
            periodicalSensor.setPeriod(periodicalSensor.getMinPeriod());

            getBleService().getBleManager().updateSensor(deviceAddress, magnetometerSensor);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                genTone();
                handler.post(new Runnable() {

                    public void run() {
                        playSound();
                    }
                });
            }
        });
//        thread.start();
    }

    @Override
    public void onDataAvailable(String deviceAddress, String serviceUuid, String characteristicUUid, String text, byte[] data) {
        Log.d(TAG, String.format("DeviceAddress: %s,ServiceUUID: %s, CharacteristicUUIS: %s", deviceAddress, serviceUuid, characteristicUUid));
        Log.d(TAG, String.format("Data: %s", text));


        TiSensor<?> tiSensor = TiSensors.getSensor(serviceUuid);
        final TiMagnetometerSensor tiMagnetometerSensor = (TiMagnetometerSensor) tiSensor;
        float[] magneticValues = tiMagnetometerSensor.getData();

        if (calibrateEnv) {
            calibrateValues.add(magneticValues);
        } else {
            float value = (magneticValues[2] - environmentCalibrateValue[2]);

            setFrequency(value);
            
            
            switch (state) {
                case LOW:
                    if (lastValue - value > OFFSET) {
                        state = State.HIGH;
                        lastValue = value;
                    }
                    break;
                case HIGH:
                    if (value - lastValue > OFFSET) {
                        state = State.LOW;
                        lastValue = value;
                    }
                    break;
            }

            switchHighLow.setChecked(state == State.HIGH);

        }
        if (calibrateEnv && calibrateValues.size() > 10) {

            float[] calibrateValue = new float[]{0, 0, 0};
            for (float[] values : calibrateValues) {
                calibrateValue[0] += values[0] / calibrateValues.size();
                calibrateValue[1] += values[1] / calibrateValues.size();
                calibrateValue[2] += values[2] / calibrateValues.size();
            }

            environmentCalibrateValue = calibrateValue;
            lastValue = calibrateValue[2];
            calibrateEnv = false;
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_magnetometer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }










    private void setFrequency(float value) {
        if(value < 0.0001f) {
            value = 0.0001f;
        }
        Log.w("FREQUENCY", "value:" + value);
        double freq =  Math.sqrt(1/value); // 0.01  0.3r
        Log.w("FREQUENCY", "freq not scaled:" + freq);
        freq = -freq * 9000 + 3100;
        Log.w("FREQUENCY", "freq:" + freq);

//        double freq = 1 / (value * value);
//        double freq = 300+ value;
        freq = Math.min(MAX_FREQ, freq);
        freq = Math.max(MIN_FREQ, freq);
        freqOfTone = freq;
        audioTrack.pause();
        audioTrack.flush();
        genTone();
        playSound();
    }


    void genTone(){
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    void playSound(){
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
        audioTrack.play();
    }
}
