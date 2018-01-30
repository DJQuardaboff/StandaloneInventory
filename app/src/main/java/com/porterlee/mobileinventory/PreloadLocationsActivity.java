package com.porterlee.mobileinventory;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import java.io.File;

public class PreloadLocationsActivity extends AppCompatActivity {
    private static final File OUTPUT_FILE = new File(Environment.getExternalStorageDirectory(), "Download/preloadinfo.txt");
    private static final String TAG = PreloadLocationsActivity.class.getSimpleName();
    //todo finish

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preload_locations_layout);
        //todo finish
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preload_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_inventory:
                startActivity(new Intent(this, InventoryActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class PreloadLocationViewHolder extends RecyclerView.ViewHolder {
        //todo finish

        PreloadLocationViewHolder(final View itemView) {
            super(itemView);
            //todo finish
        }

        void bindViews(Cursor cursor) {
            //todo finish
        }
    }
}

