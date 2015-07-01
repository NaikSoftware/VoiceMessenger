package ua.naiksoftware.messagereceiver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

public class MainActivity extends Activity {
	
	private Button btn;
	private boolean running;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		btn = (Button) findViewById(R.id.mainButton);
		btn.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (running) {
						stopService(new Intent(MainActivity.this, ReceiverService.class));
					} else {
						startService(new Intent(MainActivity.this, ReceiverService.class));
					}
				}
			});
		update();
    }

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(broadcast, new IntentFilter(ReceiverService.ACTION_UPDATE_RECEIVER));
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(broadcast);
	}
	
	private void update() {
		if (running = ReceiverService.running()) {
			btn.setText("Stop");
		} else {
			btn.setText("Start");
		}
	}
	
	private BroadcastReceiver broadcast = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			update();
		}
	};
}
