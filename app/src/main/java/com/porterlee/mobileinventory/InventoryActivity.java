package com.porterlee.mobileinventory;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import com.porterlee.mobileinventory.InventoryDatabase.BarcodesTable;
import com.porterlee.mobileinventory.InventoryDatabase.LocationsTable;

public class InventoryActivity extends AppCompatActivity {
    private static final File OUTPUT_FILE = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/invinfo.txt");
    private static final String dateFormat = "yyyy/MM/dd kk:mm:ss";
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    private RecyclerView.ItemAnimator itemRecyclerAnimator;
    //private ScannerService scannerService;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + InventoryDatabase.FILE_NAME, null);
        //db.execSQL("DROP TABLE " + BarcodesTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + BarcodesTable.TABLE_CREATION);

        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + InventoryDatabase.FILE_NAME, null);
        //db.execSQL("DROP TABLE " + InventoryDatabase.LocationsTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + LocationsTable.TABLE_CREATION);

        Button randomScanButton = findViewById(R.id.random_scan_button);
        randomScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                randomScan(v);
            }
        });

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                Cursor cursor = db.rawQuery("SELECT " + BarcodesTable.Keys.ID + " FROM " + BarcodesTable.NAME + " LIMIT 1 OFFSET " + (itemRecyclerAdapter.getItemCount() - 1 - i), null);
                cursor.moveToFirst();
                long id = cursor.getLong(0);
                System.out.println(id);
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + BarcodesTable.NAME,null);
                cursor.moveToFirst();
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_item_view, parent, false);
                return new SimpleViewHolder(itemLayoutView);
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                ((SimpleViewHolder) holder).expandedMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(InventoryActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.popup_menu, popup.getMenu());
                        popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                removeBarcodeItem(holder.getAdapterPosition());
                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                Cursor cursor = db.rawQuery("SELECT * FROM " + BarcodesTable.NAME + " LIMIT 1 OFFSET " + (itemRecyclerAdapter.getItemCount() - 1 - position),null);
                ((SimpleViewHolder) holder).bindViews(cursor);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemRecyclerAnimator);
        itemRecyclerView.getLayoutManager().setAutoMeasureEnabled(false);
        for (int i = 0; i < 583; i++) {
            randomScan(null);
        }
        itemRecyclerAdapter.notifyDataSetChanged();
        System.out.println(DateFormat.format("yyyy/MM/dd kk:mm:ss", System.currentTimeMillis()));
        //scannerService = new ScannerService(this);
        //DecodeResult decodeResult = new DecodeResult();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inventory_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(true);
                builder.setTitle("Clear Inventory");
                builder.setMessage("Are you sure you want to clear this inventory?");
                builder.setNegativeButton("no", null);
                builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (int i = itemRecyclerAdapter.getItemCount() - 1; i >= 0; i--) {
                            removeBarcodeItem(i);
                        }
                        Toast.makeText(InventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                    }
                });
                builder.create().show();
                return true;
            case R.id.action_save_to_file:
                Toast.makeText(this, saveToFile() ? "ill get to it" : "Error saving to file", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_preload:
                startActivity(new Intent(this, PreloadActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean saveToFile() {
        try {
            if (OUTPUT_FILE.exists() && !OUTPUT_FILE.delete()) return false;
            if (!OUTPUT_FILE.createNewFile()) return false;
            if (!OUTPUT_FILE.canWrite()) return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        Cursor cursor = db.rawQuery("SELECT " + BarcodesTable.Keys.BARCODE + ", " + BarcodesTable.Keys.LOCATION_ID + ", " + BarcodesTable.Keys.DATE_TIME + " FROM " + BarcodesTable.NAME + " WHERE", null);
        int barcodeIndex = cursor.getColumnIndex(BarcodesTable.Keys.BARCODE);
        int locationIdIndex = cursor.getColumnIndex(BarcodesTable.Keys.LOCATION_ID);
        int dateTimeIndex = cursor.getColumnIndex(BarcodesTable.Keys.DATE_TIME);
        cursor.moveToFirst();
        long currentLocation = -1;

        while (!cursor.isAfterLast()) {
            if (cursor.getLong(locationIdIndex) != currentLocation) {

            }
            cursor.moveToNext();
        }
        cursor.close();

        return true;
    }

    public void scanBarcode(String barcode) {
        if (barcode.startsWith("V")) {
            addLocation(barcode);
        } else if (barcode.startsWith("e")) {
            addBarcodeItem(barcode);
        }
    }

    public void addLocation(String barcode) {
        ContentValues newLocation = new ContentValues();
        newLocation.put(LocationsTable.Keys.BARCODE, barcode);
        newLocation.put(LocationsTable.Keys.DATE_TIME, System.currentTimeMillis());
        ((TextView) findViewById(R.id.current_location)).setText(barcode);

        if (db.insert(LocationsTable.NAME, null, newLocation) != -1) Toast.makeText(this, "Error adding location to the database", Toast.LENGTH_SHORT).show();
    }

    public long getLastLocationId() {
        Cursor cursor = db.rawQuery("SELECT " + LocationsTable.Keys.ID + " FROM " + LocationsTable.NAME + " ORDER BY " + LocationsTable.Keys.ID + " DESC LIMIT 1;", null);
        if (cursor.getCount() <= 0) {
            cursor.close();
            return -1;
        }

        cursor.moveToFirst();
        long id = cursor.getLong(0);
        cursor.close();
        return id;
    }

    public void removeBarcodeItem(int index) {
        if (db.delete(BarcodesTable.NAME, BarcodesTable.Keys.ID + " in ( SELECT " + BarcodesTable.Keys.ID + " FROM " + BarcodesTable.NAME + " LIMIT 1 OFFSET " + (itemRecyclerAdapter.getItemCount() - 1 - index) + ")", null) > 0) {
            itemRecyclerAdapter.notifyItemRemoved(index);
            itemRecyclerAdapter.notifyItemRangeChanged(index, itemRecyclerAdapter.getItemCount() - index);
            ((TextView) findViewById(R.id.total_items)).setText(String.valueOf(itemRecyclerAdapter.getItemCount()));
        } else Toast.makeText(InventoryActivity.this, "Error removing item from the database", Toast.LENGTH_SHORT).show();
    }

    public void addBarcodeItem(@NonNull String barcode) {
        long lastLocationId = getLastLocationId();
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }
        ContentValues newItem = new ContentValues();
        newItem.put(BarcodesTable.Keys.BARCODE, barcode);
        newItem.put(BarcodesTable.Keys.LOCATION_ID, lastLocationId);
        newItem.put(BarcodesTable.Keys.DATE_TIME, System.currentTimeMillis());

        if (db.insert(BarcodesTable.NAME, null, newItem) == -1) {
            Toast.makeText(this, "Error adding item to the database", Toast.LENGTH_SHORT).show();
            return;
        }

        itemRecyclerAdapter.notifyItemInserted(0);
        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        ((TextView) findViewById(R.id.last_scan)).setText(barcode);
        ((TextView) findViewById(R.id.total_items)).setText(String.valueOf(itemRecyclerAdapter.getItemCount()));
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

    public String formatDate(long millis) {
        return DateFormat.format(dateFormat, millis).toString();
    }

    private static final String uppercaseCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String alphaNumericCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public void randomScan(@Nullable View view) {
        Random r = new Random();
        boolean isLocation = r.nextInt(5) == 0;
        String barcode = isLocation ? "VAN " : "e1LAB00";

        for (int i = 0; i < 3; i++){
            if (isLocation) {
                barcode = barcode.concat(String.valueOf(uppercaseCharacters.charAt(r.nextInt(uppercaseCharacters.length()))));
            } else {
                barcode = barcode.concat(String.valueOf(alphaNumericCharacters.charAt(r.nextInt(alphaNumericCharacters.length()))));
            }
        }

        scanBarcode(barcode);
    }

    class SimpleViewHolder extends RecyclerView.ViewHolder {
        private ProgressBar progressLoading;
        private TextView itemBarcode;
        private TextView itemDescription;
        private ImageButton expandedMenu;

        SimpleViewHolder(final View itemView) {
            super(itemView);
            progressLoading = itemView.findViewById(R.id.progress_loading);
            itemBarcode = itemView.findViewById(R.id.item_barcode);
            itemDescription = itemView.findViewById(R.id.item_description);
            expandedMenu = itemView.findViewById(R.id.menu_button);
        }

        void bindViews(Cursor cursor) {
            cursor.moveToFirst();
            String barcode = cursor.getString(cursor.getColumnIndex(BarcodesTable.Keys.BARCODE));
            String description = cursor.getString(cursor.getColumnIndex(BarcodesTable.Keys.DESCRIPTION));
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

