package cryptium.meerkatvalley.vital;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.concurrent.ConcurrentLinkedDeque;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String SMS_SENT = "android.provider.Telephony.SMS_SENT";
    private static final String SMS_DELIVER = "android.provider.Telephony.SMS_DELIVER";
    private static final String TAG = "SmsBroadcastReceiver";
    Vital mApp;

    @Override
    public void onReceive(Context context, Intent intent) {
//        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
//            boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
//            if(noConnectivity) {
//                Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show();
//            }
//        }
        mApp = (Vital) context.getApplicationContext();
        Log.d(TAG, "smsBroadcastReceiver: " + intent.getAction());
        if (intent.getAction() != null && intent.getAction().equals(SMS_DELIVER) || intent.getAction() != null && intent.getAction().equals(SMS_RECEIVED) && !mApp.isDefault) {
            SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (smsMessages != null) {
                for(SmsMessage message : smsMessages) {
                    if (message == null) {
                        continue;
                    }
                    String smsOriginatingAddress = message.getDisplayOriginatingAddress();
                    String smsDisplayMessage = message.getDisplayMessageBody();
                    Log.d(TAG, "receivedMessage: " + smsOriginatingAddress + "   " + smsDisplayMessage);
                    Message receivedMessage = refreshChat(context, smsOriginatingAddress, smsDisplayMessage, MESSAGE_TYPE_INBOX);
                    Intent receivedIntent = new Intent(Vital.MESSAGE_RECEIVED);
                    receivedIntent.putExtra("address", receivedMessage.address);
                    context.sendBroadcast(receivedIntent);
                    DatabaseHelper sqlDatabase = new DatabaseHelper(context);
                    Chat chat = sqlDatabase.getChatByAddress(smsOriginatingAddress);
                    // if chat is null, create it
                    if (chat == null || chat.tab == null) {
                        Log.d(TAG, "chat == null");
                        chat = new Chat();
                        chat.address = smsOriginatingAddress;
                        Contact contact = mApp.getContactMap().get(chat.address);
                        if (contact != null) {
                            Log.d(TAG, "contact: " + contact.name);
                            if (contact.name != null) {
                                chat.name = contact.name;
                            }
                        }
                        chat.tab = mApp.findTab(smsOriginatingAddress);
                        chat.seen = "0";
                        chat.messageList = new ConcurrentLinkedDeque<>();
                    }
                    Log.d(TAG, "onReceive tab: " + chat.tab);
                    if (chat.tab.equals(Vital.TAB_FAVORITES) || chat.tab.equals(Vital.TAB_CONTACTS)) {
                        initNotification(context, receivedMessage, chat);
                    }
                }
            }
        }
    }

    public Message refreshChat(Context context, String address, String body, int type) {
        Message message = new Message();
        message.address = address;
        message.body = body;
        message.date = String.valueOf(System.currentTimeMillis());
        message.seen = "0";
        message.type = type;
        Chat chat = mApp.processMessage(message);
        Contact contact = mApp.getContactMap().get(message.address);
        if (contact != null) {
            Bitmap photoBitmap = mApp.getPhoto(Long.parseLong(contact.id));
            if (photoBitmap != null) {
                message.photo = mApp.getImageUri(context, photoBitmap);
            }
        }
        if (type == MESSAGE_TYPE_INBOX) {
            addSmsToDatabase(context, message.address, message.body, message.seen, message.date, "content://sms/inbox");
        }
        if (type == MESSAGE_TYPE_SENT) {
            addSmsToDatabase(context, message.address, message.body, message.seen, message.date, "content://sms/sent");
        }
//        mApp.sortChatList(chat.tab);
        Intent sortIntent = new Intent(Vital.CHAT_SORTED);
        sortIntent.putExtra("tab", chat.tab);
        mApp.sendBroadcast(sortIntent);
        return message;
    }

    public void addSmsToDatabase(Context context, String address, String body, String read, String date, String destination) {
        ContentValues values = new ContentValues();
        values.put("address", address);
        values.put("body", body);
        values.put("read", read);
        values.put("date", date);
        context.getContentResolver().insert(Uri.parse(destination), values);
    }

    public void initNotification(Context context, Message message, Chat chat) {
        Log.d(TAG, "initNotification");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if chat is in favorites
            if (chat.tab.equals(Vital.TAB_FAVORITES)) {
                NotificationPreference notificationPreference = new NotificationPreference();
                notificationPreference.importance = NotificationManager.IMPORTANCE_HIGH;
                notificationPreference.sound = true;
                notificationPreference.vibrate = true;
                notificationPreference.color = context.getResources().getColor(R.color.colorGreen);
                notificationPreference.channel = "CHANNEL_ID_FAVORITES";
                sendNotification(context, notificationPreference, message, chat);
            }
            // if chat is in contacts
            else {
                NotificationPreference notificationPreference = new NotificationPreference();
                notificationPreference.importance = NotificationManager.IMPORTANCE_LOW;
                notificationPreference.sound = false;
                notificationPreference.vibrate = true;
                notificationPreference.color = context.getResources().getColor(R.color.colorBlue);
                notificationPreference.channel = "CHANNEL_ID_CONTACTS";
                sendNotification(context, notificationPreference, message, chat);
            }
        }
    }

    public void sendNotification(Context context, NotificationPreference notificationPreference, Message message, Chat chat) {
        Log.d(TAG, "sendNotification");
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, notificationPreference.channel)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setColor(notificationPreference.color)
                        .setContentText(message.body)
                        .setLights(notificationPreference.color, 3000, 3000)
                        .setAutoCancel(true);
        if (chat.name != null) {
            builder.setContentTitle("Message from " + chat.name);
        }
        if (chat.name == null) {
            builder.setContentTitle("Message from " + message.address);
        }
        if (notificationPreference.sound) {
            if (mApp.chosenRingtone == null) {
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                builder.setSound(alarmSound);
            } else {
                Uri alarmSound = Uri.parse(mApp.chosenRingtone);
                builder.setSound(alarmSound);
            }
        }
        if (notificationPreference.vibrate) {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(300);
        }
        Intent resultIntent = new Intent(context, MessageActivity.class);
        resultIntent.putExtra("address", message.address);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(resultPendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(notificationPreference.channel, notificationPreference.channel, notificationPreference.importance);

//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//                notificationChannel.enableLights(true);
//                notificationChannel.setLightColor(notificationPreference.color);
//                notificationChannel.enableVibration(true);
//                notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
//                if (mApp.chosenRingtone != null) {
//                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
//                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                            .setUsage(AudioAttributes.USAGE_ALARM)
//                            .build();
//                    notificationChannel.setSound(Uri.parse(mApp.chosenRingtone), audioAttributes);
//                }
//            }

        notificationManager.createNotificationChannel(notificationChannel);
        int notificationId = message.address.hashCode();
        Log.d(TAG, "notificationManager.notify");
        if (getActiveNotification(context, notificationId) != null) {
            notificationManager.cancel(notificationId);
            notificationManager.notify(notificationId, builder.build());
        } else {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public Notification getActiveNotification(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] barNotifications = notificationManager.getActiveNotifications();
        for(StatusBarNotification notification: barNotifications) {
            if (notification.getId() == notificationId) {
                return notification.getNotification();
            }
        }
        return null;
    }
}
