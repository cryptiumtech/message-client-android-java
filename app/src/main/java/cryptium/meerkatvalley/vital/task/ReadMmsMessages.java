package cryptium.meerkatvalley.vital.task;

import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.util.Log;

import cryptium.meerkatvalley.vital.Vital;


public class ReadMmsMessages extends AsyncTask<Void, Void, ReadMmsMessages.ReadMmsMessagesResult> {
    private ReadMmsMessagesResultListener listener;
    private Vital mApp;
    private String TAG = "ReadMmsMessages";


    public ReadMmsMessages(ReadMmsMessages.ReadMmsMessagesResultListener listener, Vital mApp) {
        this.listener = listener;
        this.mApp = mApp;
    }

    public interface ReadMmsMessagesResultListener {
        void onReadMmsMessagesResult(ReadMmsMessagesResult result);
    }

    public static class ReadMmsMessagesResult {
        private boolean passed;
        public ReadMmsMessagesResult(boolean passed) {
            this.passed = passed;
        }
        public boolean isPassed() {
            return passed;
        }
    }

    @Override
    protected void onPreExecute() {
        Log.d(TAG, "ReadMmsMessages starting...");
        super.onPreExecute();
    }

    protected ReadMmsMessagesResult doInBackground(Void... params) {
        String sortOrder = Telephony.TextBasedSmsColumns.DATE + " desc";
        final Cursor mmsCursor = mApp.getContentResolver().query(Uri.parse("content://mms"), null, null, null, sortOrder);
        if (mmsCursor != null) {
            if (mmsCursor.moveToFirst()) {
                mApp.processSmsDatabase(mmsCursor);
                return new ReadMmsMessagesResult(true);
            } else {
                Log.d(TAG, "content://mms/ is empty");
                return null;
            }
        } else {
            Log.d(TAG, "Cursor is null");
            return null;
        }
    }

    protected void onPostExecute(ReadMmsMessagesResult readMmsMessagesResult) {
        super.onPostExecute(readMmsMessagesResult);
        if( listener != null ) {
            listener.onReadMmsMessagesResult(readMmsMessagesResult);
        }
    }
}
