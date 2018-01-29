package com.porterlee.mobileinventory;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

public class PreloadActivity extends AppCompatActivity {
    private static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/invinfo.txt");
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inventory_layout);

        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + InventoryDatabase.FILE_NAME, null);
        //db.execSQL("DROP TABLE barcodes");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadDatabase.ItemTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE barcodes");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadDatabase.ItemTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE barcodes");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadDatabase.ItemTable.TABLE_CREATION);

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public int getItemCount() {
                return 0;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return null;
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {

            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        itemRecyclerView.setItemAnimator(new DefaultItemAnimator());
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

    class SimpleViewHolder extends RecyclerView.ViewHolder {
        private ProgressBar progressLoading;
        private TextView itemBarcode;
        private TextView itemDescription;
        private ImageButton expandedMenu;

        SimpleViewHolder(final View itemView) {
            super(itemView);
            progressLoading = itemView.findViewById(R.id.progressbar_waiting);
            itemBarcode = itemView.findViewById(R.id.barcode_text_view);
            itemDescription = itemView.findViewById(R.id.location_text_view);
            expandedMenu = itemView.findViewById(R.id.menu_button);
        }

        void bindViews(Cursor cursor) {
            cursor.moveToFirst();
            String barcode = "";//cursor.getString(cursor.getColumnIndex(InventoryDatabase.ItemTable.Keys.BARCODE));
            String description = "";//cursor.getString(cursor.getColumnIndex(InventoryDatabase.ItemTable.Keys.DESCRIPTION));
            cursor.close();
            if (true) {//ready) {
                progressLoading.setVisibility(View.GONE);
                if (barcode != null) {
                    itemBarcode.setText(barcode);
                    itemBarcode.setVisibility(View.VISIBLE);
                } else itemBarcode.setVisibility(View.GONE);
                if (description != null) {
                    itemDescription.setText(description);
                    itemDescription.setVisibility(View.VISIBLE);
                } else itemDescription.setVisibility(View.GONE);
                expandedMenu.setVisibility(View.VISIBLE);
            } else {
                progressLoading.setVisibility(View.VISIBLE);
                itemBarcode.setVisibility(View.GONE);
                itemDescription.setVisibility(View.GONE);
                expandedMenu.setVisibility(View.GONE);
            }
        }
    }
}

