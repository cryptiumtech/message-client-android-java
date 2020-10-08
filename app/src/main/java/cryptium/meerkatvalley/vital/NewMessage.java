package cryptium.meerkatvalley.vital;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class NewMessage extends AppCompatActivity {

    private static final String TAG = "NewMessage";
    private Toolbar mToolbar;
    private EditText mAddress;
    private Button mSendButton;
    private TextView mError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_message);

        mToolbar = findViewById(R.id.new_message_toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle("New Conversation");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAddress = findViewById(R.id.new_message_address_edit_text);
        mError = findViewById(R.id.error_text_view);
        mError.setVisibility(View.INVISIBLE);
        mSendButton = findViewById(R.id.new_message_send_button);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mAddress.getText().length() == 10) {
                    Intent messageIntent = new Intent(NewMessage.this, MessageActivity.class);
                    messageIntent.putExtra("address", mAddress.getText().toString());
                    messageIntent.putExtra("newMessage", true);
                    startActivity(messageIntent);
                }
                else {
                    mError.setText("Enter a valid phone number (10 digits)");
                    mError.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
