package cryptium.meerkatvalley.vital;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.ConcurrentLinkedDeque;

public class DatabaseHelper extends SQLiteOpenHelper {
    final String TAG = "DatabaseHelper";
    public static final String DATABASE_NAME = "Vital.db";
    public static final String CHAT_TABLE = "chat_table";
    public static final String CHAT_ID = "ID";
    public static final String CHAT_ADDRESS = "ADDRESS";
    public static final String CHAT_NAME = "NAME";
    public static final String CHAT_SEEN = "SEEN";
    public static final String CHAT_TAB = "TAB";
//    public static final String CHAT_MESSAGE_LIST = "MESSAGE_LIST";
    public static final String MESSAGE_TABLE = "message_table";
    public static final String MESSAGE_ID = "ID";
    public static final String MESSAGE_SMS_ID = "SMS_ID";
    public static final String MESSAGE_ADDRESS = "ADDRESS";
    public static final String MESSAGE_BODY = "BODY";
    public static final String MESSAGE_DATE = "DATE";
    public static final String MESSAGE_SEEN = "SEEN";
    public static final String MESSAGE_TYPE = "TYPE";

    public DatabaseHelper (Context context) {
        super(context, DATABASE_NAME, null, 1);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + CHAT_TABLE + " (ID INTEGER PRIMARY KEY AUTOINCREMENT, NAME TEXT, ADDRESS TEXT, SEEN TEXT, TAB TEXT)");
        db.execSQL("CREATE TABLE " + MESSAGE_TABLE + " (ID INTEGER PRIMARY KEY AUTOINCREMENT, SMS_ID INTEGER, ADDRESS TEXT, BODY TEXT, DATE TEXT, SEEN TEXT, PHOTO TEXT, TYPE INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade" + oldVersion + "   " + newVersion);
//        if (oldVersion >= 1 && newVersion <= 3) {
//            db.execSQL("ALTER TABLE " + CHAT_TABLE + " ADD NAME TEXT");
//            db.execSQL("ALTER TABLE " + MESSAGE_TABLE + " ADD SMS_ID INTEGER");
//        }
    }

    public boolean insertChatData(String name, String address, String seen, String tab) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(CHAT_NAME, name);
        contentValues.put(CHAT_ADDRESS, address);
        contentValues.put(CHAT_SEEN, seen);
        contentValues.put(CHAT_TAB, tab);
        long result = db.insert(CHAT_TABLE, null, contentValues);
        db.close();
        if (result == -1) {
            Log.d(TAG, "insertChatData failed");
            return false;
        } else {
            Log.d(TAG, "insertChatData success: " + name + " " + address + " " + seen + " " + tab);
            return true;
        }
    }

    public boolean insertMessageData(int smsId, String address, String body, String date, String seen, int type) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MESSAGE_SMS_ID, smsId);
        contentValues.put(MESSAGE_ADDRESS, address);
        contentValues.put(MESSAGE_BODY, body);
        contentValues.put(MESSAGE_DATE, date);
        contentValues.put(MESSAGE_SEEN, seen);
        contentValues.put(MESSAGE_TYPE, type);
        long result = db.insert(MESSAGE_TABLE, null, contentValues);
        db.close();
        if (result == -1) {
            Log.d(TAG, "insertMessageData failed");
            return false;
        } else {
            Log.d(TAG, "insertMessageData success: " + smsId + " " + address + " " + body + " " + date + " " + seen + " " + type);
            return true;
        }
    }

    public String getRecentDate() {
        SQLiteDatabase db = getWritableDatabase();
        String recentDate = null;
        Cursor sqlRecentDateCursor = db.rawQuery("SELECT MAX(DATE) AS MAX_DATE FROM " + MESSAGE_TABLE,null);
        if (sqlRecentDateCursor.moveToFirst()) {
            int dateColumn = sqlRecentDateCursor.getColumnIndex("MAX_DATE");
            if (dateColumn > -1) {
                recentDate = sqlRecentDateCursor.getString(dateColumn);
            }
        }
        db.close();
        return recentDate;
    }

    public Chat getChatByAddress(String address) {
        SQLiteDatabase db = getWritableDatabase();
        Chat chat = new Chat();
        String tab = null;
        String name = null;
        String seen = null;
        Cursor tabCursor = db.rawQuery("SELECT TAB FROM " + CHAT_TABLE + " WHERE ADDRESS=" + address,null);
        if (tabCursor.moveToFirst()) {
            int column = tabCursor.getColumnIndex("TAB");
            if (column > -1) {
                tab = tabCursor.getString(column);
            }
        }
        Cursor nameCursor = db.rawQuery("SELECT NAME FROM " + CHAT_TABLE + " WHERE ADDRESS=" + address,null);
        Log.d(TAG, "nameCursor: " + nameCursor);
        if (nameCursor.moveToFirst()) {
            int column = nameCursor.getColumnIndex("NAME");
            if (column > -1) {
                name = nameCursor.getString(column);
            }
        }
        Cursor seenCursor = db.rawQuery("SELECT SEEN FROM " + CHAT_TABLE + " WHERE ADDRESS=" + address,null);
        if (seenCursor.moveToFirst()) {
            int column = seenCursor.getColumnIndex("SEEN");
            if (column > -1) {
                seen = seenCursor.getString(column);
            }
        }
        chat.name = name;
        chat.address = address;
        chat.tab = tab;
        chat.seen = seen;
        db.close();
        Log.d(TAG, "getChatByAddress: " + chat.name + " " + chat.address + " " + chat.tab + " " + chat.seen);
        return chat;
    }

    public ConcurrentLinkedDeque<Chat> getChats() {
        SQLiteDatabase db = getWritableDatabase();
        Chat chat = new Chat();
        ConcurrentLinkedDeque<Chat> chatList = new ConcurrentLinkedDeque<>();
        Cursor chatsCursor = db.rawQuery("SELECT * FROM " + CHAT_TABLE,null);
        if (chatsCursor.moveToFirst()) {
            do {
                int addressColumn = chatsCursor.getColumnIndex("ADDRESS");
                if (addressColumn > -1) {
                    chat.address = chatsCursor.getString(addressColumn);
                }
                int seenColumn = chatsCursor.getColumnIndex("SEEN");
                if (seenColumn > -1) {
                    chat.seen = chatsCursor.getString(seenColumn);
                }
                int tabColumn = chatsCursor.getColumnIndex("TAB");
                if (tabColumn > -1) {
                    chat.tab = chatsCursor.getString(tabColumn);
                }
                chatList.addLast(chat);
            } while(chatsCursor.moveToNext());
        }
        return chatList;
    }
}
