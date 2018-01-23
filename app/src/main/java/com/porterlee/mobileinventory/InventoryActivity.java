package com.porterlee.mobileinventory;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
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
import java.util.Random;

public class InventoryActivity extends AppCompatActivity {
    private static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/invinfo.txt");
    private String currentLocation;
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
        //db.execSQL("DROP TABLE " + InventoryDatabase.BarcodesTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + InventoryDatabase.BarcodesTable.TABLE_CREATION);

        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + InventoryDatabase.FILE_NAME, null);
        //db.execSQL("DROP TABLE " + InventoryDatabase.LocationsTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + InventoryDatabase.LocationsTable.TABLE_CREATION);

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
                Cursor cursor = db.rawQuery("SELECT " + InventoryDatabase.BarcodesTable.Keys.ID + " FROM " + InventoryDatabase.BarcodesTable.NAME + " LIMIT 1 OFFSET " + (itemRecyclerAdapter.getItemCount() - 1 - i), null);
                cursor.moveToFirst();
                long id = cursor.getLong(0);
                System.out.println(id);
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + InventoryDatabase.BarcodesTable.NAME,null);
                cursor.moveToFirst();
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
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
                Cursor cursor = db.rawQuery("SELECT * FROM " + InventoryDatabase.BarcodesTable.NAME + " LIMIT 1 OFFSET " + (itemRecyclerAdapter.getItemCount() - 1 - position),null);
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
                builder.setTitle("Remove All");
                builder.setMessage("Are you sure you want to remove all items?");
                builder.setNegativeButton("no", null);
                builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (int i = itemRecyclerAdapter.getItemCount() - 1; i >= 0; i--) {
                            if (!removeBarcodeItem(i)) Toast.makeText(InventoryActivity.this, "Error removing item", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                builder.create().show();
                return true;
            case R.id.action_save_to_file:
                return true;
            case R.id.action_preload:
                startActivity(new Intent(this, PreloadActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void scanBarcode(String barcode) {
        if (barcode.startsWith("V")) addLocation(barcode); else if (barcode.startsWith("e")) addBarcodeItem(barcode, null);
    }

    public boolean addLocation(String barcode) {
        return true;
    }

    public long getLastLocationId() {
        Cursor cursor = db.rawQuery("SELECT " + InventoryDatabase.LocationsTable.Keys.ID + " FROM " + InventoryDatabase.LocationsTable.NAME + " ORDER BY " + InventoryDatabase.LocationsTable.Keys.ID + " DESC LIMIT 1;", null);
        cursor.moveToFirst();
        long id = cursor.getLong(0);
        cursor.close();
        return id;
    }

    public boolean removeBarcodeItem(int index) {
        if (db.delete(InventoryDatabase.BarcodesTable.NAME, InventoryDatabase.BarcodesTable.Keys.ID + " in ( SELECT " + InventoryDatabase.BarcodesTable.Keys.ID + " FROM " + InventoryDatabase.BarcodesTable.NAME + " LIMIT 1 OFFSET " + (itemRecyclerAdapter.getItemCount() - 1 - index) + ")", null) > 0) {
            itemRecyclerAdapter.notifyItemRemoved(index);
            itemRecyclerAdapter.notifyItemRangeChanged(index, itemRecyclerAdapter.getItemCount() - index);
        } else return false;
        return true;
    }

    public boolean addBarcodeItem(@NonNull String barcode, @Nullable String description) {
        ContentValues values = new ContentValues();
        values.put(InventoryDatabase.BarcodesTable.Keys.BARCODE, barcode);
        values.put(InventoryDatabase.BarcodesTable.Keys.LOCATION_ID, getLastLocationId());
        values.put(InventoryDatabase.BarcodesTable.Keys.DESCRIPTION, description);
        values.put(InventoryDatabase.BarcodesTable.Keys.DATE_TIME, description);

        if (db.insert(InventoryDatabase.BarcodesTable.NAME, null, values) == -1) return false;

        itemRecyclerAdapter.notifyItemInserted(0);
        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);
        return true;
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

    public void randomScan(View view) {
        if (!addBarcodeItem("tnyc000" + new Random().nextInt(0xffff), null)) Toast.makeText(this, "Error adding item to the database", Toast.LENGTH_SHORT).show();
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
            String barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BarcodesTable.Keys.BARCODE));
            String description = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BarcodesTable.Keys.DESCRIPTION));
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

