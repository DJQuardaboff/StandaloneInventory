package com.porterlee.mobileinventory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PLCRFID/mobileinventory");
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    private ArrayList<Pair<ContentValues, Boolean>> items = new ArrayList<>();
    //private ArrayList<HashMap<String, Object>> photos = new ArrayList<>();
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + ScannedItemsDatabase.FILE_NAME, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedItemsDatabase.BarcodesTable.TABLE_CREATION);

        Cursor cursor = db.rawQuery("SELECT * FROM " + ScannedItemsDatabase.BarcodesTable.NAME,null);

        cursor.moveToFirst();

        final int idIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.ID);
        final int barcodeIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE);
        final int descriptionIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION);

        while (!cursor.isAfterLast()) {
            ContentValues values = new ContentValues();
            values.put(ScannedItemsDatabase.BarcodesTable.Keys.ID, cursor.getLong(idIndex));
            values.put(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE, cursor.getString(barcodeIndex));
            values.put(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION, cursor.getString(descriptionIndex));
            items.add(new Pair<>(values, true));
            cursor.moveToNext();
        }

        cursor.close();

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                return (Long) items.get(i).first.get(ScannedItemsDatabase.BarcodesTable.Keys.ID);
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
                final SimpleViewHolder holder = new SimpleViewHolder(itemLayoutView);
                holder.setIsRecyclable(false);
                itemLayoutView.findViewById(R.id.menu_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(MainActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.popup_menu, popup.getMenu());
                        popup.getMenu().getItem(2).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                int index = holder.getAdapterPosition();
                                removeBarcodeItem(index);
                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                return holder;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((SimpleViewHolder) holder).bindViews(items.get(position));
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        itemRecyclerView.setItemAnimator(new SimpleItemAnimator());
    }

    public boolean removeBarcodeItem(int index) {
        if (db.delete(ScannedItemsDatabase.BarcodesTable.NAME, ScannedItemsDatabase.BarcodesTable.Keys.ID + " = " + items.get(index).first.getAsLong(ScannedItemsDatabase.BarcodesTable.Keys.ID), null) > 0) {
            itemRecyclerAdapter.notifyDataSetChanged();
            items.remove(index);
            itemRecyclerAdapter.notifyItemRemoved(index);
        } else return false;
        return true;
    }

    public boolean addBarcodeItem(int index, @NonNull String barcode, @Nullable String description) {
        ContentValues values = new ContentValues();
        values.put(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE, barcode);
        values.put(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION, description);

        if (db.insert(ScannedItemsDatabase.BarcodesTable.NAME, null, values) == -1) return false;
        Cursor cursor = db.rawQuery("SELECT * FROM " + ScannedItemsDatabase.BarcodesTable.NAME + " ORDER BY " + ScannedItemsDatabase.BarcodesTable.Keys.ID + " DESC LIMIT 1;", null);
        cursor.moveToFirst();
        values.put(ScannedItemsDatabase.BarcodesTable.Keys.ID, cursor.getInt(cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.ID)));
        cursor.close();

        items.add(index, new Pair<>(values, true));
        itemRecyclerAdapter.notifyItemInserted(0);
        itemRecyclerView.scrollToPosition(0);
        return true;
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

    public void randomScan(View view) {
        addBarcodeItem(0, "tnyc000" + new Random().nextInt(0xffff), null);
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

        void bindViews(Pair<ContentValues, Boolean> item) {
            ContentValues values = item.first;
            Boolean ready = item.second;
            String barcode = values.getAsString(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE);
            String description = values.getAsString(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION);
            if (ready != null && ready) {
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

    class SimpleItemAnimator extends android.support.v7.widget.SimpleItemAnimator {

        @Override
        public boolean animateRemove(RecyclerView.ViewHolder holder) {
            Animation animation = AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.slide_out_right);
            final RecyclerView.ViewHolder finalHolder = holder;
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) { }

                @Override
                public void onAnimationEnd(Animation animation) {
                    dispatchAddFinished(finalHolder);
                }

                @Override
                public void onAnimationRepeat(Animation animation) { }
            });
            holder.itemView.startAnimation(animation);
            return false;
        }

        @Override
        public boolean animateAdd(RecyclerView.ViewHolder holder) {
            Animation animation = AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.slide_in_left);
            final RecyclerView.ViewHolder finalHolder = holder;
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) { }

                @Override
                public void onAnimationEnd(Animation animation) {
                    dispatchAddFinished(finalHolder);
                }

                @Override
                public void onAnimationRepeat(Animation animation) { }
            });
            holder.itemView.startAnimation(animation);
            return false;
        }

        @Override
        public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            dispatchAddFinished(holder);
            return false;
        }

        @Override
        public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromLeft, int fromTop, int toLeft, int toTop) {
            dispatchAddFinished(oldHolder);
            dispatchAddFinished(newHolder);
            return false;
        }

        @Override
        public void runPendingAnimations() {

        }

        @Override
        public void endAnimation(RecyclerView.ViewHolder item) {

        }

        @Override
        public void endAnimations() {

        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}

