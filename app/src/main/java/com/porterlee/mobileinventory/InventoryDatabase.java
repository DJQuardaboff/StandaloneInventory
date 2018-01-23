package com.porterlee.mobileinventory;

public class InventoryDatabase {
    public static final String FILE_NAME = "inventory.db";

    public class BarcodesTable {
        public static final String NAME = "barcodes";
        public static final String TABLE_CREATION = NAME + " ( " + Keys.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + Keys.BARCODE + " TEXT, " + Keys.LOCATION_ID + " INTEGER, " + Keys.DESCRIPTION + " TEXT )";
        public class Keys {
            public static final String ID = "id";
            public static final String BARCODE = "barcode";
            public static final String LOCATION_ID = "location";
            public static final String DESCRIPTION = "description";
        }
    }

    public class LocationsTable {
        public static final String NAME = "locations";
        public static final String TABLE_CREATION = NAME + " ( " + Keys.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + Keys.BARCODE + " TEXT )";
        public class Keys {
            public static final String ID = "id";
            public static final String BARCODE = "barcode";
        }
    }

    public class PicturesTable {
        public static final String NAME = "pictures";
        public static final String TABLE_CREATION = NAME + " ( " + Keys.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + Keys.PHOTO + " BLOB )";
        public class Keys {
            public static final String ID = "id";
            public static final String PHOTO = "PHOTO";

        }
    }
}
