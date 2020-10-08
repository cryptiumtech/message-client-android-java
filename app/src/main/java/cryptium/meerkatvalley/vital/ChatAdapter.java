package cryptium.meerkatvalley.vital;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedDeque;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private static final String TAG = "ChatAdapter";
    private ConcurrentLinkedDeque<Chat> mChatList;
    private Context mContext;
    Vital mApp;

    public ChatAdapter(Context context, ConcurrentLinkedDeque<Chat> chatList) {
        this.mContext = context;
        this.mChatList = chatList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_single_layout, parent, false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        mApp = (Vital) mContext.getApplicationContext();
        final Chat chat = DequeUtil.get(mChatList, position);

        // Configure address for UI
        final String address = chat.address;
        Contact contact = mApp.getContactMap().get(address);
        if (contact != null) {
            holder.userName.setText(contact.name);
        } else {
            String newAddress = address;
            holder.userName.setText(newAddress);
        }

        // Configure body for UI
        holder.userBody.setText(chat.messageList.getFirst().body);
        Log.d(TAG, "mChatList seen: " + chat.seen);
        if (chat.seen.equals("0")) {
            holder.userBody.setTypeface(holder.userBody.getTypeface(), Typeface.BOLD);
        } else if (chat.seen.equals("1")) {
            holder.userBody.setTypeface(holder.userBody.getTypeface(), Typeface.NORMAL);
        }

        // Configure date for UI
        Long timestamp = chat.messageList.getFirst().getDate();
        Instant instant = Instant.ofEpochMilli(timestamp);
        Date date = Date.from(instant);
        String delims = "[ ]+";
        String[] tokens = date.toString().split(delims);
        String dateText = tokens[1] + " " + tokens[2];
        holder.userDate.setText(dateText);

        // Configure photo for UI
        if (chat.messageList.getFirst().photo != null && holder.userPhoto != null) {
            Uri photoUri = chat.messageList.getFirst().photo;
            Picasso.with(mContext).load(photoUri).placeholder(R.drawable.default_user).into(holder.userPhoto);
        } else if (holder.userPhoto != null){
            holder.userPhoto.setImageResource(R.drawable.default_user);
            if (chat.tab.equals(Vital.TAB_FAVORITES)) {
                holder.userPhoto.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorGreen));
            }
            if (chat.tab.equals(Vital.TAB_CONTACTS)) {
                holder.userPhoto.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorBlue));
            }
            if (chat.tab.equals(Vital.TAB_UNKNOWN)) {
                holder.userPhoto.setCircleBackgroundColor(ContextCompat.getColor(mContext, R.color.colorRed));
            }
        }

        // Tap to open message
        holder.parentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view.getContext();
                Intent intent = new Intent(context, MessageActivity.class);
                intent.putExtra("address", address);
                context.startActivity(intent);

            }
        });

        holder.parentLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openChatDialog(position, chat);
                return true;
            }
        });
    }

    public void openChatDialog(final int position, final Chat chat) {
        final String address = chat.address;
//        String body = mChatList.get(position).messageList.get(mChatList.get(position).messageList.size() - 1).body;
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete this chat?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean result = deleteChat(mContext, address);
                        if (result) {
                            Toast.makeText(mContext, "Chat deleted " + address, Toast.LENGTH_SHORT).show();
                            mChatList.remove(chat);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, getItemCount());
                        }
                        else {
                            Toast.makeText(mContext, "Error deleting chat", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int result = 0;
                    }
                });
        builder.show();
//        return builder.create();
    }

    public boolean deleteChat(Context context, String number) {
        Boolean chatDeleted = false;
        try {
            Uri uriSms = Uri.parse("content://sms");
            Cursor c = context.getContentResolver().query(uriSms,
                    new String[] { "_id", "thread_id", "address",
                            "person", "date", "body" }, null, null, null);
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(0);
                    long threadId = c.getLong(1);
                    String address = c.getString(2);

                    if (address.equals(number) || address.equals(number.substring(2))) {
                        context.getContentResolver().delete(
                                Uri.parse("content://sms/" + id), null, null);
                        chatDeleted = true;
                    }
                } while (c.moveToNext());
                return chatDeleted;
            }
        } catch (Exception e) {
        }
        return chatDeleted;
    }

    public void onDialogPositivePressed(int result) {
    }

    @Override
    public int getItemCount() {
        return mChatList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView userPhoto;
        TextView userName;
        TextView userBody;
        TextView userDate;
        ConstraintLayout parentLayout;
        public ViewHolder(View itemView) {
            super(itemView);
            userPhoto = itemView.findViewById(R.id.user_single_photo);
            userName = itemView.findViewById(R.id.user_single_name);
            userBody = itemView.findViewById(R.id.user_single_message);
            userDate = itemView.findViewById(R.id.user_single_date);
            parentLayout = itemView.findViewById(R.id.parent_layout);
        }
    }
}
