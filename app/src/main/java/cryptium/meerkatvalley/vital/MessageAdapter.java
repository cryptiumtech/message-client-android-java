package cryptium.meerkatvalley.vital;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedDeque;

import de.hdodenhof.circleimageview.CircleImageView;

import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    Vital mApp;
    private static final String TAG = "MessageAdapter";
    private ConcurrentLinkedDeque<Message> mMessageList;
    private Context mContext;

    public MessageAdapter(Context context, ConcurrentLinkedDeque<Message> messageList) {
        this.mContext = context;
        this.mMessageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
//        return mMessageList.get(position).type;
        return DequeUtil.get(mMessageList, position).type;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch(viewType) {
            case MESSAGE_TYPE_SENT:
            case MESSAGE_TYPE_DRAFT:
            case MESSAGE_TYPE_FAILED:
            case MESSAGE_TYPE_QUEUED:
            case MESSAGE_TYPE_OUTBOX:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_single_layout_from, parent, false);
                break;
            case MESSAGE_TYPE_ALL:
            case MESSAGE_TYPE_INBOX:
                default:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_single_layout_to, parent, false);
                break;
        }
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        mApp = (Vital) mContext.getApplicationContext();

        // Configure MMS for UI
        Message message = DequeUtil.get(mMessageList, position);
        if (message.mms != null) {
            Uri imageUri = mApp.getImageUri(mContext, message.mms);
            Picasso.with(mContext).load(imageUri).into(holder.messageMms);
        }
        // Configure SMS for UI
        holder.messageBody.setText(message.body);

        // Configure date for UI
        Long timestamp = message.getDate();
        Instant instant = Instant.ofEpochMilli(timestamp);
        Date date = Date.from(instant);
        String delims = "[ ]+";
        String[] tokens = date.toString().split(delims);
        String dateText = tokens[1] + " " + tokens[2];
        holder.messageDate.setText(dateText);

        // Configure photo for UI
        if (message.photo != null && holder.messagePhoto != null) {
            Uri photoUri = message.photo;
            Picasso.with(mContext).load(photoUri).placeholder(R.drawable.default_user).into(holder.messagePhoto);
        } else if (holder.messagePhoto != null){
            holder.messagePhoto.setImageResource(R.drawable.default_user);
            String tab = mApp.findTab(message.address);
            if (tab.equals(Vital.TAB_FAVORITES)) {
                holder.messagePhoto.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorGreen));
            }
            if (tab.equals(Vital.TAB_CONTACTS)) {
                holder.messagePhoto.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorBlue));
            }
            if (tab.equals(Vital.TAB_UNKNOWN)) {
                holder.messagePhoto.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorRed));
            }
        }
    }

    @Override
    public int getItemCount() {
        return mMessageList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView messagePhoto;
        TextView messageBody;
        TextView messageDate;
        ImageView messageMms;
        ConstraintLayout parentLayout;
        public ViewHolder(View itemView) {
            super(itemView);
            messagePhoto = itemView.findViewById(R.id.message_single_photo);
            messageBody = itemView.findViewById(R.id.message_single_body);
            messageMms = itemView.findViewById(R.id.message_single_mms);
            messageDate = itemView.findViewById(R.id.message_single_date);
            parentLayout = itemView.findViewById(R.id.parent_layout);
        }
    }
}
