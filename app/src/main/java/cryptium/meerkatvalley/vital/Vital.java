package cryptium.meerkatvalley.vital;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import cryptium.meerkatvalley.vital.task.ReadMessages;

public class Vital extends Application {
    final String TAG = "Vital";
    final static String CHAT_MOVED = "cryptium.meerkatvalley.vital.CHAT_MOVED";
    final static String CHAT_SORTED = "cryptium.meerkatvalley.vital.CHAT_SORTED";
    final static String MESSAGE_RECEIVED = "cryptium.meerkatvalley.vital.MESSAGE_RECEIVED";
    final static String MESSAGE_SENT = "cryptium.meerkatvalley.vital.MESSAGE_SENT";
    final static String TAB_FAVORITES = "Favorites";
    final static String TAB_CONTACTS = "Contacts";
    final static String TAB_UNKNOWN = "Unknown";
    final ConcurrentLinkedDeque<Chat> mContacts = new ConcurrentLinkedDeque<>();
    final ConcurrentLinkedDeque<Chat> mFavorites = new ConcurrentLinkedDeque<>();
    final ConcurrentLinkedDeque<Chat> mUnknown = new ConcurrentLinkedDeque<>();
    final Map<String, Chat> mChatMap = new HashMap<>();
    final Map<String, Contact> mContactMap = new HashMap<>();
    final Map<String, Contact> mContactMapById = new HashMap<>();
    int updateInterval = 10;
    int updateAdditive = 20;
    boolean isProcessed = false;
    boolean isContactsProcessed = false;
    boolean isDefault;
    String chosenRingtone;
    boolean isSmsRead = false;
    boolean isMmsRead = false;
    private Chat currentChat = null;

    public ConcurrentLinkedDeque<Chat> getContacts() {
        return mContacts;
    }

    public ConcurrentLinkedDeque<Chat> getFavorites() {
        return mFavorites;
    }

    public ConcurrentLinkedDeque<Chat> getUnknown() {
        return mUnknown;
    }

    public Map<String, Chat> getChatMap() {
        return mChatMap;
    }

    // normalized phone number => contact
    public Map<String, Contact> getContactMap() {
        return mContactMap;
    }

    public void setCurrentChat(Chat chat) {
        currentChat = chat;
    }

    public Chat getCurrentChat() {
        return currentChat;
    }

    // normalized phone number => contact
//    public Map<String, Contact> getContactMapById() {
//        return mContactMapById;
//    }

    public static boolean isDefaultSmsApp(Context context) {
        return context.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(context));
    }

    public void insertSqlDatabase(Chat chat) {
        DatabaseHelper sqlDatabase = new DatabaseHelper(this);
        boolean chatResult = sqlDatabase.insertChatData(chat.name, chat.address, chat.seen, chat.tab);
//        Log.d(TAG, "sqlDatabase.insertChatData: " + chatResult + "  " + chat.address + "   " + chat.seen + "   " + chat.tab);
        boolean messageResult = sqlDatabase.insertMessageData(chat.messageList.getFirst().smsId, chat.address, chat.messageList.getFirst().body, chat.messageList.getFirst().date, chat.seen, chat.messageList.getFirst().type);
//        Log.d(TAG, "sqlDatabase.insertMessageData: " + messageResult + "  " + chat.address + "   " + chat.messageList.getFirst().body + "   " + chat.messageList.getFirst().date + "   " + chat.seen + chat.messageList.getFirst().type);
    }

    // get all sms data from android internal sms database via cursor
    public void processSmsDatabase(Cursor cursor) {
        DatabaseHelper sqlDatabase = new DatabaseHelper(this);
        String sqlRecentDate = sqlDatabase.getRecentDate();

        ConcurrentLinkedDeque<Chat> favoritesList = new ConcurrentLinkedDeque<>();
        ConcurrentLinkedDeque<Chat> contactsList = new ConcurrentLinkedDeque<>();
        ConcurrentLinkedDeque<Chat> unknownList = new ConcurrentLinkedDeque<>();
        Map<String, String> messageAttr = new HashMap<>();
        Chat chat;
        int counter = 0;
        do {
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                messageAttr.put(cursor.getColumnName(i), cursor.getString(i));
//                Log.d(TAG, "messageAttr column: " + cursor.getColumnName(i) + "   " + cursor.getString(i));
            }
            Log.d(TAG, "sqlRecentDate: " + sqlRecentDate + " smsRecentDate: " + messageAttr.get("date"));
            if (Long.parseLong(sqlRecentDate) > Long.parseLong(messageAttr.get("date"))) {
                Log.d(TAG, "SQL up to date, breaking");
                processSqlDatabase();
                break;
            }
            chat = processMessageMap(messageAttr);
            if (chat.tab.equals(TAB_FAVORITES)) {
                Log.d(TAG, "TAB_FAVORITES added: " + chat.address);
                favoritesList.add(chat);
            }
            if (chat.tab.equals(TAB_CONTACTS)) {
                Log.d(TAG, "TAB_CONTACTS added: " + chat.address);
                contactsList.add(chat);
            }
            if (chat.tab.equals(TAB_UNKNOWN)) {
                Log.d(TAG, "TAB_UNKNOWN added: " + chat.address);
                unknownList.add(chat);
            }
            if (counter == 0 || counter == updateInterval) {
                Log.d(TAG, "Update: " + counter);
                updateTabIfChanged(TAB_FAVORITES, favoritesList);
                updateTabIfChanged(TAB_CONTACTS, contactsList);
                updateTabIfChanged(TAB_UNKNOWN, unknownList);
                counter = 0;
                favoritesList.clear();
                contactsList.clear();
                unknownList.clear();
                updateInterval += updateAdditive;
            }
            counter++;
            insertSqlDatabase(chat);
            Log.d(TAG, "sqlRecentDate: " + sqlRecentDate + " messageAttr.get(date): " + messageAttr.get("date"));
        } while (cursor.moveToNext());
        updateTabIfChanged(TAB_FAVORITES, favoritesList);
        updateTabIfChanged(TAB_CONTACTS, contactsList);
        updateTabIfChanged(TAB_UNKNOWN, unknownList);
        Log.d(TAG, "finished processSmsDatabase");
    }

    // if sql has most recent message, process rest from sql
    public void processSqlDatabase() {
        Log.d(TAG, "processSQLDatabase beginning: ");
        DatabaseHelper sqlDatabase = new DatabaseHelper(this);
        ConcurrentLinkedDeque<Chat> chatList = sqlDatabase.getChats();
        Log.d(TAG, "processSQLDatabase finished: " + chatList);
        updateTabs(chatList);
    }

    // now update tabs
    public void updateTabs(ConcurrentLinkedDeque<Chat> chatList) {
        if (!chatList.isEmpty()) {
//            sortChatMessageList(chatList);
//            sortChatList(tab);
            int counter = 0;
            for (Chat chat:chatList) {
                if (updateInterval == counter) {
                    Intent sortIntent = new Intent(Vital.CHAT_SORTED);
                    sortIntent.putExtra("tab", chat.tab);
                    sendBroadcast(sortIntent);

                    Intent sortMessageIntent = new Intent(Vital.MESSAGE_RECEIVED);
                    sortMessageIntent.putExtra("address", chat.address);
                    sendBroadcast(sortMessageIntent);
                    Log.d(TAG, "sending broadcasts: " + chat.address + " " +  chat.name + " " + chat.tab);
                    counter = 0;
                }
                counter++;
            }
        } else {
            Log.e(TAG, "chatList is empty");
        }
        Log.d(TAG, "tabs updated");
    }

    public void updateTabIfChanged(String tab, ConcurrentLinkedDeque<Chat> chatList) {
        if (!chatList.isEmpty()) {
//            sortChatMessageList(chatList);
//            sortChatList(tab);
            Intent sortIntent = new Intent(Vital.CHAT_SORTED);
            sortIntent.putExtra("tab", tab);
            sendBroadcast(sortIntent);

            for (Chat chat:chatList) {
                if (chat == currentChat) {
                    Log.d(TAG, "currentChat: " + currentChat.address);
                    Intent sortMessageIntent = new Intent(Vital.MESSAGE_RECEIVED);
                    sortMessageIntent.putExtra("address", chat.address);
                    sendBroadcast(sortMessageIntent);
                    break;
                }
            }
        }
    }

    private String getAddressNumber(String id) {
        String selectionAdd = new String("msg_id=" + id);
        String uriStr = MessageFormat.format("content://mms/{0}/addr", id);
        Uri uriAddress = Uri.parse(uriStr);
        Cursor cAdd = getContentResolver().query(uriAddress, null,
                selectionAdd, null, null);
        String name = null;
        if (cAdd.moveToFirst()) {
            do {
                String number = cAdd.getString(cAdd.getColumnIndex("address"));
                if (number != null) {
                    try {
                        Long.parseLong(number.replace("-", ""));
                        name = number;
                    } catch (NumberFormatException nfe) {
                        if (name == null) {
                            name = number;
                        }
                    }
                }
            } while (cAdd.moveToNext());
        }
        if (cAdd != null) {
            cAdd.close();
        }
        return name;
    }

    private String getMmsText(String id) {
        Uri partURI = Uri.parse("content://mms/part/" + id);
        InputStream is = null;
        StringBuilder sb = new StringBuilder();
        try {
            is = getContentResolver().openInputStream(partURI);
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader reader = new BufferedReader(isr);
                String temp = reader.readLine();
                while (temp != null) {
                    sb.append(temp);
                    temp = reader.readLine();
                }
            }
        } catch (IOException e) {}
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return sb.toString();
    }

    private Bitmap getMmsImage(String _id) {
        Uri partURI = Uri.parse("content://mms/part/" + _id);
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            is = getContentResolver().openInputStream(partURI);
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {}
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return bitmap;
    }

    public Chat processMessageMap(Map<String,String> map) {
        Message message = new Message();
        message.date = (map.get("date"));
        message.seen = (map.get("seen"));
        if (map.get("address") != null) {
            message.address = normalizeAddress(map.get("address"));
        } else {
            message.address = normalizeAddress(map.get("_id"));
        }
        if (map.get("body") != null) {
            message.body = map.get("body");
        } else {
            message.body = getMmsText(map.get("_id"));
        }
        if (map.get(Telephony.TextBasedSmsColumns.TYPE) != null) {
            // 1 is inbox, 2 is sent
            message.type = Integer.valueOf(map.get(Telephony.TextBasedSmsColumns.TYPE));
        } else {
            // 128 is MESSAGE_TYPE_SEND_REQ, 132 is MESSAGE_TYPE_RETRIEVE_CONF
            message.type = Integer.valueOf(map.get(Telephony.BaseMmsColumns.MESSAGE_TYPE));
        }
        String contentType = map.get(Telephony.BaseMmsColumns.CONTENT_TYPE);
        if (contentType != null) {
            if ("image/jpeg".equals(contentType) || "image/bmp".equals(contentType) || "image/gif".equals(contentType)
                    || "image/jpg".equals(contentType) || "image/png".equals(contentType)
                    || "application/vnd.wap.multipart.related".equals(contentType)
                    || "application/vnd.wap.multipart.mixed".equals(contentType)) {
                Bitmap image = getMmsImage(map.get("_id"));
                message.mms = image;
                Log.d(TAG, "image bitmap: " + image);
            }
        }
        Contact contact = getContactMap().get(message.address);
        if (contact != null) {
            Bitmap photoBitmap = getPhoto(Long.parseLong(contact.id));
            if (photoBitmap != null) {
                Uri photoUri = getImageUri(getApplicationContext(), photoBitmap);
                message.photo = photoUri;
            }
        }

        return processMessage(message);
    }

    public Chat processMessage(Message message) {
        Map<String,Chat> chatMap = getChatMap();
        ConcurrentLinkedDeque<Chat> contacts = getContacts();
        ConcurrentLinkedDeque<Chat> favorites = getFavorites();
        ConcurrentLinkedDeque<Chat> unknown = getUnknown();
        message.address = normalizeAddress(message.address);
        Chat chat = chatMap.get(message.address);
        if (chat == null) {
            chat = new Chat();
//            chat.messageList = new ArrayList<>();
            chat.messageList = new ConcurrentLinkedDeque<>();
            chat.address = message.address;
            chatMap.put(chat.address, chat);
            Contact contact = mContactMap.get(chat.address);
            // Separate into lists depending on if this chat.address is in contacts
            if (contact != null) {
                if (contact.starred) {
                    favorites.add(chat);
                    chat.tab = TAB_FAVORITES;
                } else {
                    contacts.add(chat);
                    chat.tab = TAB_CONTACTS;
                }
            } else {
                unknown.add(chat);
                 chat.tab = TAB_UNKNOWN;
            }
        }
        chat.seen = message.seen;
        chat.address = message.address;
        // Adding at the beginning of the list because the messages are coming most recent > least recent
        chat.messageList.addFirst(message);
        Log.d(TAG, "processMessage getFirst: " + chat.messageList.getFirst().body + "   " + chat.messageList.getFirst().address);
        Log.d(TAG, "processMessage getLast: " + chat.messageList.getLast().body + "   " + chat.messageList.getLast().address);
        Log.d(TAG, "messageInfo: " + message.address + "   " + message.body + "   " + message.date + "   " + message.type + "   " + message.mms);
        return chat;
    }

    public void initContactMap() {
        Set<String> existingContactIdSet = mContactMapById.keySet();
        Set<String> updatedContactIdSet = new HashSet<>();
        List<Intent> changes = new ArrayList<>();
        Map<String,Contact> updatedContacts = new HashMap<>();
        ContentResolver cr = getContentResolver();
        String[] projection = new String[] {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.STARRED};
        String selection = ContactsContract.Contacts.HAS_PHONE_NUMBER + "=?";
        String[] selectionArgs = new String[]{"1"};
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, null);
        while (cur != null && cur.moveToNext()) {
            String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
            String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            Contact contact = new Contact();
            contact.name = name;
            contact.id = id;
            int starredInt = cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.STARRED));
            contact.starred = (starredInt == 1);
            updatedContactIdSet.add(id);
            updatedContacts.put(id, contact);
            Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);
            while (pCur != null && pCur.moveToNext()) {
                String phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                String normalizedPhoneNumber = normalizeAddress(phoneNo);
                contact.phoneNumberSet.add(normalizedPhoneNumber);
                mContactMap.put(normalizedPhoneNumber, contact);
            }
            pCur.close();
        }
        cur.close();
        // detect deleted contact by comparing updatedContactIdSet to existingContactIdSet
        Set<String> deletedContactIdSet = new HashSet<>();
        deletedContactIdSet.addAll(existingContactIdSet);
        deletedContactIdSet.removeAll(updatedContactIdSet);
        // check if notifications need to be sent
        if (isContactsProcessed) {
            for(Contact updatedContact : updatedContacts.values()) {
                Contact existing = mContactMapById.get(updatedContact.id);
                if(existing == null) {
                    // new contact: move from unknown to contacts/favorites
                    for(String phoneNumber : updatedContact.phoneNumberSet) {
                        Intent intent = new Intent(CHAT_MOVED);
                        intent.putExtra("from", TAB_UNKNOWN);
                        if(updatedContact.starred) {
                            intent.putExtra("to", TAB_FAVORITES);
                        }
                        else {
                            intent.putExtra("to", TAB_CONTACTS);
                        }
                        intent.putExtra("address", phoneNumber);
                        changes.add(intent);
                    }
                }
                else {
                    // existing contact: move from favorites/contacts to the other
                    for(String phoneNumber : updatedContact.phoneNumberSet) {
                        if(existing.starred != updatedContact.starred) {
                            Intent intent = new Intent(CHAT_MOVED);
                            if(existing.starred) {
                                intent.putExtra("from", TAB_FAVORITES);
                            }
                            else {
                                intent.putExtra("from", TAB_CONTACTS);
                            }
                            if(updatedContact.starred) {
                                intent.putExtra("to", TAB_FAVORITES);
                            }
                            else {
                                intent.putExtra("to", TAB_CONTACTS);
                            }
                            intent.putExtra("address", phoneNumber);
                            changes.add(intent);
                        }
                    }
                }

            }
            // move deleted contacts to unknown tab
            for (String deletedContactId : deletedContactIdSet) {
                Contact deletedContact = mContactMapById.get(deletedContactId);
                for (String deletedPhoneNumber : deletedContact.phoneNumberSet) {
                    Intent intent = new Intent(CHAT_MOVED);
                    if (deletedContact.starred) {
                        intent.putExtra("from", TAB_FAVORITES);
                    } else {
                        intent.putExtra("from", TAB_CONTACTS);
                    }
                    intent.putExtra("to", TAB_UNKNOWN);
                    intent.putExtra("address", deletedPhoneNumber);
                    changes.add(intent);
                    mContactMap.remove(deletedPhoneNumber);

                }
            }
        }
        // commit all changes to the contacts map
        mContactMapById.putAll(updatedContacts);
        for(String deletedContactId : deletedContactIdSet) {
            mContactMapById.remove(deletedContactId);
        }
        // sendButton the changes to the UI
        for(Intent intent : changes) {
            sendBroadcast(intent);
        }
        isContactsProcessed = true;
    }

    // Sorts messages in chat, most recent at end
    public void sortChatMessageList(Collection<Chat> chatCollection) {
//        for(Chat chat : chatCollection) {
//            Collections.sort(chat.messageList);
//        }
    }

    // Sorts chats in tab, most recent at start
//    public void sortChatList(String tab) {
//        switch (tab) {
//            case TAB_FAVORITES:
//                Collections.sort(getFavorites());
//                break;
//            case TAB_CONTACTS:
//                Collections.sort(getContacts());
//                break;
//            case TAB_UNKNOWN:
//                Collections.sort(getUnknown());
//                break;
//        }
//    }

    public String normalizeAddress(String address) {
        String normalizedAddress = address;
        normalizedAddress = normalizedAddress.replace("-", "");
        normalizedAddress = normalizedAddress.replace(" ", "");
        normalizedAddress = normalizedAddress.replace(",", "");
        normalizedAddress = normalizedAddress.replace("#", "");
        normalizedAddress = normalizedAddress.replace("*", "");
        normalizedAddress = normalizedAddress.replace("(", "");
        normalizedAddress = normalizedAddress.replace(")", "");

        // if its 11 digits and starts with a 1, add a + in front
        if (normalizedAddress.length() == 11 && normalizedAddress.charAt(0) == '1') {
            normalizedAddress = "+" + normalizedAddress;
        }
        // if 10 digits, add a +1
        else if (normalizedAddress.length() == 10 ){
            normalizedAddress = "+1" + normalizedAddress;
        }
        return normalizedAddress;
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        Log.d(TAG, "inImage: " + inImage);
//        inImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
//        Bitmap compressedImage = BitmapFactory.decodeStream(outputStream);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, Long.toString(System.currentTimeMillis()), null);
        return Uri.parse(path);
    }

    public long getContactIdFromNumber(String contactNumber, Context context) {
        String UriContactNumber = Uri.encode(contactNumber);
        long phoneContactID = new Random().nextInt();
        Cursor contactLookupCursor = context.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, UriContactNumber),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID}, null, null, null);
        while (contactLookupCursor.moveToNext()) {
            phoneContactID = contactLookupCursor.getLong(contactLookupCursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID));
        }
        contactLookupCursor.close();
        return phoneContactID;
    }

    public Bitmap getPhoto(long contactId) {
        long normalizedContactId = Long.parseLong(Long.toString(contactId).replace("-", ""));
        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, normalizedContactId);
        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        Cursor cursor = this.getContentResolver().query(photoUri,
                new String[] {ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                if (data != null) {
                    return BitmapFactory.decodeStream(new ByteArrayInputStream(data));
                }
            }
        } finally {
            cursor.close();
        }
        return null;

    }

    public String findTab(String address) {
        Contact contact = getContactMap().get(address);
        String tab;
        if (contact != null) {
            if (contact.starred) {
                tab = Vital.TAB_FAVORITES;
            } else {
                tab = Vital.TAB_CONTACTS;
            }
        } else {
            tab = Vital.TAB_UNKNOWN;
        }
        Log.d(TAG, "findTab return: " + tab);
        return tab;
    }
}
