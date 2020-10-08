package cryptium.meerkatvalley.vital.task;

import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cryptium.meerkatvalley.vital.Chat;
import cryptium.meerkatvalley.vital.Vital;


public class ReadMessages extends AsyncTask<Void, Void, ReadMessages.ReadMessagesResult> {
    private ReadMessagesResultListener listener;
    private Vital mApp;
    private String TAG = "ReadMessages";


    public ReadMessages(ReadMessages.ReadMessagesResultListener listener, Vital mApp) {
        this.listener = listener;
        this.mApp = mApp;
    }

    public interface ReadMessagesResultListener {
        void onReadMessagesResult(ReadMessagesResult result);
    }

    public static class ReadMessagesResult {
        private boolean passed;
        public ReadMessagesResult(boolean passed) {
            this.passed = passed;
        }
        public boolean isPassed() {
            return passed;
        }
    }

    @Override
    protected void onPreExecute() {
        Log.d(TAG, "ReadMessages starting...");
        super.onPreExecute();
    }

    protected ReadMessagesResult doInBackground(Void... params) {
        String sortOrder = Telephony.TextBasedSmsColumns.DATE + " desc";
        final Cursor cursor = mApp.getContentResolver().query(Uri.parse("content://sms/"), null, null, null, sortOrder);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mApp.processSmsDatabase(cursor);
                return new ReadMessagesResult(true);
            } else {
                Log.d(TAG, "content://sms/ is empty");
                return null;
            }
        } else {
            Log.d(TAG, "Cursor is null");
            return null;
        }
    }

    protected void onPostExecute(ReadMessagesResult readMessagesResult) {
        super.onPostExecute(readMessagesResult);
        if( listener != null ) {
            listener.onReadMessagesResult(readMessagesResult);
        }
    }
}
