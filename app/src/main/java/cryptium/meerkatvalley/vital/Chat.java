package cryptium.meerkatvalley.vital;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Chat{
    public String name;
    public String address;
    public String tab;
    public String seen;
//    public ArrayList<Message> messageList;
    public ConcurrentLinkedDeque<Message> messageList;

    // Most recent is first
//    public int compareTo(Chat compareChat) {
//        return -getRecent().compareTo(compareChat.getRecent());
//    }

//    public Message getRecent() {
////        return messageList.get( messageList.size() - 1 );
//        return messageList.getFirst();
//    }
}
