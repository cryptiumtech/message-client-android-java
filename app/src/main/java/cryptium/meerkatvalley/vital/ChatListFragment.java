package cryptium.meerkatvalley.vital;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ChatListFragment extends Fragment {

    Vital mApp;
    private ConcurrentLinkedDeque<Chat> mChatList;
    private RecyclerView mChatListRecyclerView;
    private View mMainView;
    final private String TAG = "ChatListFragment";
    private String mTab; // "Favorites", "Contacts", "Unknown"
    private Chat mChat;
    ChatAdapter mChatAdapter;

    public ChatListFragment() {
        // Required empty public constructor
    }

    @Override
    public void setArguments(Bundle bundle) {
        mTab = bundle.getString("tab");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApp = (Vital) getActivity().getApplication();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mMainView = inflater.inflate(R.layout.fragment_chatlist, container, false);
        initRecyclerView();
        IntentFilter movedFilter = new IntentFilter(Vital.CHAT_MOVED);
        IntentFilter sortedFilter = new IntentFilter(Vital.CHAT_SORTED);
        getContext().registerReceiver(contactBroadcastReceiver, movedFilter);
        getContext().registerReceiver(sortedBroadcastReceiver, sortedFilter);
        return mMainView;
    }

    private void initRecyclerView() {
        switch(mTab) {
            case Vital.TAB_FAVORITES:
                mChatList = mApp.getFavorites();
                break;
            case Vital.TAB_CONTACTS:
                mChatList = mApp.getContacts();
                break;
            case Vital.TAB_UNKNOWN:
                mChatList = mApp.getUnknown();
                break;
        }
        mChatListRecyclerView = mMainView.findViewById(R.id.chatlist_recyclerview);
        mChatAdapter = new ChatAdapter(getActivity(), mChatList);
        mChatListRecyclerView.setAdapter(mChatAdapter);
        mChatListRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    }

    private BroadcastReceiver sortedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String tab = intent.getStringExtra("tab");
            Log.d(TAG, "sortedBroadcastReceiver: " + action);
            if (action != null && action.equals(Vital.CHAT_SORTED)) {
                if (tab != null) {
                    if (tab.equals(mTab)) {
                        mChatAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    private BroadcastReceiver contactBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "contactBroadcastReceiver: " + action);
            if(action != null && action.equals(Vital.CHAT_MOVED)) {
                String address = intent.getStringExtra("address");
                String from = intent.getStringExtra("from");
                String to = intent.getStringExtra("to");
                Chat chat = mApp.getChatMap().get(address);
                Integer position = DequeUtil.getPositionByAddress(mChatList, address);
                // if from == this mTab, delete the chat from the display
                if(from.equals(mTab)) {
                    Chat removedChat = DequeUtil.removeByAddress(mChatList, address);
                    mChatList.remove(removedChat);
                    mChatAdapter.notifyItemRemoved(position);
                }
                // if to == this mTab, add the chat to the display
                if(to.equals(mTab)) {
                    chat.tab = mTab;
                    mChatList.add(chat);
                    mChatAdapter.notifyItemInserted(position);
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(contactBroadcastReceiver);
        getContext().unregisterReceiver(sortedBroadcastReceiver);
    }
}
