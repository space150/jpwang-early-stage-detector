package org.umn.jpwang.earlystagedetection;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class WelcomeActivity extends Activity implements ConnectionManagerDelegate
{
    private static final String TAG = "EarlyStageDetection::WelcomeActivity";

    private String _outputString = "";
    private TextView _outputView;
    private ScrollView _outputScrollView;

    private Button _startButton;

    private ConnectionManager _connectionManager;


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
                _connectionManager.startProtocol();
            }
        });

        _outputView = (TextView)findViewById(R.id.output_view);
        _outputScrollView = (ScrollView)findViewById(R.id.output_scrollview);

        _connectionManager = new ConnectionManager(this, this);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        _connectionManager.findDevices();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        _connectionManager.destroy();
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

    @Override
    public void connected(boolean success)
    {
        Log.d(TAG, "connected, enabling start button");
        _startButton.setEnabled(success);
    }

    @Override
    public void disconnected()
    {
        Log.d(TAG, "disconnected, disabling start button");
        _startButton.setEnabled(false);
    }

    @Override
    public void message(String message)
    {
        final String m = message;
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                _outputString += m + "\n";
                _outputView.setText(_outputString);
                _outputScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }
}
