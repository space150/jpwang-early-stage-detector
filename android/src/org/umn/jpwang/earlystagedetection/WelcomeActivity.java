package org.umn.jpwang.earlystagedetection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

public class WelcomeActivity extends Activity
{
    private static final String TAG = "EarlyStageDetection::WelcomeActivity";

    private String _outputString = "";
    private TextView _outputView;
    private ScrollView _outputScrollView;

    private Button _startButton;

    private UsbManager _usbManager;
    private UsbSerialDriver _driver;

    private final ExecutorService _executor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager _serialIoManager;
    private final SerialInputOutputManager.Listener _serialListener = new SerialInputOutputManager.Listener()
    {
        @Override
        public void onRunError(Exception e)
        {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data)
        {
            WelcomeActivity.this.runOnUiThread(new Runnable()
            {
                @Override
                public void run() {
                    WelcomeActivity.this.updateReceivedData(data);
                }
            });
        }
    };

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
                Thread thread = new Thread()
                {
                    @Override
                    public void run() {
                        try
                        {
                            Packet configPacket = new Packet(Packet.Type.Config);
                            int numBytesWritten = _driver.write(configPacket.getBuffer(), 1000);
                            message("wrote config packet, " + numBytesWritten + " bytes...");

                            SystemClock.sleep(2000);

                            Packet startPacket = new Packet(Packet.Type.Start);
                            numBytesWritten = _driver.write(startPacket.getBuffer(), 1000);
                            message("wrote start packet, " + numBytesWritten + " bytes...");
                        }
                        catch (IOException e)
                        {
                            message("failed to write packets: " + e.getMessage());
                        }
                    }
                };
                thread.start();
            }
        });
        _startButton.setEnabled(false);

        _outputView = (TextView)findViewById(R.id.output_view);
        _outputScrollView = (ScrollView)findViewById(R.id.output_scrollview);

        _usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if ( _driver == null )
        {
        List<UsbSerialDriver> drivers = UsbSerialProber.findAllDevices(_usbManager);
        if ( drivers.size() > 0 )
        {
            _driver = drivers.get(0);
            message("Found " + drivers.size() + " compatible drivers, using the first one: " + _driver);
        }
        else
            message("No compatible drivers found!");
        }

        Log.d(TAG, "Resumed, sDriver=" + _driver);
        if ( _driver == null )
        {
            message("No serial device.");
        }
        else
        {
            try
            {
                _driver.open();
                _driver.setParameters(115200, 8, UsbSerialDriver.STOPBITS_1, UsbSerialDriver.PARITY_NONE);
            }
            catch (IOException e)
            {
                message("Error opening device: " + e.getMessage());
                try
                {
                    _driver.close();
                }
                catch (IOException e2)
                {
                    // Ignore.
                }
                _driver = null;
                return;
            }
            message("Serial device: " + _driver.getClass().getSimpleName());
        }

        onDeviceStateChange();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        stopIoManager();

        if ( _driver != null )
        {
            try {
                _driver.close();
            } catch (IOException e) {
                // Ignore.
            }
            _driver = null;
        }
        finish();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
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

    public void message(final String message)
    {
        Log.d(TAG, message);
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                _outputString += message + "\n";
                _outputView.setText(_outputString);
                _outputScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void stopIoManager()
    {
        if ( _serialIoManager != null )
        {
            Log.i(TAG, "Stopping io manager ..");
            _serialIoManager.stop();
            _serialIoManager = null;
        }
    }

    private void startIoManager()
    {
        if ( _driver != null )
        {
            Log.i(TAG, "Starting io manager ..");
            _serialIoManager = new SerialInputOutputManager(_driver, _serialListener);
            _executor.submit(_serialIoManager);
        }
    }

    private void onDeviceStateChange()
    {
        stopIoManager();
        startIoManager();

        _startButton.setEnabled(_driver != null);
    }

    private void updateReceivedData(byte[] data)
    {
        message("Read " + data.length + " bytes: " + HexDump.toHexString(data));
    }
}
