package cryptium.meerkatvalley.vital;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import cryptium.meerkatvalley.vital.task.ReadMessages;
import cryptium.meerkatvalley.vital.task.ReadMmsMessages;


public class MainActivity extends AppCompatActivity {

    Vital mApp;
    private ProgressBar toolbarProgressBar;
    private Toolbar mToolbar;
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private static final String TAG = "MainActivity";
    final int DEFAULT_REQUEST_CODE = 0;
    final int PERMISSIONS_REQUEST_CODE = 1;
    final int CHANGE_NOTIFICATION_REQUEST_CODE = 2;
    final String[] PERMISSIONS = {
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.VIBRATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbarProgressBar = findViewById(R.id.toolbarProgressBar);
        toolbarProgressBar.setVisibility(View.VISIBLE);

        mToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(Vital.TAB_FAVORITES);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager = findViewById(R.id.main_tabPager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        mTabLayout = findViewById(R.id.main_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        // this is for setting Toolbar title when you click fragment tabs
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        mToolbar.setTitle(Vital.TAB_FAVORITES);
                        break;
                    case 1:
                        mToolbar.setTitle(Vital.TAB_CONTACTS);
                        break;
                    case 2:
                        mToolbar.setTitle(Vital.TAB_UNKNOWN);
                        break;
                }
            }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        mToolbar.setTitle(Vital.TAB_FAVORITES);
                        break;
                    case 1:
                        mToolbar.setTitle(Vital.TAB_CONTACTS);
                        break;
                    case 2:
                        mToolbar.setTitle(Vital.TAB_UNKNOWN);
                        break;
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                mToolbar.setTitle("Messages");
            }
        });

        FloatingActionButton newMessageButton = findViewById(R.id.newMessageButton);
        newMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent newMessageIntent = new Intent(MainActivity.this, NewMessage.class);
                startActivity(newMessageIntent);
            }
        });

        if (!checkPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length == PERMISSIONS.length && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initApplication();
            } else {
                finish();
            }
            return;
        }
    }

    // Initialize Vital app class, get contacts uri and ask for default app
    public void initApplication() {
        mApp = (Vital) getApplication();
        if (!mApp.isProcessed) {
            mApp.initContactMap();
            readMessages();
//            readMmsMessages();
        }
        mApp.isProcessed = true;
        MyContentObserver contentObserver = new MyContentObserver();
        getApplicationContext().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contentObserver);
        Intent setSmsAppIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        startActivityForResult(setSmsAppIntent, DEFAULT_REQUEST_CODE);
        mApp.isDefault = Vital.isDefaultSmsApp(getApplicationContext());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == DEFAULT_REQUEST_CODE) {
        }
        if (resultCode == Activity.RESULT_OK && requestCode == CHANGE_NOTIFICATION_REQUEST_CODE)
        {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null)
            {
                mApp.chosenRingtone = uri.toString();
            }
            else
            {
                mApp.chosenRingtone = null;
            }
        }
    }

    public void readMessages() {
        final ReadMessages.ReadMessagesResultListener callback = new ReadMessages.ReadMessagesResultListener() {
            @Override
            public void onReadMessagesResult(ReadMessages.ReadMessagesResult result) {
                mApp.isSmsRead = true;
                if (mApp.isSmsRead /*&& mApp.isMmsRead*/) {
                    toolbarProgressBar.setVisibility(View.INVISIBLE);
                }
            }
        };
        ReadMessages readMessages = new ReadMessages(callback, mApp);
        readMessages.execute();
    }

    public void readMmsMessages() {
        final ReadMmsMessages.ReadMmsMessagesResultListener callback = new ReadMmsMessages.ReadMmsMessagesResultListener() {
            @Override
            public void onReadMmsMessagesResult(ReadMmsMessages.ReadMmsMessagesResult result) {
                mApp.isMmsRead = true;
                if (mApp.isSmsRead && mApp.isMmsRead) {
                    toolbarProgressBar.setVisibility(View.INVISIBLE);
                }
            }
        };
        ReadMmsMessages readMmsMessages = new ReadMmsMessages(callback, mApp);
        readMmsMessages.execute();
    }

    private class MyContentObserver extends ContentObserver {
        public MyContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange);
            if (uri.toString().equals("content://com.android.contacts")) {
                mApp.initContactMap();
            }
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.main_change_notification) {
            changeNotification();
        }
//        if (id == R.id.main_change_theme) {
//            changeTheme();
//        }
        if (id == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public static class PlaceholderFragment extends Fragment {
        private static final String ARG_SECTION_NUMBER = "section_number";
        public PlaceholderFragment() {
        }
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }
    }

    public void changeNotification() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
        startActivityForResult(intent, CHANGE_NOTIFICATION_REQUEST_CODE);
    }

    public void changeTheme() {
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            setTheme(R.style.LightTheme);
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            setTheme(R.style.AppTheme);
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
        }
        setContentView(R.layout.activity_main);
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = null;
            switch (position) {
                case 0:
                    // favorites
                    fragment = new ChatListFragment();
                    Bundle favoritesBundle = new Bundle();
                    favoritesBundle.putString("tab", Vital.TAB_FAVORITES);
                    fragment.setArguments(favoritesBundle);
                    break;
                case 1:
                    // contacts
                    fragment = new ChatListFragment();
                    Bundle contactsBundle = new Bundle();
                    contactsBundle.putString("tab", Vital.TAB_CONTACTS);
                    fragment.setArguments(contactsBundle);
                    break;
                case 2:
                    // unknown
                    fragment = new ChatListFragment();
                    Bundle unknownBundle = new Bundle();
                    unknownBundle.putString("tab", Vital.TAB_UNKNOWN);
                    fragment.setArguments(unknownBundle);
                    break;
            }
            return fragment;
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        // this is for tab titles
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return Vital.TAB_FAVORITES;
                case 1:
                    return Vital.TAB_CONTACTS;
                case 2:
                    return Vital.TAB_UNKNOWN;
            }
            return null;
        }
    }

    public static boolean checkPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
}
