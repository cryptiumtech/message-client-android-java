package cryptium.meerkatvalley.vital;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

import de.hdodenhof.circleimageview.CircleImageView;

import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT;

public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";
    private Context mContext;
    private Toolbar mToolbar;
    private RecyclerView mMessagesRecyclerView;
    private View mActionBarView;
    private View mMainView;
    private LinearLayoutManager mLinearLayout;
    private EditText mMessageEditText;
    private ImageButton sendButton;
    private ImageButton addButton;
    private Vital mApp;
    private String mUserName;
    private TextView mTitleView;
    private CircleImageView mProfileImage;
    private SwipeRefreshLayout mRefreshLayout;
    private int mCurrentPage = 1;
    private String mAddress;
    private String mMessage;
    private Chat mChat;
    private MessageAdapter mMessageAdapter;
    private Boolean cameFromNewMessage;
    private int DEFAULT_REQUEST_CODE = 0;
    private static final int GALLERY_PICK = 1;
    private int currentPage = 1;
    private int itemPosition = 0;
    private static final int TOTAL_ITEMS_TO_LOAD = 10;

    public MessageActivity() {
        // Required empty public constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        Log.d(TAG, "MessageActivity");

        mApp = (Vital) getApplication();
        mContext = getApplicationContext();
        Intent thisIntent = getIntent();
        String intentAddress = thisIntent.getStringExtra("address");
        cameFromNewMessage = thisIntent.getBooleanExtra("newMessage", false);
        mAddress = mApp.normalizeAddress(intentAddress);
        String newAddress = mAddress;
        mToolbar = findViewById(R.id.chat_toolbar);
        setSupportActionBar(mToolbar);
        mToolbar.setTitle("Chat with X person");
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setTitle("Chat with Y person");
        LayoutInflater actionBarInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mActionBarView = actionBarInflater.inflate(R.layout.message_custom_toolbar, null);
        actionBar.setCustomView(mActionBarView);

        mChat = mApp.getChatMap().get(mAddress);
        if (mChat == null) {
            Log.d(TAG, "chat == null");
            mChat = new Chat();
            mChat.messageList = new ConcurrentLinkedDeque<>();
            mChat.address = mAddress;
            String tab = mApp.findTab(mAddress);
            mChat.tab = tab;
        }
        mChat.seen = "1";

        mTitleView = findViewById(R.id.custom_bar_title);
        mProfileImage = findViewById(R.id.custom_bar_image);
        Contact contact = mApp.getContactMap().get(mAddress);
        if (contact != null) {
            mTitleView.setText(contact.name);
        } else {
            mTitleView.setText(newAddress);
        }
        if (mChat.messageList.getFirst().photo != null) {
            Picasso.with(mContext).load(mChat.messageList.getFirst().photo).placeholder(R.drawable.default_user).into(mProfileImage);
        } else {
            mProfileImage.setImageResource(R.drawable.default_user);
            String tab = mApp.findTab(mAddress);
            if (tab.equals(Vital.TAB_FAVORITES)) {
                mProfileImage.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorGreen));
            }
            if (tab.equals(Vital.TAB_CONTACTS)) {
                mProfileImage.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorBlue));
            }
            if (tab.equals(Vital.TAB_UNKNOWN)) {
                mProfileImage.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorRed));
            }
        }

        mMessageAdapter = new MessageAdapter(this, mChat.messageList);
        mMessagesRecyclerView = findViewById(R.id.messages_list);
        mMessagesRecyclerView.setAdapter(mMessageAdapter);
        mMessagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mLinearLayout = new LinearLayoutManager(this);
        mMessagesRecyclerView.setHasFixedSize(true);
        mMessagesRecyclerView.setLayoutManager(mLinearLayout);
        mLinearLayout.setStackFromEnd(false);
        mRefreshLayout = findViewById(R.id.message_swipe_layout);
        mMessageEditText = findViewById(R.id.chat_message_view);
        addButton = findViewById(R.id.chat_add_btn);
        sendButton = findViewById(R.id.chat_send_btn);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent();
                galleryIntent.setType("image/*");
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(galleryIntent, "SELECT IMAGE"), GALLERY_PICK);
            }
        });
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSend(v);
            }
        });
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                currentPage ++;
                itemPosition = 0;
                loadMoreMessages();
            }
        });

        IntentFilter sentFilter = new IntentFilter(Vital.MESSAGE_SENT);
        registerReceiver(sentBroadcastReceiver, sentFilter);
        IntentFilter receivedFilter = new IntentFilter(Vital.MESSAGE_RECEIVED);
        registerReceiver(receivedBroadcastReceiver, receivedFilter);
    }

    public void loadMoreMessages() {
        // load messages up to currentPage * TOTAL_ITEMS_TO_LOAD
        mRefreshLayout.setRefreshing(false);
    }

    public void onSend(View v) {
        Log.d(TAG, "onSend");
        // If not default app, ask for default
        Intent setSmsAppIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        startActivityForResult(setSmsAppIntent, DEFAULT_REQUEST_CODE);

        // Check for default. if true sendButton message, if false warn user
        mApp.isDefault = Vital.isDefaultSmsApp(getApplicationContext());
        if (mApp.isDefault) {
            String address = mAddress;
            String body = mMessageEditText.getText().toString();
            if(address == null || address.length() == 0 || body == null || body.length() == 0) {
                return;
            }
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(address, null, body, null, null);
            mMessageEditText.setText("");
            mMessageEditText.clearFocus();
            hideKeyboard(this);
//            if(mChat.tab != null){
//                mApp.sortChatList(mChat.tab);
//            }
            Intent sentIntent = new Intent(Vital.MESSAGE_SENT);
            sentIntent.putExtra("address", mChat.address);
            sentIntent.putExtra("body", body);
            sendBroadcast(sentIntent);
        } else {
            Toast.makeText(getApplicationContext(), "Set Vital as default sms app to sendButton", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DEFAULT_REQUEST_CODE && resultCode == RESULT_OK) {
            if (resultCode == -1) {
//                onSend(mMainView);
            }
        }
        if(requestCode == GALLERY_PICK && resultCode == RESULT_OK){
            Uri imageUri = data.getData();
        }
    }

    private BroadcastReceiver sentBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "sentBroadcastReceiver: " + action);
            if (action != null && action.equals(Vital.MESSAGE_SENT)) {
                Message sentMessage = refreshChat(intent, MESSAGE_TYPE_SENT);
                scrollToEnd();
                mChat.seen = "1";
            }
        }
    };

    private BroadcastReceiver receivedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "receivedBroadcastReceiver: " + action);
            if (action != null && action.equals(Vital.MESSAGE_RECEIVED)) {
                scrollToEnd();
                mChat.seen = "1";
            }
        }
    };

    public Message refreshChat(Intent intent, int type) {
        String newAddress = intent.getStringExtra("address");
        String newBody = intent.getStringExtra("body");
//        Bundle dataBundle = intent.getExtras();
//        String newAddress = "";
//        String newBody = "";
//        if (dataBundle != null) {
//            Object[] mypdu = (Object[]) dataBundle.get("pdus");
//            final SmsMessage[] newMessage = new SmsMessage[mypdu.length];
//            for (int i=0; i<mypdu.length; i++) {
//                String format = dataBundle.getString("format");
//                newMessage[i] = SmsMessage.createFromPdu((byte[])mypdu[i], format);
//                newAddress = newMessage[i].getOriginatingAddress();
//                newBody = newMessage[i].getMessageBody();
//            }
            Message message = new Message();
            message.address = newAddress;
            message.body = newBody;
            message.date = String.valueOf(System.currentTimeMillis());
            message.seen = "1";
            message.type = type;
            mChat = mApp.processMessage(message);
            Log.d(TAG, "refresh chat: " +  mChat.messageList.getFirst().body);
            if (type == MESSAGE_TYPE_INBOX) {
                addSmsToDatabase(message.address, message.body, message.seen, message.date, "content://sms/inbox");
            }
            if (type == MESSAGE_TYPE_SENT) {
                addSmsToDatabase(message.address, message.body, message.seen, message.date, "content://sms/sent");
            }
//            mApp.sortChatList(chat.tab);
            Intent sortIntent = new Intent(Vital.CHAT_SORTED);
            sortIntent.putExtra("tab", mChat.tab);
            mApp.sendBroadcast(sortIntent);
            return message;
//        }
//        return null;
    }

    public void addSmsToDatabase(String address, String body, String read, String date, String destination) {
        ContentValues values = new ContentValues();
        values.put("address", address);
        values.put("body", body);
        values.put("read", read);
        values.put("date", date);
        mContext.getContentResolver().insert(Uri.parse(destination), values);
    }

    public void scrollToEnd() {
        mMessageAdapter.notifyDataSetChanged();
        mMessagesRecyclerView.scrollToPosition(mChat.messageList.size()-1);
        Log.d(TAG, "scrollToEnd: " + mChat.messageList.getFirst().body);
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if (id == android.R.id.home) {
//        }
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    public static void hideKeyboard(Activity activity) {
        View view = activity.findViewById(android.R.id.content);
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receivedBroadcastReceiver);
        unregisterReceiver(sentBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mChat.seen = "1";
        mApp.setCurrentChat(mChat);
        scrollToEnd();
    }
}