package com.porterlee.mobileinventory;

public class ScannedItemsDatabase {
    public static final String FILE_NAME = "scanned_items.db";

    public class BarcodesTable {
        public static final String NAME = "barcodes";
        public static final String TABLE_CREATION = NAME + " ( " + Keys.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + Keys.BARCODE + " TEXT, " + Keys.DESCRIPTION + " TEXT )";
        public class Keys {
            public static final String ID = "id";
            public static final String BARCODE = "barcode";
            public static final String DESCRIPTION = "description";
        }
    }

    public class PhotosTable {
        public static final String NAME = "photos";
        public static final String TABLE_CREATION = NAME + " ( " + Keys.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + Keys.PHOTO + " BLOB )";
        public class Keys {
            public static final String ID = "id";
            public static final String PHOTO = "PHOTO";

        }
    }
}
