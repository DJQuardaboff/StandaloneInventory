package com.porterlee.mobileinventory;

import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;


public class MainActivity extends AppCompatActivity {
    protected static final String ITEM_ID = "id";
    protected static final String ITEM_READY = "ready";
    protected static final String ITEM_BARCODE = "barcode";
    protected static final String ITEM_DESCRIPTION = "description";
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    private ArrayList<HashMap<String, Object>> items = new ArrayList<>();
    //private ArrayList<HashMap<String, Object>> photos = new ArrayList<>();
    private SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(":memory:", null);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                return (Long) items.get(i).get(ITEM_ID);
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
                                itemRecyclerAdapter.notifyDataSetChanged();
                                items.remove(index);
                                itemRecyclerAdapter.notifyItemRemoved(index);
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

    public void addBarcodeItem(int index, @NonNull String barcode, @Nullable String description) {

        HashMap<String, Object> hash = new HashMap<>();
        hash.put(ITEM_READY, true);
        hash.put(ITEM_BARCODE, barcode);
        hash.put(ITEM_DESCRIPTION, description);
        items.add(index, hash);
        itemRecyclerAdapter.notifyItemInserted(0);
        itemRecyclerView.scrollToPosition(0);
    }

    public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }

    public void randomScan(View view) {
        Random r = new Random();
        addBarcodeItem(0, "tnyc000" + r.nextInt(0xffff), null);
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

        void bindViews(HashMap<String, Object> item) {
            Object ready = item.get(ITEM_READY);
            Object barcode = item.get(ITEM_BARCODE);
            Object description = item.get(ITEM_DESCRIPTION);
            //System.out.println((String) ready);
            if (ready != null && (Boolean) ready) {
                progressLoading.setVisibility(View.GONE);
                if (barcode != null) {
                    itemBarcode.setText((String) barcode);
                    itemBarcode.setVisibility(View.VISIBLE);
                } else itemBarcode.setVisibility(View.GONE);
                if (description != null) {
                    itemDescription.setText((String) description);
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

