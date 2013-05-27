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
    private UsbDeviceConnection _connectionRead = null;
    private UsbEndpoint _endpointIntrRead = null;
    private UsbDeviceConnection _connectionWrite = null;
    private UsbEndpoint _endpointIntrWrite = null;

    private Thread _waitThread = null;
    private boolean _stopWaitThread = false;

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
    public void onDestroy()
    {
        super.onDestroy();

        _stopWaitThread = true;

        if ( _currentDevice != null )
            teardownDevice(_currentDevice);
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
        Log.d(TAG, message);
        _outputString += message + "\n";
        _outputView.setText(_outputString);
        _outputScrollView.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void appendMessageOnUIThread(String message)
    {
        final String m = message;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendMessage(m);
            }
        });
    }

    private void findUsbDeviceAndConnect()
    {
        if ( _currentDevice == null )
        {
            appendMessage("");
            appendMessage("--------------------------------------------");
            appendMessage("[" + (new Date()).toString() + "]");
            appendMessage("--------------------------------------------");

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

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(_usbReceiver, filter);
    }

    private void setupDevice(UsbDevice device)
    {
        _currentDevice = device;
        _startButton.setEnabled(true);

        appendMessage("setupDevice " + device);
        appendMessage("device has " + device.getInterfaceCount() + " interfaces available");

        if ( device.getInterfaceCount() == 0 )
        {
            appendMessage("device has no interfaces! exiting...");
            return;
        }

        UsbInterface usbInterfaceRead = null;
        UsbInterface usbInterfaceWrite = null;
        UsbEndpoint ep1 = null;
        UsbEndpoint ep2 = null;
        boolean usingSingleInterface = false;

        if ( device.getInterfaceCount() < 2 )
        {
            appendMessage("Attempting to use a single interface for both INPUT and OUTPUT");
            usingSingleInterface = true;
        }

        if ( usingSingleInterface )
        {
            // Using the same interface for reading and writing
            usbInterfaceRead = device.getInterface(0x00);
            usbInterfaceWrite = usbInterfaceRead;
            if (usbInterfaceRead.getEndpointCount() == 2)
            {
                ep1 = usbInterfaceRead.getEndpoint(0);
                ep2 = usbInterfaceRead.getEndpoint(1);

                appendMessage("assigned read interface to ep1 and write interface to ep2");
            }
            else
            {
                appendMessage("we are trying to use a single interface, but the interface does not have 2 endpoints to use!");
                return;
            }
        }
        else
        {
            usbInterfaceRead = device.getInterface(0x00);
            usbInterfaceWrite = device.getInterface(0x01);
            if ((usbInterfaceRead.getEndpointCount() == 1) && (usbInterfaceWrite.getEndpointCount() == 1))
            {
                ep1 = usbInterfaceRead.getEndpoint(0);
                ep2 = usbInterfaceWrite.getEndpoint(0);

                appendMessage("assigned read interface to ep1 and write interface to ep2");
            }
            else
            {
                appendMessage("we are trying to using two interfaces, but the both interfaces do not have a endpoint to use!");
                return;
            }
        }


        if ((ep1 == null) || (ep2 == null))
        {
            appendMessage("Endpoint 1 or 2 is NULL!");
            return;
        }

        // Determine which endpoint is the read, and which is the write
        if ( ep1.getType() == UsbConstants.USB_ENDPOINT_XFER_INT )
        {
            appendMessage("ep1 is of type USB_ENDPOINT_XFER_INT");
        }
        else if ( ep1.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK )
        {
            appendMessage("ep1 is of type USB_ENDPOINT_XFER_BULK");
        }

        if ( ep2.getType() == UsbConstants.USB_ENDPOINT_XFER_INT )
        {
            appendMessage("ep2 is of type USB_ENDPOINT_XFER_INT");
        }
        else if ( ep2.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK )
        {
            appendMessage("ep2 is of type USB_ENDPOINT_XFER_BULK");
        }

        if ( ep1.getType() == UsbConstants.USB_ENDPOINT_XFER_INT || ep1.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK )
        {
            if (ep1.getDirection() == UsbConstants.USB_DIR_IN)
            {
                _endpointIntrRead = ep1;
            }
            else if (ep1.getDirection() == UsbConstants.USB_DIR_OUT)
            {
                _endpointIntrWrite = ep1;
            }
        }
        if ( ep2.getType() == UsbConstants.USB_ENDPOINT_XFER_INT || ep2.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK )
        {
            if (ep2.getDirection() == UsbConstants.USB_DIR_IN)
            {
                _endpointIntrRead = ep2;
            }
            else if (ep2.getDirection() == UsbConstants.USB_DIR_OUT)
            {
                _endpointIntrWrite = ep2;
            }
        }
        if ((_endpointIntrRead == null) || (_endpointIntrWrite == null))
        {
            appendMessage("Unable to get read or write end point!");
            return;
        }

        _connectionRead = _usbManager.openDevice(device);
        _connectionRead.claimInterface(usbInterfaceRead, true);

        if ( usingSingleInterface )
        {
            _connectionWrite = _connectionRead;
        }
        else
        {
            _connectionWrite = _usbManager.openDevice(device);
            _connectionWrite.claimInterface(usbInterfaceWrite, true);
        }

        appendMessage("device has been set up and connected, read: " + _endpointIntrRead.toString() + ", write: " + _endpointIntrWrite.toString());
    }

    private void teardownDevice(UsbDevice device)
    {
        if ( device == _currentDevice )
        {
            appendMessage("tearing down device");

            _connectionRead.close();
            _connectionRead = null;

            _connectionWrite.close();
            _connectionWrite = null;

            _endpointIntrRead = null;
            _endpointIntrWrite = null;

            _stopWaitThread = true;

            _currentDevice = null;
            _startButton.setEnabled(false);
        }
    }

    private void sendConfigAndStartPackets()
    {
        _stopWaitThread = false;

        _waitThread = new Thread(this);
        _waitThread.start();
    }

    @Override
    public void run()
    {
        UsbRequest request = new UsbRequest();
        request.initialize(_connectionWrite, _endpointIntrWrite);

        // config packet
        appendMessageOnUIThread("Sending config packet...");

        Packet configPacket = new Packet(Packet.Type.Config);
        byte[] cb = configPacket.getBuffer();
        ByteBuffer byteBuffer = ByteBuffer.allocate(cb.length);
        byteBuffer.put(cb, 0, cb.length);

        // queue a OUT request
        boolean response = request.queue(byteBuffer, byteBuffer.capacity());
        if (_connectionWrite.requestWait() == request)
            appendMessageOnUIThread("sent config packet, queue response: " + response);
        else
            appendMessageOnUIThread("requestWait() failed, queue response: " + response);


        // wait 2 seconds?
        try { Thread.sleep(2000); }
        catch (InterruptedException e) { }


        // start packet
        appendMessageOnUIThread("Sending start packet...");
        Packet startPacket = new Packet(Packet.Type.Start);
        byte[] sb = startPacket.getBuffer();
        byteBuffer = ByteBuffer.allocate(sb.length);
        byteBuffer.put(sb, 0, sb.length);

        // queue a OUT request
        response = request.queue(byteBuffer, byteBuffer.capacity());
        if (_connectionWrite.requestWait() == request)
            appendMessageOnUIThread("sent start packet, queue response: " + response);
        else
            appendMessageOnUIThread("requestWait() failed, queue response: " + response);


        // wait 2 seconds?
        try { Thread.sleep(2000); }
        catch (InterruptedException e) { }


        UsbRequest readRequest = new UsbRequest();
        readRequest.initialize(_connectionRead, _endpointIntrRead);

        int maxPacketSize = _endpointIntrRead.getMaxPacketSize();
        ByteBuffer buffer = ByteBuffer.allocate(maxPacketSize);

        while ( !_stopWaitThread )
        {
            appendMessageOnUIThread("requesting response from read endpoint");

            // queue a IN request on the interrupt endpoint
            readRequest.queue(buffer, maxPacketSize);

            // wait for it to complete
            _connectionRead.requestWait();

            appendMessageOnUIThread("buffer received: " + buffer.array().toString());

            try { Thread.sleep(100); }
            catch (InterruptedException e) { }
        }

        if ( _stopWaitThread )
            appendMessageOnUIThread("wait thread stopping!");
    }
}
