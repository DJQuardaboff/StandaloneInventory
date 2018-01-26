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
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

import com.porterlee.mobileinventory.InventoryDatabase.ItemTable;
import com.porterlee.mobileinventory.InventoryDatabase.ContainerTable;
import com.porterlee.mobileinventory.InventoryDatabase.LocationTable;

public class InventoryActivity extends AppCompatActivity {
    private static final File OUTPUT_FILE = new File(Environment.getExternalStorageDirectory(), "Download/invinfo.txt");
    private static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final int maxItemHistoryIncrease = 25;
    private static int maxItemHistory = maxItemHistoryIncrease;
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
        db.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ItemTable.TABLE_CREATION);

        db.execSQL("DROP TABLE IF EXISTS " + ContainerTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ContainerTable.TABLE_CREATION);

        db.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + LocationTable.TABLE_CREATION);

        final EditText barcodeField = findViewById(R.id.barcode_field);
        this.<EditText>findViewById(R.id.barcode_field).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                String barcode = s.toString();
                if (barcode.length() >= 4) {
                    if (barcode.equals(">><<")) {
                        if (barcodeField != null) barcodeField.setText("");
                    } else if ((barcode.startsWith(">>")) && (barcode.endsWith("<<"))) {
                        if (barcodeField != null) barcodeField.setText("");
                        barcode = barcode.substring(2);
                        barcode = barcode.substring(0, barcode.length() - 2);
                        scanBarcode(barcode);
                    } else if (barcode.endsWith("<<")) {
                        if (barcodeField != null) barcodeField.setText("");
                    }
                }
            }
        });

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                Cursor cursor = db.rawQuery("SELECT " + InventoryDatabase.ID + " FROM " + ItemTable.NAME + ", " + ContainerTable.NAME + " ORDER BY " + InventoryDatabase.DATE_TIME + " LIMIT 1 OFFSET " + i, null);
                cursor.moveToFirst();
                long id = cursor.getLong(0);
                System.out.println(id);
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                int count = InventoryActivity.this.getItemCount() + getContainerCount();
                count = Math.min(count, maxItemHistory);
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
                                Cursor cursor = db.rawQuery("SELECT " + InventoryDatabase.BARCODE + " FROM " + ItemTable.NAME + ", " + ContainerTable.NAME + " WHERE " + InventoryDatabase.ID + " = " + itemRecyclerAdapter.getItemId(holder.getAdapterPosition()), null);
                                cursor.moveToFirst();
                                String barcode = cursor.getString(0);
                                if (isContainer(barcode)) removeBarcodeContainer(holder.getAdapterPosition());
                                if (isItem(barcode)) removeBarcodeItem(holder.getAdapterPosition());
                                updateInfo();
                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                Cursor cursor = db.rawQuery("SELECT * FROM " + ItemTable.NAME + ", " + ContainerTable.NAME + " ORDER BY " + InventoryDatabase.DATE_TIME + " DESC LIMIT 1 OFFSET " + position, null);
                ((SimpleViewHolder) holder).bindViews(cursor);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        itemRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1) && itemRecyclerAdapter.getItemCount() == maxItemHistory) {
                    maxItemHistory += maxItemHistoryIncrease;
                    itemRecyclerAdapter.notifyDataSetChanged();
                }
            }
        });
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemRecyclerAnimator);
        //for (int i = 0; i < 10; i++) {
            //randomScan(null);
        //}
        itemRecyclerAdapter.notifyDataSetChanged();
        updateInfo();
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
                        //for (int i = getAllCount() - 1; i >= 0; i--) {
                            //removeBarcodeItem(i);
                        //}
                        db.delete(ItemTable.NAME, null, null);
                        db.delete(ContainerTable.NAME, null, null);
                        db.delete(LocationTable.NAME, null, null);
                        itemRecyclerAdapter.notifyDataSetChanged();
                        itemRecyclerAdapter.notifyItemRangeRemoved(0, itemRecyclerAdapter.getItemCount());
                        updateInfo();
                        Toast.makeText(InventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                    }
                });
                builder.create().show();
                return true;
            case R.id.action_save_to_file:
                Toast.makeText(this, saveToFile() ? "Saved to file" : "Error saving to file", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_preload:
                startActivity(new Intent(this, PreloadActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public int getItemCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME,null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public int getContainerCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ContainerTable.NAME,null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public int getLocationCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + LocationTable.NAME,null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public void updateInfo() {
        Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + InventoryDatabase.DATE_TIME + " DESC LIMIT 5", null);
        cursor.moveToFirst();
        if (cursor.getCount() > 0) ((TextView) findViewById(R.id.current_location)).setText(cursor.getString(0)); else ((TextView) findViewById(R.id.current_location)).setText("-");
        cursor.close();
        cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + ", " + ContainerTable.Keys.BARCODE + " FROM " + ItemTable.NAME + ", " + ContainerTable.NAME + " ORDER BY " + ItemTable.Keys.DATE_TIME + " DESC, " + ContainerTable.Keys.DATE_TIME + " DESC LIMIT 1", null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            System.out.print(cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE)) + " - ");
            System.out.println(cursor.getString(cursor.getColumnIndex(ContainerTable.Keys.BARCODE)));
            cursor.moveToNext();
        }
        if (cursor.getCount() > 0) ((TextView) findViewById(R.id.last_scan)).setText(cursor.getString(0)); else ((TextView) findViewById(R.id.last_scan)).setText("-");
        cursor.close();
        ((TextView) findViewById(R.id.total_items)).setText(String.valueOf(getItemCount()));
        ((TextView) findViewById(R.id.total_containers)).setText(String.valueOf(getContainerCount()));
    }

    public boolean saveToFile() {
        try {
            //System.out.println(OUTPUT_FILE.exists() + "-" + OUTPUT_FILE.delete() + "-" + OUTPUT_FILE.createNewFile() + "-" + OUTPUT_FILE.getAbsoluteFile());
            OUTPUT_FILE.delete();
            OUTPUT_FILE.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        Cursor itemCursor = db.rawQuery("SELECT " + InventoryDatabase.BARCODE + ", " + InventoryDatabase.LOCATION_ID + ", " + InventoryDatabase.DATE_TIME + " FROM " + ItemTable.NAME + ", " + ContainerTable.NAME + " ORDER BY " + InventoryDatabase.DATE_TIME + " ASC", null);
        itemCursor.moveToFirst();
        int itemBarcodeIndex = itemCursor.getColumnIndex(ItemTable.Keys.BARCODE);
        int itemLocationIdIndex = itemCursor.getColumnIndex(ItemTable.Keys.LOCATION_ID);
        int itemDateTimeIndex = itemCursor.getColumnIndex(ItemTable.Keys.DATE_TIME);
        long currentLocation = -1;
        try {
            PrintStream printStream = new PrintStream(OUTPUT_FILE);

            while (!itemCursor.isAfterLast()) {
                long tempLocation = itemCursor.getLong(itemLocationIdIndex);
                if (tempLocation != currentLocation) {
                    currentLocation = tempLocation;
                    Cursor locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " WHERE " + InventoryDatabase.ID + " = " + currentLocation + " LIMIT 1", null);
                    locationCursor.moveToFirst();
                    String locationText = locationCursor.getString(locationCursor.getColumnIndex(LocationTable.Keys.BARCODE)) + "|" + formatDate(locationCursor.getLong(locationCursor.getColumnIndex(LocationTable.Keys.DATE_TIME)));
                    locationCursor.close();
                    //System.out.println(locationText);
                    printStream.println(locationText);

                    printStream.flush();
                }
                String itemText = itemCursor.getString(itemBarcodeIndex) + "|" + formatDate(itemCursor.getLong(itemDateTimeIndex));
                //System.out.println(itemText);
                printStream.println(itemText);
                printStream.flush();

                itemCursor.moveToNext();
            }
            printStream.close();
            MediaScannerConnection.scanFile(this, new String[]{OUTPUT_FILE.getAbsolutePath()}, null, null);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        itemCursor.close();
        return true;
    }

    public void scanBarcode(String barcode) {
        if (isItem(barcode)) {
            addBarcodeItem(barcode);
        } else if (isContainer(barcode)) {
            addBarcodeContainer(barcode);
        } else if (isLocation(barcode)) {
            addBarcodeLocation(barcode);
        } else {
            Toast.makeText(this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
        }
    }

    public void addBarcodeItem(@NonNull String barcode) {
        long lastLocationId = getLastLocationId();
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }
        ContentValues newItem = new ContentValues();
        newItem.put(InventoryDatabase.BARCODE, barcode);
        newItem.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newItem.put(InventoryDatabase.DATE_TIME, System.currentTimeMillis());

        if (db.insert(ItemTable.NAME, null, newItem) == -1) {
            Toast.makeText(this, "Error adding item \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        itemRecyclerAdapter.notifyItemInserted(0);
        if (itemRecyclerAdapter.getItemCount() == maxItemHistory) itemRecyclerAdapter.notifyItemRemoved(itemRecyclerAdapter.getItemCount() - 1);
        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    public void removeBarcodeItem(int index) {
        if (db.delete(ItemTable.NAME, InventoryDatabase.ID + " = " + itemRecyclerAdapter.getItemId(index), null) > 0) {
            itemRecyclerAdapter.notifyItemRemoved(index);
            itemRecyclerAdapter.notifyItemRangeChanged(index, itemRecyclerAdapter.getItemCount() - index);
            updateInfo();
        } else Toast.makeText(InventoryActivity.this, "Error removing item \"" + index + "\" from the inventory", Toast.LENGTH_SHORT).show();
    }

    public void addBarcodeContainer(@NonNull String barcode) {
        long lastLocationId = getLastLocationId();
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }
        ContentValues newContainer = new ContentValues();
        newContainer.put(InventoryDatabase.BARCODE, barcode);
        newContainer.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newContainer.put(InventoryDatabase.DATE_TIME, System.currentTimeMillis());

        if (db.insert(ContainerTable.NAME, null, newContainer) == -1) {
            Toast.makeText(this, "Error adding container \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        itemRecyclerAdapter.notifyItemInserted(0);
        if (itemRecyclerAdapter.getItemCount() == maxItemHistory) itemRecyclerAdapter.notifyItemRemoved(itemRecyclerAdapter.getItemCount() - 1);
        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    public void removeBarcodeContainer(int index) {
        if (db.delete(ContainerTable.NAME, InventoryDatabase.ID + " = " + itemRecyclerAdapter.getItemId(index), null) > 0) {
            itemRecyclerAdapter.notifyItemRemoved(index);
            itemRecyclerAdapter.notifyItemRangeChanged(index, itemRecyclerAdapter.getItemCount() - index);
            updateInfo();
        } else {
            db.rawQuery("SELECT " + ContainerTable.Keys.BARCODE + " FROM " + ContainerTable.NAME + " WHERE " + ContainerTable.Keys.ID + " = " + itemRecyclerAdapter.getItemId(index), null);
            Toast.makeText(InventoryActivity.this, "Error removing item \"" + index + "\" from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    public void addBarcodeLocation(String barcode) {
        ContentValues newLocation = new ContentValues();
        newLocation.put(InventoryDatabase.BARCODE, barcode);
        newLocation.put(InventoryDatabase.DATE_TIME, System.currentTimeMillis());

        if (db.insert(LocationTable.NAME, null, newLocation) == -1) {
            Toast.makeText(this, "Error adding location \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }
        updateInfo();
    }

    public long getLastLocationId() {
        Cursor cursor = db.rawQuery("SELECT " + InventoryDatabase.ID + " FROM " + LocationTable.NAME + " ORDER BY " + InventoryDatabase.ID + " DESC LIMIT 1", null);
        if (cursor.getCount() <= 0) {
            cursor.close();
            return -1;
        }

        cursor.moveToFirst();
        long id = cursor.getLong(cursor.getColumnIndex(InventoryDatabase.ID));
        cursor.close();
        return id;
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

    public String formatDate(long millis) {
        return DateFormat.format(DATE_FORMAT, millis).toString();
    }

    public boolean isItem(@NonNull String barcode) {
        return barcode.startsWith("e1") || barcode.startsWith("E") || barcode.startsWith("t") || barcode.startsWith("T");
    }

    public boolean isContainer(@NonNull String barcode) {
        return barcode.startsWith("m1") || barcode.startsWith("M") || barcode.startsWith("a") || barcode.startsWith("A");
    }

    public boolean isLocation(@NonNull String barcode) {
        return barcode.startsWith("V") || barcode.startsWith("L5");
    }

    public boolean isProcess(@NonNull String barcode) {
        return barcode.startsWith("L3");
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
            String barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
            String description = cursor.getString(cursor.getColumnIndex(InventoryDatabase.DESCRIPTION));
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

