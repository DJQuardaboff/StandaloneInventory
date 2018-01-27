package com.porterlee.mobileinventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

import com.porterlee.mobileinventory.InventoryDatabase.ItemTable;
import com.porterlee.mobileinventory.InventoryDatabase.LocationTable;

import device.scanner.IScannerService;
import device.scanner.ScannerService;

import static com.porterlee.mobileinventory.MainActivity.iScanner;
import static com.porterlee.mobileinventory.MainActivity.mDecodeResult;

public class InventoryActivity extends AppCompatActivity {
    private static final File TEMP_OUTPUT_FILE = new File(Environment.getExternalStorageDirectory(), "Download/invinfo_temp.txt");
    private static final File OUTPUT_FILE = new File(Environment.getExternalStorageDirectory(), "Download/invinfo.txt");
    private static final String DATABASE_DIRECTORY = "Inventory";
    private static final String IS_LIKE_ITEM_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'e1%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'E%\'";
    private static final String IS_LIKE_CONTAINER_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'m1%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'M%\'";
    //private static final String IS_LIKE_LOCATION_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'V%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'L5%\'";
    //private static final String IS_LIKE_PROCESS_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'L3%\'";
    private static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final String TAG = InventoryActivity.class.getSimpleName();
    private static final int maxItemHistoryIncrease = 25;
    private int maxItemHistory = maxItemHistoryIncrease;
    private ScanResultReceiver resultReciever;
    private IntentFilter resultFilter;
    private int itemCount = 0;
    private int containerCount = 0;
    private long lastLocationId = -1;
    private String lastLocationBarcode = "-";
    private String lastItemBarcode = "-";
    //private int locationCount;
    //private int processCount;
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inventory_layout);
        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        new File(getFilesDir() + "/" + DATABASE_DIRECTORY).mkdirs();
        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + DATABASE_DIRECTORY + "/" + InventoryDatabase.FILE_NAME, null);
        System.out.println("deletee: " + new File(getFilesDir() + "/" + InventoryDatabase.FILE_NAME).delete());
        //db.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
        //db.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ItemTable.TABLE_CREATION);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + LocationTable.TABLE_CREATION);

        itemCount = getItemCount();
        containerCount = getContainerCount();
        lastLocationId = getLastLocationId();
        lastLocationBarcode = getLastLocationBarcode();
        lastItemBarcode = getLastItemBarcode();

        this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                randomScan();
            }
        });

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(i)});
                cursor.moveToFirst();
                long id = cursor.getLong(0);
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                int count = itemCount + containerCount;
                count = Math.min(count, maxItemHistory);
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_item_layout, parent, false);
                return new SimpleViewHolder(itemLayoutView);
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                final SimpleViewHolder simpleViewHolder = ((SimpleViewHolder) holder);
                simpleViewHolder.expandedMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(InventoryActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.popup_menu, popup.getMenu());
                        popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(holder.getAdapterPosition())});
                                cursor.moveToFirst();
                                String barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
                                Log.d(TAG, "Removing " + (isItem(barcode) ? "item" : "container") + " at position " + holder.getAdapterPosition() + " with barcode " + barcode);
                                cursor.close();
                                if (isContainer(barcode)) removeBarcodeContainer(simpleViewHolder);
                                if (isItem(barcode)) removeBarcodeItem(simpleViewHolder);
                                updateInfo();
                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.ID + ", " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.DESCRIPTION + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(position)});
                ((SimpleViewHolder) holder).bindViews(cursor);
                cursor.close();
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
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        resultReciever = new ScanResultReceiver();
        resultFilter = new IntentFilter();
        resultFilter.setPriority(0);
        resultFilter.addAction("device.scanner.USERMSG");
        registerReceiver(resultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(resultReciever);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inventory_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                if (itemRecyclerAdapter.getItemCount() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Clear Inventory");
                    builder.setMessage("Are you sure you want to clear this inventory?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int deltedCount = db.delete(ItemTable.NAME, "1", null);
                            db.delete(LocationTable.NAME, null, null);

                            if (itemCount + containerCount != deltedCount)
                                Toast.makeText(InventoryActivity.this, "Detected inconsistencies with number of items while deleting", Toast.LENGTH_SHORT).show();

                            itemCount = 0;
                            containerCount = 0;
                            lastLocationId = -1;
                            lastLocationBarcode = "-";
                            lastItemBarcode = "-";

                            itemRecyclerAdapter.notifyDataSetChanged();
                            itemRecyclerAdapter.notifyItemRangeRemoved(0, itemRecyclerAdapter.getItemCount());
                            updateInfo();
                            Toast.makeText(InventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                } else
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_save_to_file:
                saveToFile();
                return true;
            case R.id.action_preload:
                startActivity(new Intent(this, PreloadActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initScanner() throws RemoteException {
        iScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"));

        if (iScanner != null) {

            iScanner.aDecodeAPIInit();
            //try {
                //Thread.sleep(500);
            //} catch (InterruptedException e) {
            //}
            iScanner.aDecodeSetDecodeEnable(1);
            iScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
        }
    }

    public int getItemCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + IS_LIKE_ITEM_CLAUSE + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    public int getContainerCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + IS_LIKE_CONTAINER_CLAUSE + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    /*public int getLocationCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + LocationTable.NAME + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }*/

    public void updateInfo() {
        ((TextView) findViewById(R.id.current_location)).setText(lastLocationBarcode);
        ((TextView) findViewById(R.id.last_scan)).setText(lastItemBarcode);
        ((TextView) findViewById(R.id.total_items)).setText(String.valueOf(itemCount));
        ((TextView) findViewById(R.id.total_containers)).setText(String.valueOf(containerCount));
    }

    public void saveToFile() {
        try {
            //System.out.println(OUTPUT_FILE.exists() + "-" + OUTPUT_FILE.delete() + "-" + OUTPUT_FILE.createNewFile() + "-" + OUTPUT_FILE.getAbsoluteFile());
            if (OUTPUT_FILE.exists() && !OUTPUT_FILE.delete()) {
                Toast.makeText(this, "Could not delete existing output file", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!TEMP_OUTPUT_FILE.createNewFile()) {
                Toast.makeText(this, "Could not create new output file", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (IOException e) {
            Toast.makeText(this, "Could not create new output file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return;
        }

        try {
            Cursor itemCursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.LOCATION_ID + ", " + ItemTable.Keys.DATE_TIME + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " ASC;",null);
            itemCursor.moveToFirst();
            long currentLocation = -1;

            PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);
            Cursor locationCursor;

            while (!itemCursor.isAfterLast()) {
                long tempLocation = itemCursor.getLong(itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID));
                if (tempLocation != currentLocation) {
                    currentLocation = tempLocation;
                    locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ? LIMIT 1;", new String[] {String.valueOf(currentLocation)});
                    locationCursor.moveToFirst();
                    String locationText = locationCursor.getString(locationCursor.getColumnIndex(InventoryDatabase.BARCODE)) + "|" + formatDate(locationCursor.getLong(locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME)));
                    locationCursor.close();
                    //System.out.println(locationText);
                    printStream.println(locationText);

                    printStream.flush();
                }
                String itemText = itemCursor.getString(itemCursor.getColumnIndex(InventoryDatabase.BARCODE)) + "|" + formatDate(itemCursor.getLong(itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME)));
                //System.out.println(itemText);
                printStream.println(itemText);
                printStream.flush();

                itemCursor.moveToNext();
            }
            printStream.close();

            itemCursor.moveToFirst();

            BufferedReader br = new BufferedReader(new FileReader(TEMP_OUTPUT_FILE));
            String line;
            int i = 0;
            while (!itemCursor.isAfterLast()) {
                line = br.readLine();
                long tempLocation = itemCursor.getLong(itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID));
                if (tempLocation != currentLocation) {
                    currentLocation = tempLocation;
                    locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ? LIMIT 1;", new String[] {String.valueOf(currentLocation)});
                    locationCursor.moveToFirst();
                    String locationText = locationCursor.getString(locationCursor.getColumnIndex(InventoryDatabase.BARCODE)) + "|" + formatDate(locationCursor.getLong(locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME)));
                    locationCursor.close();
                    //System.out.println(locationText);
                    if (!locationText.equals(line)) {
                        Log.e(TAG, "Error at item #" + i + " in file output");
                        Log.e(TAG, "Correct String: " + locationText);
                        Log.e(TAG, "String in file: " + line);
                        Toast.makeText(this, "There was a problem verifying the output file", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    line = br.readLine();
                    i++;
                }
                String itemText = itemCursor.getString(itemCursor.getColumnIndex(InventoryDatabase.BARCODE)) + "|" + formatDate(itemCursor.getLong(itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME)));
                //System.out.println(itemText);
                if (!itemText.equals(line)) {
                    Log.e(TAG, "Error at item #" + i + " in file output");
                    Log.e(TAG, "Correct String: " + itemText);
                    Log.e(TAG, "String in file: " + line);
                    Toast.makeText(this, "There was a problem verifying the output file", Toast.LENGTH_SHORT).show();
                    return;
                }
                itemCursor.moveToNext();
                i++;
            }
            itemCursor.close();
            br.close();

            if (!TEMP_OUTPUT_FILE.renameTo(OUTPUT_FILE)) {
                Toast.makeText(this, "Could not rename output file to \"" + OUTPUT_FILE.getName() + "\"", Toast.LENGTH_SHORT).show();
                return;
            }
            MediaScannerConnection.scanFile(this, new String[]{OUTPUT_FILE.getAbsolutePath()}, null, null);

        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Could not find file \"" + OUTPUT_FILE.getName() + "\", even though it was just created", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return;
        } catch (IOException e) {
            Toast.makeText(this, "IOException occured while verifying output", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        Toast.makeText(this, "Saved to file", Toast.LENGTH_SHORT).show();
    }

    private static final String alphaNumeric = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public void randomScan() {
        Random r = new Random();
        int temp = r.nextInt(10);
        String barcode = "V";
        if (lastLocationId != -1)
            barcode = temp < 2 ? "V" : (temp < 3 ? "m1" : (temp < 4 ? "M" : (temp < 7 ? "e1" : "E"))) ;

        if (isLocation(barcode))
            for (int i = r.nextInt(5) + 5; i > 0; i--)
                barcode = barcode.concat(String.valueOf(alphaNumeric.charAt(r.nextInt(alphaNumeric.length()))).toUpperCase());

        if (isItem(barcode) || isContainer(barcode))
            for (int i = r.nextInt(5) + 5; i > 0; i--)
                barcode = barcode.concat(String.valueOf(alphaNumeric.charAt(r.nextInt(alphaNumeric.length()))));

        scanBarcode(barcode);
    }

    public void scanBarcode(String barcode) {
        Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.BARCODE + " = ?;", new String[] {String.valueOf(barcode)});

        if (cursor.getCount() > 0) {
            cursor.close();
            Toast.makeText(this, "Duplicate " + (isItem(barcode) ? "item" : "container") + " scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        cursor.close();

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
        //System.out.println("item: " + barcode + "-" + isContainer(barcode) + "-" + isItem(barcode) + "-" + isLocation(barcode) + "-" + isProcess(barcode));

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

        itemCount++;
        lastItemBarcode = barcode;
        itemRecyclerAdapter.notifyItemInserted(0);
        if (itemRecyclerAdapter.getItemCount() == maxItemHistory) itemRecyclerAdapter.notifyItemRemoved(itemRecyclerAdapter.getItemCount() - 1);
        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    public void removeBarcodeItem(SimpleViewHolder holder) {
        //System.out.println("remove item " + index);
        if (db.delete(ItemTable.NAME, InventoryDatabase.ID + " = ?;", new String[] {String.valueOf(holder.getId())}) > 0) {
            itemCount--;
            lastItemBarcode = getLastItemBarcode();
            itemRecyclerAdapter.notifyItemRemoved(holder.getAdapterPosition());
            itemRecyclerAdapter.notifyItemRangeChanged(holder.getAdapterPosition() + 1, itemRecyclerAdapter.getItemCount() - holder.getAdapterPosition());
            updateInfo();
        } else {
            Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE));
            }

            cursor.close();
            Log.d(TAG, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory");
            Toast.makeText(InventoryActivity.this, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    public void addBarcodeContainer(@NonNull String barcode) {
        //System.out.println("container: " + barcode + "-" + isContainer(barcode) + "-" + isItem(barcode) + "-" + isLocation(barcode) + "-" + isProcess(barcode));

        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues newContainer = new ContentValues();
        newContainer.put(InventoryDatabase.BARCODE, barcode);
        newContainer.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newContainer.put(InventoryDatabase.DATE_TIME, System.currentTimeMillis());

        if (db.insert(ItemTable.NAME, null, newContainer) == -1) {
            Toast.makeText(this, "Error adding container \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        containerCount++;
        lastItemBarcode = barcode;
        itemRecyclerAdapter.notifyItemInserted(0);
        if (itemRecyclerAdapter.getItemCount() == maxItemHistory) itemRecyclerAdapter.notifyItemRemoved(itemRecyclerAdapter.getItemCount() - 1);
        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    public void removeBarcodeContainer(SimpleViewHolder holder) {
        //System.out.println("remove container " + index);
        if (db.delete(ItemTable.NAME, InventoryDatabase.ID + " = " + holder.getId(), null) > 0) {
            containerCount--;
            lastItemBarcode = getLastItemBarcode();
            itemRecyclerAdapter.notifyItemRemoved(holder.getAdapterPosition());
            itemRecyclerAdapter.notifyItemRangeChanged(holder.getAdapterPosition() + 1, itemRecyclerAdapter.getItemCount() - holder.getAdapterPosition());
            updateInfo();
        } else {
            Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE));
            }

            cursor.close();
            Log.d(TAG, "Error removing container " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory");
            Toast.makeText(InventoryActivity.this, "Error removing container " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    public void addBarcodeLocation(String barcode) {
        //System.out.println("location: " + barcode + "-" + isContainer(barcode) + "-" + isItem(barcode) + "-" + isLocation(barcode) + "-" + isProcess(barcode));
        ContentValues newLocation = new ContentValues();
        newLocation.put(InventoryDatabase.BARCODE, barcode);
        newLocation.put(InventoryDatabase.DATE_TIME, System.currentTimeMillis());

        long rowID = db.insert(LocationTable.NAME, null, newLocation);

        if (rowID == -1) {
            Toast.makeText(this, "Error adding location \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        //locationCount++;
        lastLocationId = rowID;
        lastLocationBarcode = barcode;
        updateInfo();
    }

    public long getLastLocationId() {
        Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;", null);
        long id = -1;

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            id = cursor.getLong(cursor.getColumnIndex(InventoryDatabase.ID));
        }

        cursor.close();
        return id;
    }

    public String getLastLocationBarcode() {
        Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;", null);
        String barcode = "-";

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
        }

        cursor.close();
        return barcode;
    }

    public String getLastItemBarcode() {
        Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1;", null);
        String barcode = "-";

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
        }

        cursor.close();
        return barcode;
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

    public String formatDate(long millis) {
        return DateFormat.format(DATE_FORMAT, millis).toString();
    }

    public boolean isItem(@NonNull String barcode) {
        return barcode.startsWith("e1") || barcode.startsWith("E");// || barcode.startsWith("t") || barcode.startsWith("T");
    }

    public boolean isContainer(@NonNull String barcode) {
        return barcode.startsWith("m1") || barcode.startsWith("M");// || barcode.startsWith("a") || barcode.startsWith("A");
    }

    public boolean isLocation(@NonNull String barcode) {
        return barcode.startsWith("V");// || barcode.startsWith("L5");
    }

    /*public boolean isProcess(@NonNull String barcode) {
        return barcode.startsWith("L3");
    }*/

    class SimpleViewHolder extends RecyclerView.ViewHolder {
        //private ProgressBar progressLoading;
        private TextView itemBarcode;
        private TextView itemDescription;
        private View itemDivider;
        private ImageButton expandedMenu;
        private long id = -1;

        private String barcode;
        private String description;

        SimpleViewHolder(final View itemView) {
            super(itemView);
            //progressLoading = itemView.findViewById(R.id.progress_loading);
            itemBarcode = itemView.findViewById(R.id.item_barcode);
            itemDescription = itemView.findViewById(R.id.item_location);
            itemDivider = itemView.findViewById(R.id.item_divider);
            expandedMenu = itemView.findViewById(R.id.menu_button);
        }

        public long getId() {
            return id;
        }

        public String getBarcode() {
            return barcode;
        }

        public String getDescription() {
            return description;
        }

        void bindViews(Cursor cursor) {
            cursor.moveToFirst();
            id = cursor.getLong(cursor.getColumnIndex(InventoryDatabase.ID));
            barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
            description = cursor.getString(cursor.getColumnIndex(InventoryDatabase.DESCRIPTION));
            //if (ready) {
                //progressLoading.setVisibility(View.GONE);
                if (barcode != null) {
                    itemBarcode.setText(barcode);
                    itemBarcode.setVisibility(View.VISIBLE);
                } else {
                    itemBarcode.setVisibility(View.GONE);
                    itemDivider.setVisibility(View.GONE);
                }
                if (description != null) {
                    itemDescription.setText(description);
                    itemDescription.setVisibility(View.VISIBLE);
                } else {
                    itemDescription.setVisibility(View.GONE);
                    itemDivider.setVisibility(View.GONE);
                }
                expandedMenu.setVisibility(View.VISIBLE);
            //} else {
                //progressLoading.setVisibility(View.VISIBLE);
                //itemBarcode.setVisibility(View.GONE);
                //itemDescription.setVisibility(View.GONE);
                //expandedMenu.setVisibility(View.GONE);
            //}
        }
    }

    public class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (iScanner != null) {
                try {
                    iScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;
                    if (barcode.length() >= 4) {
                        if (barcode.equals(">><<")) {
                            Toast.makeText(InventoryActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                            return;
                        } else if ((barcode.startsWith(">>")) && (barcode.endsWith("<<"))) {
                            barcode = barcode.substring(2, barcode.length() - 2);
                            if (barcode.equals("SCAN AGAIN")) return;
                            scanBarcode(barcode);
                        } else if (barcode.endsWith("<<")) {
                            Toast.makeText(InventoryActivity.this, "Error scanning barcode: Incorrect prefix", Toast.LENGTH_SHORT).show();
                        }
                    } else return;
                    //System.out.println("symName: " + mDecodeResult.symName);
                    //System.out.println("decodeValue: " + mDecodeResult.decodeValue);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

