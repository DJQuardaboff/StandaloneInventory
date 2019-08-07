package com.porterlee.standardinventory;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.*;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;

import com.porterlee.plcscanners.AbstractScanner;
import com.porterlee.standardinventory.InventoryDatabase.ItemTable;
import com.porterlee.standardinventory.InventoryDatabase.LocationTable;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

public class InventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), InventoryDatabase.DIRECTORY);
    private static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final String TAG = InventoryActivity.class.getSimpleName();
    private static final String OUTPUT_FILE_HEADER = String.format(Locale.US, "%s|%s|%s|v%s|%d", BuildConfig.APPLICATION_ID.substring(BuildConfig.APPLICATION_ID.indexOf('.', BuildConfig.APPLICATION_ID.indexOf('.') + 1) + 1), BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    private SQLiteStatement IS_DUPLICATE_STATEMENT;
    private SQLiteStatement LAST_ITEM_BARCODE_STATEMENT;
    private SQLiteStatement TOTAL_ITEM_COUNT;
    private SQLiteStatement TOTAL_LOCATION_COUNT;
    private SQLiteStatement UPDATE_QUANTITY;
    private File outputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Float, String> saveTask;
    private long lastLocationId = -1;
    private String lastLocationBarcode = "";
    private String lastItemBarcode = "";
    private SelectableRecyclerView itemRecyclerView;
    private SelectableRecyclerView locationRecyclerView;
    private CursorRecyclerViewAdapter<InventoryItemViewHolder> itemRecyclerAdapter;
    private CursorRecyclerViewAdapter<InventoryLocationViewHolder> locationRecyclerAdapter;
    private SQLiteDatabase mDatabase;

    private final AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener = new AbstractScanner.OnBarcodeScannedListener() {
        @Override
        public void onBarcodeScanned(final String barcode) {
            if (saveTask != null) {
                AbstractScanner.onScanComplete(false);
                Toast.makeText(InventoryActivity.this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
                return;
            }

            if (barcode == null || barcode.equals("")) {
                AbstractScanner.onScanComplete(false);
                Toast.makeText(InventoryActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                return;
            }

            if (BarcodeType.getBarcodeType(barcode).equals(BarcodeType.Invalid)) {
                AbstractScanner.onScanComplete(false);
                Toast.makeText(InventoryActivity.this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
                return;
            }

            IS_DUPLICATE_STATEMENT.bindLong(1, lastLocationId);
            IS_DUPLICATE_STATEMENT.bindString(2, barcode);
            final boolean isDuplicate = IS_DUPLICATE_STATEMENT.simpleQueryForLong() > 0;

            if (isDuplicate) {
                AbstractScanner.onScanComplete(false);
                getScanner().setIsEnabled(false);
                new AlertDialog.Builder(InventoryActivity.this)
                        .setCancelable(false)
                        .setTitle("Duplicate item")
                        .setMessage("An item with the same barcode was already scanned, would you still like to add it to the list?")
                        .setNegativeButton(R.string.action_no, null)
                        .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                addItem(barcode);
                            }
                        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        getScanner().setIsEnabled(true);
                    }
                }).create().show();
                return;
            }

            if (BarcodeType.Item.isOfType(barcode) || BarcodeType.Container.isOfType(barcode)) {
                AbstractScanner.onScanComplete(true);
                addItem(barcode);
            } else if (BarcodeType.Location.isOfType(barcode)) {
                AbstractScanner.onScanComplete(true);
                addBarcodeLocation(barcode);
            } else {
                AbstractScanner.onScanComplete(false);
                Toast.makeText(InventoryActivity.this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
            }
        }
    };
    
    private AbstractScanner getScanner() {
        return AbstractScanner.getInstance();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AbstractScanner.setActivity(this);
        
        if (!getScanner().init()) {
            finish();
            Toast.makeText(this, "Scanner failed to initialize", Toast.LENGTH_LONG).show();
            return;
        }

        setContentView(R.layout.inventory_layout);

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%s v%s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return;
            }
        }

        preInit();
    }

    private void preInit() {
        archiveDirectory = new File(getFilesDir() + "/" + InventoryDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        outputFile = new File(OUTPUT_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        outputFile.getParentFile().mkdirs();
        databaseFile = new File(getFilesDir() + "/" + InventoryDatabase.DIRECTORY + "/" + InventoryDatabase.FILE_NAME);
        //databaseFile = new File(OUTPUT_PATH, InventoryDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.getParentFile().mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            try {
                //System.out.println(databaseFile.exists());
                if (databaseFile.renameTo(File.createTempFile("error", ".db", new File(databaseFile.getParent(), InventoryDatabase.ARCHIVE_DIRECTORY)))) {
                    Toast.makeText(this, "There was an error loading the inventory file. It has been archived", Toast.LENGTH_SHORT).show();
                } else {
                    databaseLoadError();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
                databaseLoadError();
            }
        }
    }

    private void databaseLoadError() {
        getScanner().setIsEnabled(false);
        new AlertDialog.Builder(InventoryActivity.this)
                .setCancelable(false)
                .setTitle("Database Load Error")
                .setMessage("There was an error loading the inventory file and it could not be archived.\n\nWould you like to delete the it?\n\nAnswering no will close the app.")
                .setNegativeButton(R.string.action_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!databaseFile.delete()) {
                            Toast.makeText(InventoryActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        Toast.makeText(InventoryActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                        initialize();
                        //mDatabase = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        getScanner().setIsEnabled(true);
                    }
                }).create().show();
    }

    private void initialize() throws SQLiteCantOpenDatabaseException{
        mDatabase = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        ItemTable.create(mDatabase);
        LocationTable.create(mDatabase);

        IS_DUPLICATE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " IN ( SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ? ) ) AND " + ItemTable.Keys.BARCODE + " = ?;");
        LAST_ITEM_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1;");
        TOTAL_ITEM_COUNT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME);
        TOTAL_LOCATION_COUNT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME);
        UPDATE_QUANTITY = mDatabase.compileStatement("UPDATE " + ItemTable.NAME + " SET " + InventoryDatabase.QUANTITY + " = ? WHERE " + InventoryDatabase.ID + " = ?");

        //itemCount = getItemCount();
        //containerCount = getContainerCount();
        //lastLocationId = getLastLocationId();
        //lastLocationBarcode = getLastLocationBarcode();
        lastItemBarcode = getLastItemBarcode();

        progressBar = findViewById(R.id.progress_saving);

        /*this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                randomScan();
            }
        });*/

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new CursorRecyclerViewAdapter<InventoryItemViewHolder>(queryItems()) {
            @NonNull
            @Override
            public InventoryItemViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
                return new InventoryItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(BuildConfig.display_quantity ? R.layout.inventory_quantity_item_layout : R.layout.inventory_item_layout, parent, false));
            }

            @Override
            public void onBindViewHolder(final InventoryItemViewHolder holder, final Cursor cursor) {
                holder.bindViews(cursor, itemRecyclerView.getSelectedItem() == holder.getAdapterPosition());
            }

            @Override
            public void onViewRecycled(@NonNull InventoryItemViewHolder holder) {
                holder.saveQuantity();
            }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemRecyclerAnimator);
        itemRecyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider, DividerItemDecoration.VERTICAL_LIST));

        locationRecyclerView = findViewById(R.id.location_list_view);
        locationRecyclerView.setHasFixedSize(true);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerAdapter = new CursorRecyclerViewAdapter<InventoryLocationViewHolder>(queryLocations()) {
            @NonNull
            @Override
            public InventoryLocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new InventoryLocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_location_layout, parent, false));
            }

            @Override
            public void onBindViewHolder(InventoryLocationViewHolder viewHolder, Cursor cursor) {
                viewHolder.bindViews(cursor, locationRecyclerView.getSelectedItem());
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        locationRecyclerView.setAdapter(locationRecyclerAdapter);
        final RecyclerView.ItemAnimator locationRecyclerAnimator = new DefaultItemAnimator();
        locationRecyclerAnimator.setAddDuration(100);
        locationRecyclerAnimator.setChangeDuration(100);
        locationRecyclerAnimator.setMoveDuration(100);
        locationRecyclerAnimator.setRemoveDuration(100);
        locationRecyclerView.setItemAnimator(locationRecyclerAnimator);

        /*mDatabase.beginTransaction();
        for (int i = 0; i < 1000; i++) {
            //Log.e(TAG, String.valueOf(i));
            randomScan();
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();*/

        if (BuildConfig.display_quantity) {
            final SoftKeyboardHandledConstraintLayout softKeyboardHandler = findViewById(R.id.inventory_layout);
            softKeyboardHandler.setOnSoftKeyboardVisibilityChangeListener(new SoftKeyboardHandledConstraintLayout.SoftKeyboardVisibilityChangeListener() {
                @Override
                public void onSoftKeyboardShow() { }

                @Override
                public void onSoftKeyboardHide() {
                    final View view = getCurrentFocus();
                    if (view != null)
                        view.clearFocus();
                }
            });
        }

        itemRecyclerView.setSelectedItem(-1);
        locationRecyclerView.setSelectedItem(-1);
        updateInfo();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getScanner().onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getScanner().onResume();
        AbstractScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
        AbstractScanner.setActivity(this);
    }

    @Override
    protected void onPause() {
        AbstractScanner.setActivity(null);
        AbstractScanner.setOnBarcodeScannedListener(null);
        getScanner().onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        getScanner().onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (saveTask != null) {
            saveTask.cancel(false);
            saveTask = null;
        }
        if (mDatabase != null)
            mDatabase.close();
        getScanner().onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inventory_menu, menu);
        return super.onCreateOptionsMenu(menu) | getScanner().onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu) | getScanner().onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                if (TOTAL_ITEM_COUNT.simpleQueryForLong() > 0 || TOTAL_LOCATION_COUNT.simpleQueryForLong() > 0) {
                    getScanner().setIsEnabled(false);
                    new AlertDialog.Builder(this)
                            .setCancelable(true)
                            .setTitle("Clear Inventory")
                            .setMessage("Are you sure you want to clear this inventory?")
                            .setNegativeButton(R.string.action_no, null)
                            .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (saveTask != null) {
                                        return;
                                    }

                                    changedSinceLastArchive = true;

                                    //int deletedCount = mDatabase.delete(ItemTable.NAME, "1", null);
                                    //mDatabase.delete(ItemTable.NAME, null, null);
                                    //mDatabase.delete(LocationTable.NAME, null, null);

                                    mDatabase.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
                                    ItemTable.create(mDatabase);

                                    mDatabase.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);
                                    LocationTable.create(mDatabase);

                                    lastLocationId = -1;
                                    lastLocationBarcode = "";
                                    lastItemBarcode = "";

                                    itemRecyclerAdapter.changeCursor(null);
                                    locationRecyclerAdapter.changeCursor(null);
                                    updateInfo();
                                    Toast.makeText(InventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                                }
                            }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    getScanner().setIsEnabled(true);
                                }
                            }).create().show();
                } else {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_save_to_file:
                if (TOTAL_ITEM_COUNT.simpleQueryForLong() < 1) {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                        return true;
                    }
                }

                if (saveTask == null) {
                    for (int i = 0, childCount = itemRecyclerView.getChildCount(); i < childCount; i++) {
                        final View v = itemRecyclerView.getChildAt(i);
                        if (v != null) {
                            final InventoryItemViewHolder holder = (InventoryItemViewHolder) itemRecyclerView.getChildViewHolder(v);
                            holder.saveQuantity();
                        }
                    }
                    preSave();
                    archiveDatabase();
                    (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                    saveTask = new SaveToFileTask().execute();
                } else {
                    saveTask.cancel(false);
                    postSave();
                }

                return true;
            case R.id.cancel_save:
                if (saveTask != null) {
                    getScanner().setIsEnabled(false);
                    new AlertDialog.Builder(this)
                            .setCancelable(true)
                            .setTitle("Cancel Save")
                            .setMessage("Are you sure you want to stop saving this file?")
                            .setNegativeButton(R.string.action_no, null)
                            .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (saveTask != null && !saveTask.isCancelled()) {
                                        saveTask.cancel(false);
                                    }
                                }
                            }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    getScanner().setIsEnabled(true);
                                }
                            }).create().show();
                } else {
                    postSave();
                }
                return true;
            /*case R.id.action_continuous:
                try {
                    if (!item.isChecked()){
                        iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    } else {
                        iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
                    }
                    item.setChecked(!item.isChecked());
                } catch (RemoteException e) {
                    e.printStackTrace();
                    item.setChecked(false);
                    Toast.makeText(this, "An error occured while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;*/
            default:
                return super.onOptionsItemSelected(item) | getScanner().onOptionsItemSelected(item);
        }
    }
    /*
    private void loadCurrentScannerOptions() {
        if (mOptionsMenu != null) {
            MenuItem item = mOptionsMenu.findItem(R.id.action_continuous);
            try {
                if (iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO) {
                    iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    item.setChecked(true);
                } else
                    item.setChecked(iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
            } catch (NullPointerException e) {
                e.printStackTrace();
                item.setVisible(false);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public int getLocationCount() {
        Cursor cursor = mDatabase.rawQuery("SELECT COUNT(*) FROM " + LocationTable.NAME + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }
    */
    private void updateInfo() {
        //Log.v(TAG, "Updating info");
        ((TextView) findViewById(R.id.current_location)).setText(lastLocationBarcode.isEmpty() ? "-" : lastLocationBarcode);
        ((TextView) findViewById(R.id.last_scan)).setText(lastItemBarcode.isEmpty() ? "-" : lastItemBarcode);
        ((TextView) findViewById(R.id.total_items)).setText(lastLocationBarcode.isEmpty() ? "-" : String.valueOf(itemRecyclerAdapter.getItemCount()));
        //((TextView) findViewById(R.id.total_items)).setText(String.valueOf(itemCount));
        //((TextView) findViewById(R.id.total_containers)).setText(String.valueOf(containerCount));
        findViewById(R.id.total_containers_title).setVisibility(View.GONE);
        findViewById(R.id.total_containers).setVisibility(View.GONE);
    }

    private void preSave() {
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(false);
        mOptionsMenu.findItem(R.id.cancel_save).setVisible(true);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        saveTask = null;
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }
    /*
    private static final String alphaNumeric = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private void randomScan() {
        Random r = new Random();
        final float temp = r.nextFloat();
        String barcode = (lastLocationId == -1 | temp < .1) ? "V" : (temp < .25 ? "m1" : (temp < .3 ? "M" : (temp < .9 ? "e1" : "E")));

        for (int i = r.nextInt(5) + 10; i > 0; i--)
            barcode = barcode.concat(String.valueOf(alphaNumeric.charAt(r.nextInt(alphaNumeric.length()))).toUpperCase());

        if (isLocation(barcode))
            barcode = barcode.toUpperCase();

        scanBarcode(barcode);
    }
    */
    private void addItem(@NonNull String barcode) {
        if (saveTask != null) return;
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues newItem = new ContentValues();
        newItem.put(InventoryDatabase.BARCODE, barcode);
        newItem.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newItem.put(InventoryDatabase.DESCRIPTION, "");
        newItem.put(InventoryDatabase.TAGS, "");
        newItem.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (mDatabase.insert(ItemTable.NAME, null, newItem) == -1) {
            com.porterlee.plcscanners.Utils.vibrate(this.getApplicationContext());
            Log.w(TAG, "Error adding item \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding item \"" + barcode + "\" to the inventory", Toast.LENGTH_LONG).show();
            return;
        }

        itemRecyclerAdapter.changeCursor(queryItems());
        itemRecyclerView.setSelectedItem(itemRecyclerAdapter.getIndexOfBarcode(barcode));
        changedSinceLastArchive = true;
        lastItemBarcode = barcode;
        updateInfo();
    }

    private void removeItem(@NonNull InventoryItemViewHolder holder) {
        if (saveTask != null) return;
        if (mDatabase.delete(ItemTable.NAME, InventoryDatabase.ID + " = ?", new String[] { String.valueOf(holder.getId()) }) > 0) {
            lastItemBarcode = getLastItemBarcode();
            itemRecyclerAdapter.changeCursor(queryItems());
            changedSinceLastArchive = true;
            updateInfo();
        } else {
            com.porterlee.plcscanners.Utils.vibrate(this.getApplicationContext());

            if (!holder.barcode.equals("")) {
                Log.w(TAG, "Error removing item at adapter position " + holder.getAdapterPosition() + ", with barcode \"" + holder.barcode + "\", from the inventory");
                Toast.makeText(InventoryActivity.this, "Error removing item \"" + holder.barcode + "\" from the inventory", Toast.LENGTH_LONG).show();
            } else {
                Log.w(TAG, "Error removing item at adapter position " + holder.getAdapterPosition() + " from the inventory");
                Toast.makeText(InventoryActivity.this, "Error removing item", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void addBarcodeLocation(@NonNull String barcode) {
        if (saveTask != null) return;
        ContentValues newLocation = new ContentValues();
        newLocation.put(InventoryDatabase.BARCODE, barcode);
        newLocation.put(InventoryDatabase.DESCRIPTION, "");
        newLocation.put(InventoryDatabase.TAGS, "");
        newLocation.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        long rowID = mDatabase.insert(LocationTable.NAME, null, newLocation);

        if (rowID == -1) {
            Log.w(TAG, "Error adding location \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding location \"" + barcode + "\" to the inventory", Toast.LENGTH_LONG).show();
            return;
        }

        locationRecyclerAdapter.changeCursor(queryLocations());
        locationRecyclerView.setSelectedItem(locationRecyclerAdapter.getIndexOfBarcode(barcode));
        changedSinceLastArchive = true;

        if (!lastLocationBarcode.equals(barcode)) {
            lastLocationId = rowID;
            lastLocationBarcode = barcode;
            itemRecyclerAdapter.changeCursor(queryItems());
            itemRecyclerView.setSelectedItem(-1);
        }

        updateInfo();
    }

    private Cursor queryLocations() {
        return mDatabase.rawQuery("SELECT MAX(" + LocationTable.Keys.ID + ") AS _id, MIN(" + LocationTable.Keys.ID + ") AS min_id, " + LocationTable.Keys.BARCODE + " AS barcode, MAX(" + LocationTable.Keys.DESCRIPTION + ") AS description, MAX(" + LocationTable.Keys.TAGS + ") AS tags, MAX(" + LocationTable.Keys.DATE_TIME + ") AS date_time FROM " + LocationTable.NAME + " GROUP BY barcode ORDER BY min_id", null);
    }

    private Cursor queryItems() {
        return mDatabase.rawQuery("SELECT * FROM ( SELECT " + ItemTable.Keys.ID + " AS _id, " + ItemTable.Keys.ID + " AS min_id, " + ItemTable.Keys.LOCATION_ID + " AS location_id, " + ItemTable.Keys.BARCODE + " AS barcode, " + ItemTable.Keys.QUANTITY + " AS quantity, " + ItemTable.Keys.DESCRIPTION + " AS description, " + ItemTable.Keys.TAGS + " AS tags, " + ItemTable.Keys.DATE_TIME + " AS date_time FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " AS _id FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? ) ) WHERE _id NOT NULL ORDER BY _id DESC", new String[] { lastLocationBarcode });
    }

    private boolean updateQuantity(long itemId, int quantity) {
        UPDATE_QUANTITY.bindLong(1, quantity);
        UPDATE_QUANTITY.bindLong(2, itemId);
        boolean success = UPDATE_QUANTITY.executeUpdateDelete() > 0;
        if (success) {
            changedSinceLastArchive = true;
        }
        return success;
    }

    private String getLastItemBarcode() {
        try {
            return LAST_ITEM_BARCODE_STATEMENT.simpleQueryForString();
        } catch (SQLiteDoneException e) {
            return "-";
        }
    }

    private CharSequence formatDate(long millis) {
        return DateFormat.format(DATE_FORMAT, millis).toString();
    }

    private class InventoryLocationViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;
        private View background;
        private long id = -1;
        private long minId = -1;
        private String barcode = "";
        private String description = "";
        private String tags = "";
        private String dateTime = "";
        private boolean isSelected = false;

        private InventoryLocationViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.location_text_view);
            background = itemView.findViewById(R.id.location_background);
            /*itemView.setClickable(true);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addBarcodeLocation(barcode, "");
                }
            });*/
        }

        private void bindViews(Cursor cursor, int selectedPosition) {
            id = cursor.getLong(cursor.getColumnIndex("_id"));
            minId = cursor.getLong(cursor.getColumnIndex("min_id"));
            barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            description = cursor.getString(cursor.getColumnIndex("description"));
            tags = cursor.getString(cursor.getColumnIndex("tags"));
            dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            isSelected = getAdapterPosition() == selectedPosition;

            itemView.setSelected(isSelected);

            background.setBackground(ContextCompat.getDrawable(InventoryActivity.this, isSelected ? R.drawable.scanned_selected_location_background : R.drawable.scanned_deselected_location_background));
            textView.setText(barcode);
        }
    }

    private class InventoryItemViewHolder extends RecyclerView.ViewHolder {
        //private MaterialProgressBar progressBarWaiting;
        private TextView barcodeTextView;
        private EditText quantityEditText;
        private ImageButton expandedMenuButton;
        private long id = -1;
        private long locationId = -1;
        private long quantity = 1;
        private String barcode = "";
        private String description = "";
        private String tags = "";
        private String dateTime = "";
        private boolean isSelected = false;

        InventoryItemViewHolder(final View itemView) {
            super(itemView);
            barcodeTextView = itemView.findViewById(R.id.barcode_text_view);
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PopupMenu popup = new PopupMenu(InventoryActivity.this, view);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.inventory_item_popup_menu, popup.getMenu());
                    popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (saveTask != null) {
                                Toast.makeText(InventoryActivity.this, "Cannot edit inventory while saving", Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            getScanner().setIsEnabled(false);
                            new AlertDialog.Builder(InventoryActivity.this)
                                    .setCancelable(true)
                                    .setTitle("Remove " + BarcodeType.getBarcodeType(barcode).name())
                                    .setMessage(String.format("Are you sure you want to remove item \"%s\"?", barcode))
                                    .setNegativeButton(R.string.action_no, null)
                                    .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            removeItem(InventoryItemViewHolder.this);
                                        }
                                    }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                        @Override
                                        public void onDismiss(DialogInterface dialog) {
                                            getScanner().setIsEnabled(true);
                                        }
                                    }).create().show();

                            return true;
                        }
                    });
                    popup.show();
                }
            });
            if (BuildConfig.display_quantity) {
                quantityEditText = itemView.findViewById(R.id.edit_quantity);
                quantityEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            saveQuantity();
                        }
                    }
                });
                quantityEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            saveQuantity();
                            quantityEditText.clearFocus();
                            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(quantityEditText.getWindowToken(), 0);
                            return true;
                        }
                        return false;
                    }
                });
                expandedMenuButton.setFocusable(false);
            }
        }

        void saveQuantity() {
            if (BuildConfig.display_quantity) {
                try {
                    String quantityText = quantityEditText.getText().toString();
                    if (quantityText.length() > 0) {
                        int inputQuantity = Integer.parseInt(quantityText);
                        if (updateQuantity(id, inputQuantity)) {
                            quantity = inputQuantity;
                        } else {
                            quantityEditText.setText(String.valueOf(quantity));
                            Toast.makeText(InventoryActivity.this, "Could not set quantity", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        quantityEditText.setText(String.valueOf(quantity));
                    }
                } catch (NumberFormatException e) {
                    quantityEditText.setText(String.valueOf(quantity));
                    Toast.makeText(InventoryActivity.this, "Quantity incorrectly formatted", Toast.LENGTH_LONG).show();
                }
            }
        }

        long getId() {
            return id;
        }

        void bindViews(final Cursor cursor, final boolean isSelected) {
            this.id = cursor.getLong(cursor.getColumnIndex("_id"));
            //this.locationId = cursor.getLong(cursor.getColumnIndex("location_id"));
            this.barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            this.description = cursor.getString(cursor.getColumnIndex("description"));
            this.tags = cursor.getString(cursor.getColumnIndex("tags"));
            this.dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            this.isSelected = isSelected;

            barcodeTextView.setText(barcode);

            if (isSelected) {
                barcodeTextView.setTypeface(null, Typeface.BOLD);
            } else {
                barcodeTextView.setTypeface(null, Typeface.NORMAL);
            }

            if (itemRecyclerAdapter.getIsDuplicate(barcode)) {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_yellow, null));
            } else {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_white, null));
            }

            if (BuildConfig.display_quantity) {
                quantityEditText.setText(cursor.getString(cursor.getColumnIndexOrThrow(InventoryDatabase.QUANTITY)));
                if (isSelected) {
                    quantityEditText.requestFocus();
                } else {
                    quantityEditText.clearFocus();
                }
            }
        }
    }

    private class SaveToFileTask extends AsyncTask<Void, Float, String> {
        private static final int MAX_UPDATES = 100;

        protected String doInBackground(Void... voids) {
            Cursor itemCursor = mDatabase.rawQuery("SELECT " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.QUANTITY + ", " + ItemTable.Keys.LOCATION_ID + ", " + ItemTable.Keys.DATE_TIME + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " ASC;",null);
            Cursor locationCursor = mDatabase.rawQuery("SELECT " + LocationTable.Keys.ID + ", " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " ASC;", null);

            itemCursor.moveToFirst();
            int itemBarcodeIndex = itemCursor.getColumnIndex(InventoryDatabase.BARCODE);
            int itemQuantityIndex = itemCursor.getColumnIndex(InventoryDatabase.QUANTITY);
            int itemLocationIdIndex = itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID);
            int itemDateTimeIndex = itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME);

            locationCursor.moveToFirst();
            int locationIdIndex = locationCursor.getColumnIndex(InventoryDatabase.ID);
            int locationBarcodeIndex = locationCursor.getColumnIndex(InventoryDatabase.BARCODE);
            int locationDateTimeIndex = locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME);

            PrintStream printStream = null;

            try {
                //noinspection ResultOfMethodCallIgnored
                OUTPUT_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("tmp", ".txt", OUTPUT_PATH);

                int totalItemCount = itemCursor.getCount() + 1;
                int currentLocationId = -1;

                printStream = new PrintStream(TEMP_OUTPUT_FILE);
                //Cursor locationCursor;

                int tempLocation;
                int itemIndex = 0;
                int updateNum = 0;

                //
                printStream.print(OUTPUT_FILE_HEADER + "\r\n");
                printStream.flush();
                //

                while (!itemCursor.isAfterLast()) {
                    if (isCancelled())
                        return "Save canceled";

                    final float tempProgress = ((float) itemIndex) / totalItemCount;
                    if (tempProgress * MAX_UPDATES > updateNum) {
                        publishProgress(tempProgress);
                        updateNum++;
                    }

                    tempLocation = itemCursor.getInt(itemLocationIdIndex);

                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                        while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                            locationCursor.moveToNext();
                            if (locationCursor.isAfterLast()) {
                                return "Location of \"" + itemCursor.getString(itemBarcodeIndex).trim() + "\" does not exist";
                            }
                        }

                        printStream.printf("\"%s\"|\"%s\"\r\n", locationCursor.getString(locationBarcodeIndex), locationCursor.getString(locationDateTimeIndex));
                        printStream.flush();
                    }

                    if (BuildConfig.display_quantity) {
                        printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex), itemCursor.getLong(itemQuantityIndex), itemCursor.getString(itemDateTimeIndex));
                    } else {
                        printStream.printf("\"%s\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex), itemCursor.getString(itemDateTimeIndex));
                    }
                    printStream.flush();

                    itemCursor.moveToNext();
                    itemIndex++;
                }

                if (outputFile.exists() && !outputFile.delete()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not delete existing output file");
                    return "Could not delete existing output file";
                }

                Utils.refreshExternalPath(InventoryActivity.this.getApplicationContext(), outputFile);

                if (!TEMP_OUTPUT_FILE.renameTo(outputFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not rename temp file to \"" + outputFile.getName() + "\"");
                    return "Could not rename temp file to \"" + outputFile.getName() + "\"";
                }

                Utils.refreshExternalPath(InventoryActivity.this.getApplicationContext(), outputFile);
            } catch (FileNotFoundException e){
                e.printStackTrace();
                return "FileNotFoundException occurred while saving";
            } catch (IOException e){
                e.printStackTrace();
                return "IOException occurred while saving";
            } finally {
                if (printStream != null)
                    printStream.close();
                itemCursor.close();
                locationCursor.close();
            }

            //Log.v(TAG, "Saved to: " + outputFile.getAbsolutePath());
            return "Saved to file";
        }

        @Override
        protected void onProgressUpdate(Float... progress) {
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressBar.setProgress(progress[0],true);
                progressBar.animate();
            } else*/ {
                progressBar.setProgress((int) (progress[0] * progressBar.getMax()));
            }
        }

        protected void onPostExecute(String result) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(InventoryActivity.this, result, Toast.LENGTH_SHORT).show();
            if (changedSinceLastArchive)
                archiveDatabase();
            postSave();
            //MediaScannerConnection.scanFile(InventoryActivity.this, new String[]{outputFile.getAbsolutePath()}, null, null);
        }

        @Override
        protected void onCancelled(String s) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(InventoryActivity.this, s, Toast.LENGTH_SHORT).show();
            postSave();
        }
    }

    private void archiveDatabase() {
        //noinspection ResultOfMethodCallIgnored
        //archiveDirectory.mkdirs();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 1) {
                        if (saveTask == null) {
                            preSave();
                            archiveDatabase();
                            (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                            saveTask = new SaveToFileTask().execute();
                        } else {
                            saveTask.cancel(false);
                            postSave();
                        }
                    } else if (requestCode == 2) {
                        preInit();
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
