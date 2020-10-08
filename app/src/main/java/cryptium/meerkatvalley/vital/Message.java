package cryptium.meerkatvalley.vital;

import android.graphics.Bitmap;
import android.net.Uri;

public class Message implements Comparable<Message>{
    public int smsId;
    public String address;
    public String date;
    public String seen;
    public String body;
    public Uri photo;
    public Bitmap mms;
    public int type;

    public long getDate() {
        return Long.parseLong(date);
    }

    // Most recent is last (end)
    public int compareTo(Message compareMessage) {
        long compareDate = compareMessage.getDate();
        long result = Long.parseLong(this.date) - compareDate;
        if (result < 0) {
            return -1;
        }
        if (result > 0) {
            return 1;
        }
        else {
            return 0;
        }
    }
}
