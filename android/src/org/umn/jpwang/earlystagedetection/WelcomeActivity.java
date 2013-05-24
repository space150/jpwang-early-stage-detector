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

import java.nio.ByteBuffer;
import java.util.*;

public class WelcomeActivity extends Activity implements Runnable
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
    private UsbDeviceConnection _connection;
    private UsbEndpoint _endpointIntr;

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
            appendMessage("[" + (new Date()).toString() + "]");
            appendMessage("----------------------");

            appendMessage("Searching for USB devices...");

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
                        appendMessage("permission callback recieved!");

                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if ( intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) )
                        {
                            appendMessage("permission granted");

                            if( device != null )
                                setupDevice(device);
                            else
                                appendMessage("but the device was nil?");
                        }
                        else
                        {
                            appendMessage("permission denied for device " + device);
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

        appendMessage("setupDevice " + device);
        if (device.getInterfaceCount() != 1)
        {
            appendMessage("could not find interface");
            return;
        }

        UsbInterface intf = device.getInterface(0);
        // device should have one endpoint
        if (intf.getEndpointCount() != 1)
        {
            appendMessage("could not find endpoint");
            return;
        }
        // endpoint should be of type interrupt
        UsbEndpoint ep = intf.getEndpoint(0);
        if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_INT)
        {
            appendMessage("endpoint is not interrupt type");
            return;
        }
        _endpointIntr = ep;

        if ( device != null )
        {
            UsbDeviceConnection connection = _usbManager.openDevice(device);
            if ( connection != null && connection.claimInterface(intf, true) )
            {
                appendMessage("open SUCCESS");
                _connection = connection;
            }
            else
            {
                appendMessage("open FAIL");
                _connection = null;
            }
        }
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
        // start up the thread that will wait for data
        Thread thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run()
    {
        // config packet
        appendMessage("Sending config packet...");
        Packet configPacket = new Packet(Packet.Type.Config);
        byte[] buf = configPacket.getBuffer();
        _connection.bulkTransfer(_endpointIntr, buf, buf.length, 0);

        // wait 2 seconds?
        try { Thread.sleep(2000); }
        catch (InterruptedException e) { }

        // start packet
        appendMessage("Sending start packet...");
        Packet startPacket = new Packet(Packet.Type.Start);
        buf = startPacket.getBuffer();
        _connection.bulkTransfer(_endpointIntr, buf, buf.length, 0);

        // wait 2 seconds?
        try { Thread.sleep(2000); }
        catch (InterruptedException e) { }

        ByteBuffer buffer = ByteBuffer.allocate(_endpointIntr.getMaxPacketSize());
        UsbRequest request = new UsbRequest();
        request.initialize(_connection, _endpointIntr);

        while ( true )
        {
            // queue a IN request on the interrupt endpoint
            request.queue(buffer, _endpointIntr.getMaxPacketSize());

            // wait for it to complete
            _connection.requestWait();

            appendMessage("buffer received: " + buffer.toString());

            try { Thread.sleep(100); }
            catch (InterruptedException e) { }
        }
    }
}
