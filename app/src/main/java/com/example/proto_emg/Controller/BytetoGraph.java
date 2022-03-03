package com.example.proto_emg.Controller;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.proto_emg.Module.Entity.ScannedData;
import com.example.proto_emg.Module.Service.BluetoothLeService;
import com.example.proto_emg.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BytetoGraph extends AppCompatActivity {
    public static final String TAG = BytetoGraph.class.getSimpleName();
    public static final String INTENT_KEY = "GET_DEVICE";
    private final String serviceUUID = "00001101-0000-1000-8000-00805f9b34fb";
    private final String characteristicsUUID = "000000a81-0000-1000-8000-00805f9b34fb";
    private BluetoothGattCharacteristic emg_characteristic;
    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;
    private TextView byteView,serverStatus;
    private short data_count = 0;

    private LineChart data_chart;
    private Thread thread;
    private boolean plotData = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.byte_graph_view);
        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
        initBLE();
        initUI();
        startPlot();
    }

    /**Initialize Bluetooth*/
    private void initBLE(){
        /**Bind Service
         * @see BluetoothLeService*/
        Intent bleService = new Intent(this, BluetoothLeService.class);
        bindService(bleService,mServiceConnection,BIND_AUTO_CREATE);
        /**Set Broadcasting*/
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);//連接一個GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);//從GATT服務中斷開連接
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);//查找GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);//從服務中接受(收)數據

        registerReceiver(mGattUpdateReceiver, intentFilter);
        if (mBluetoothLeService != null) mBluetoothLeService.connect(selectedDevice.getAddress());
    }

    /**Setup UI elements*/
    private void initUI(){
        byteView = findViewById(R.id.byteView);
        serverStatus = findViewById(R.id.connection_state);
        setGraph();
    }

    /**BT Connected/Disconnected*/
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            mBluetoothLeService.connect(selectedDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService.disconnect();
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            /**If connected*/
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "BT Connected");
                serverStatus.setText("Connected");
            }
            /**If no connection*/
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "Bluetooth Disconnected");
                serverStatus.setText("Disconnected");
            }
            /**GATT Service Found*/
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "GATT Service Discovered");
                List<BluetoothGattService> gattList =  mBluetoothLeService.getSupportedGattServices();
                displayGattAtLogCat(gattList);
                connectToCharacteristics(gattList);
            }
            /**Receive Data From Bluetooth Server*/
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG, "Received Data via Bluetooth");
                byte[] getByteData = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                //byte[] decoded = Base64.decode(getByteData, Base64.DEFAULT);
                //Log.d(TAG, "No. of Bytes: " + getByteData.length);
                //Log.println(Log.DEBUG,TAG,Arrays.toString(getByteData));
                int[] result_int = byteToInt(getByteData);
//                StringBuilder stringBuilder = new StringBuilder(getByteData.length);
//                for (byte byteChar : getByteData)
//                    stringBuilder.append(String.format("%02X ", byteChar));
//                String stringData = new String(getByteData);
//                Log.d(TAG, "String: "+stringData+"\n"
//                        +"byte[]: "+BluetoothLeService.byteArrayToHexStr(getByteData));
                Log.d(TAG, "Decoded int[]: " + Arrays.toString(result_int));
                byteView.setText("int[]: "+ Arrays.toString(result_int));
                updateGraph(result_int);                                                               //add entries to graph one by one
            }
        }
    };//onReceive
    /**Display BT information on Logcat*/
    private void displayGattAtLogCat(List<BluetoothGattService> gattList){
        for (BluetoothGattService service : gattList){
            Log.d(TAG, "Service: "+service.getUuid().toString());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()){
                Log.d(TAG, "\tCharacteristic: "+characteristic.getUuid().toString()+" ,Properties: "+
                        mBluetoothLeService.getPropertiesTagArray(characteristic.getProperties()));
                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()){
                    Log.d(TAG, "\t\tDescriptor: "+descriptor.getUuid().toString());
                }
            }
        }
    }
    /**Close BT*/
    private void closeBluetooth() {
        if (mBluetoothLeService == null) return;
        mBluetoothLeService.disconnect();
        unbindService(mServiceConnection);
        unregisterReceiver(mGattUpdateReceiver);

    }

    @Override
    protected void onResume(){
        super.onResume();
        if (mBluetoothLeService != null) mBluetoothLeService.connect(selectedDevice.getAddress());
    }

    @Override
    protected void onPause(){
        super.onPause();

        if(thread!=null){
            thread.interrupt();
        }

        mBluetoothLeService.disconnect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(thread!=null){
            thread.interrupt();
        }
        closeBluetooth();
    }

    private void connectToCharacteristics(List<BluetoothGattService> gattList){
        for(BluetoothGattService service: gattList){
            if(service.getUuid().compareTo(UUID.fromString(serviceUUID))==0){
                for(BluetoothGattCharacteristic characteristic:service.getCharacteristics()){
                    if(characteristic.getUuid().compareTo(UUID.fromString(characteristicsUUID))==0){
                        emg_characteristic = characteristic;
                        mBluetoothLeService.sendValue("Notifications and indications enabled",emg_characteristic);
                        return;
                    }
                }
            }
        }
        Log.d(TAG, "Bluetooth Device Incompatible!");
        serverStatus.setText("Device Incompatible! Please connect to another device!");
    }

    private int[] byteToInt(byte[] decoded){
        int iter = (decoded.length-decoded.length%3)/3;                                             //in case received data is not complete
        int[] result_arr = new int[iter*2];

        for(int i=0;i<iter;++i){
            int first = decoded[i*3] &0xff;
            int second = decoded[i*3+1]&0xff;
            int third = decoded[i*3+2]&0xff;
            //Log.println(Log.DEBUG,TAG,"First: "+Integer.toBinaryString(first)+" Second: "+Integer.toBinaryString(second));
            result_arr[i*2] = (first << 4&0b111111110000) | second >>> 4;
            result_arr[i*2+1] = ((second<<8)&0b111100000000) | third;                  //3840 == 0000111100000000; bitmask operation
        }
        data_count += result_arr.length;
        Log.println(Log.INFO,TAG, "Result_arr length: " + result_arr.length);
        Log.println(Log.INFO,TAG,"Total Data Count: " + data_count);
        return result_arr;
    }

    private LineDataSet createSet(){
        LineDataSet set = new LineDataSet(null, "Dynamic EMG Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(1f);
        set.setColor(Color.BLUE);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        return set;
    }

    private void addEntry(int element){
        LineData data = data_chart.getData();

        if(data!=null){
            ILineDataSet set = data.getDataSetByIndex(0);

            if(set==null){
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), element), 0);
            data.notifyDataChanged();
            data_chart.setMaxVisibleValueCount(500);
            data_chart.setVisibleXRange(0,500);
            data_chart.notifyDataSetChanged();
            data_chart.invalidate();
            data_chart.moveViewToX(data.getEntryCount());
        }
    }

    private void updateGraph(int[] arr){
        if(plotData){
            plotData = false;
            for(int element:arr)
                addEntry(element);
        }
    }

    private void startPlot(){
        if(thread!=null){
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    //if(!plotData) break;
                    plotData = true;
                    try {
                        Thread.sleep(10);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    private void setGraph(){
        data_chart = findViewById(R.id.chart1);
        data_chart.getDescription().setEnabled(true);
        data_chart.getDescription().setText("Raw EMG Data Display");
        data_chart.setDragEnabled(false);
        data_chart.setScaleEnabled(false);
        data_chart.setDrawGridBackground(true);
        data_chart.setBackgroundColor(Color.BLACK);
        data_chart.setPinchZoom(false);

        LineData data = new LineData();
        data.setValueTextColor(Color.DKGRAY);
        data_chart.setData(data);
        Legend legend = data_chart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextColor(Color.WHITE);

        XAxis x =  data_chart.getXAxis();
        x.setTextColor(Color.BLACK);
        x.setDrawGridLines(true);//畫X軸線
        x.setPosition(XAxis.XAxisPosition.BOTTOM);//把標籤放底部
        x.setLabelCount(5,true);//設置顯示5個標籤
        //設置X軸標籤內容物
        x.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "No. "+Math.round(value);
            }
        });

        YAxis y = data_chart.getAxisLeft();
        y.setTextColor(Color.BLACK);
        y.setDrawGridLines(true);
        y.setAxisMaximum(5000);
        y.setAxisMinimum(-2000);
        data_chart.getAxisRight().setEnabled(false);
        data_chart.setVisibleXRange(0,50);//
    }
}
