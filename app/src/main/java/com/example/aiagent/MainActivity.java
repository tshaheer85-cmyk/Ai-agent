package com.example.aiagent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText("AI Agent chal raha hai");
        t.setTextSize(24);
        t.setPadding(40, 200, 40, 40);
        setContentView(t);
    }
}
