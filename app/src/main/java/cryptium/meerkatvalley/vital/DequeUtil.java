package cryptium.meerkatvalley.vital;

import java.util.Deque;
import java.util.Iterator;

public class DequeUtil {
    public static <T> T get(Deque<T> deque, int position) {
        Iterator<T> it = deque.iterator();
        T found = null;
        int i = 0;
        while(it.hasNext() && i <= position) {
            found = it.next();
            i += 1;
        }
        return found;
    }
    public static int getPositionByAddress(Deque<Chat> deque, String address) {
        Iterator<Chat> it = deque.iterator();
        Chat found = null;
        int i = 0;
        while(it.hasNext()) {
            found = it.next();
            if (found.address.equals(address)) {
                return i;
            }
            i += 1;
        }
        return -1;
    }
    public static Chat removeByAddress(Deque<Chat> deque, String address) {
        Iterator<Chat> it = deque.iterator();
        Chat found = null;
        while(it.hasNext()) {
            found = it.next();
            if (found.address.equals(address)) {

                return found;
            }
        }
        return null;
    }
}
