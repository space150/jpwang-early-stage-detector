package org.umn.jpwang.earlystagedetection;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.*;

public class WelcomeActivity extends Activity
{
    private static final String TAG = "EarlyStageDetection";
    private static final String ACTION_USB_PERMISSION = "org.umn.jpwang.earlystagedetection.USB_PERMISSION";

    private String _outputString = "";
    private TextView _outputView;
    private ScrollView _outputScrollView;

    private Button _startButton;

    private UsbManager _usbManager;
    private BroadcastReceiver _usbReceiver;
    private PendingIntent _usbPermissionIntent;
    private UsbDevice _currentDevice = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        _startButton = (Button)findViewById(R.id.start_button);
        _startButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                sendConfigAndStartPackets();
            }
        });

        _outputView = (TextView)findViewById(R.id.output_view);
        _outputScrollView = (ScrollView)findViewById(R.id.output_scrollview);

        setupUsbPermissionHandlers();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        findUsbDeviceAndConnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater i = getMenuInflater();
        i.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_email:
                sendEmail();
                break;
            default:
                break;
        }
        return true;
    }

    private void sendEmail()
    {
        String[] recipients = new String[] { "shawn.roske@space150.com" };

        Intent i = new Intent(android.content.Intent.ACTION_SEND);
        i.putExtra(android.content.Intent.EXTRA_EMAIL, recipients);
        i.putExtra(android.content.Intent.EXTRA_SUBJECT, "EarlyStageDetection - Debug Log");
        i.putExtra(android.content.Intent.EXTRA_TEXT, _outputString);
        i.setType("text/plain");

        startActivity(Intent.createChooser(i, "Email Log with:"));
    }

    /*private void testAppendingText()
    {
        Timer t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appendMessage((new Date()).toString());
                    }
                });
            }
        }, 1000, 1000);
    }*/

    private void appendMessage(String message)
    {
        _outputString += message + "\n";
        _outputView.setText(_outputString);
        _outputScrollView.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void findUsbDeviceAndConnect()
    {
        if ( _currentDevice == null )
        {
            appendMessage("----------------------");
            appendMessage("[" + (new Date()).toString() + "] Searching for USB devices...");

            // try to find a USB device to connect to
            HashMap<String, UsbDevice> deviceList = _usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            boolean foundDevice = false;
            while( deviceIterator.hasNext() )
            {
                UsbDevice device = deviceIterator.next();
                appendMessage("Found device: " + device.getDeviceName() + " (" + device.getDeviceId() + "), requesting permission!");
                foundDevice = true;

                _usbManager.requestPermission(device, _usbPermissionIntent);
            }

            if ( !foundDevice )
                appendMessage("No USB devices found!");
        }
    }

    private void setupUsbPermissionHandlers()
    {
        _usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        _usbReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ( ACTION_USB_PERMISSION.equals(action) )
                {
                    synchronized ( this )
                    {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if ( intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) )
                        {
                            if( device != null )
                                setupDevice(device);
                        }
                        else
                        {
                            Log.d(TAG, "permission denied for device " + device);
                        }
                    }
                }
                else if ( UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) )
                {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if ( device != null )
                    {
                        appendMessage("Attached! device: " + device.getDeviceName() + " (" + device.getDeviceId() + "), requesting permission!");
                        _usbManager.requestPermission(device, _usbPermissionIntent);
                    }
                }
                else if ( UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) )
                {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if ( device != null )
                    {
                        appendMessage("Detached! device: " + device.getDeviceName() + " (" + device.getDeviceId() + ")");
                        teardownDevice(device);
                    }
                }
            }
        };

        _usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(_usbReceiver, filter);
    }

    private void setupDevice(UsbDevice device)
    {
        _currentDevice = device;
        _startButton.setEnabled(true);
    }

    private void teardownDevice(UsbDevice device)
    {
        if ( device == _currentDevice )
        {
            _currentDevice = null;
            _startButton.setEnabled(false);
        }
    }

    private void sendConfigAndStartPackets()
    {
        // connect
        UsbInterface intf = _currentDevice.getInterface(0);
        UsbEndpoint endpoint = intf.getEndpoint(0);
        UsbDeviceConnection connection = _usbManager.openDevice(_currentDevice);
        connection.claimInterface(intf, true);

        // config packet
        Packet configPacket = new Packet(Packet.Type.Config);
        byte[] buffer = configPacket.getBuffer();
        connection.bulkTransfer(endpoint, buffer, buffer.length, 0);

        // start packet
        Packet startPacket = new Packet(Packet.Type.Start);
        buffer = startPacket.getBuffer();
        connection.bulkTransfer(endpoint, buffer, buffer.length, 0);

        // now listen to data coming down the wire
        // TODO

        connection.close();
    }
}
