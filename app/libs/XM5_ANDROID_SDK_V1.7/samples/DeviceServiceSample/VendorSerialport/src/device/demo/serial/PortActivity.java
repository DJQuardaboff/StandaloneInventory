package device.demo.serial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class PortActivity extends ListActivity {
	private static final String TAG = PortActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        
        setListAdapter(new SimpleAdapter(this, getList(),
                android.R.layout.simple_list_item_1, new String[] { "title" },
                new int[] { android.R.id.text1 }));
        getListView().setTextFilterEnabled(true);
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_setup:
			startActivity(new Intent(this, SetupPreferences.class));
			return true;
		case R.id.action_info:
			openInfo();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void openInfo() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            }
        });

        String version = getString(R.string.msg_version_suffix);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pi != null) {
                version = pi.versionName;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        alert.setMessage(getString(R.string.application_label) + " v" + version);
        alert.show();
    }
    
    protected List<Map<String, Object>> getList() {
    	List<Map<String, Object>> myList = new ArrayList<Map<String, Object>>();
    	
    	addItem(myList, getString(R.string.title_console), new ComponentName(this, ConsoleActivity.class.getName()));
    	addItem(myList, getString(R.string.title_oem615), new ComponentName(this, OEM615Activity.class.getName()));
    	addItem(myList, getString(R.string.title_rfid), new ComponentName(this, RFIDActivity.class.getName()));
    	addItem(myList, "Quit", null);
    	
    	return myList;
    }
    
    protected void addItem(List<Map<String, Object>> data, String name, ComponentName component) {
        Map<String, Object> temp = new HashMap<String, Object>();
        temp.put("title", name);
        temp.put("component", component);
        data.add(temp);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Map<String, Object> map = (Map<String, Object>)l.getItemAtPosition(position);
        ComponentName component = (ComponentName) map.get("component");
        if (component == null) {
        	finish();
        } else {
	        Intent intent = new Intent();
	        intent.setComponent(component);
	        startActivity(intent);
        }
    }
}
