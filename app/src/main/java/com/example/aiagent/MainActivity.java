package com.example.aiagent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView status;
    private EditText keyBox;
    private Button agentBtn;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private boolean agentOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("agent", MODE_PRIVATE);
        agentOn = prefs.getBoolean("agentOn", false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        keyBox = new EditText(this);
        keyBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyBox.setHint(prefs.getString("key", "").isEmpty()
                ? "Gemini API key yahan paste karein" : "Key save hai (badalne ke liye nayi paste karein)");

        Button saveBtn = new Button(this);
        saveBtn.setText("Key save karein");

        status = new TextView(this);
        status.setText("Button dabayein aur boliye");
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 40, 0, 40);

        Button btn = new Button(this);
        btn.setText("Bolna shuru karein");

        agentBtn = new Button(this);
        updateAgentButton();

        layout.addView(keyBox);
        layout.addView(saveBtn);
        layout.addView(status);
        layout.addView(btn);
        layout.addView(agentBtn);
        setContentView(layout);

        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }

        saveBtn.setOnClickListener(v -> {
            String k = keyBox.getText().toString().trim();
            if (k.isEmpty()) {
                status.setText("Pehle key paste karein");
            } else {
                prefs.edit().putString("key", k).apply();
                keyBox.setText("");
                keyBox.setHint("Key save hai");
                status.setText("Key save ho gayi");
            }
        });

        btn.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            } else {
                startListening();
            }
        });

        agentBtn.setOnClickListener(v -> {
            if (agentOn) {
                stopService(new Intent(this, AgentService.class));
                agentOn = false;
                prefs.edit().putBoolean("agentOn", false).apply();
                updateAgentButton();
                status.setText("Agent band ho gaya");
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
            } else {
                startAgent();
            }
        });
    }

    private void updateAgentButton() {
        agentBtn.setText(agentOn ? "Agent band karein (abhi ON hai)" : "Agent ON karein (background)");
    }

    private void startAgent() {
        startForegroundService(new Intent(this, AgentService.class));
        agentOn = true;
        prefs.edit().putBoolean("agentOn", true).apply();
        updateAgentButton();
        status.setText("Agent ON. Ab app band karke notification dekhein.");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == 1) {
            if (granted) startListening();
            else status.setText("Mic ki ijazat nahi mili");
        } else if (code == 3) {
            if (granted) startAgent();
            else status.setText("Mic ki ijazat nahi mili");
        }
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Is phone mein speech recognition nahi mila");
            return;
        }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { status.setText("Sun raha hoon..."); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float f) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() {}
            public void onError(int e) { status.setText("Error code: " + e); }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
            public void onResults(Bundle b) {
                ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (r != null && !r.isEmpty()) {
                    askAI(r.get(0));
                }
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        recognizer.startListening(i);
    }

    private void askAI(final String question) {
        final String key = prefs.getString("key", "");
        if (key.isEmpty()) {
            status.setText("Pehle upar key paste karke save karein");
            return;
        }
        status.setText("Aapne kaha: " + question + "\n\nSoch raha hoon...");
        new Thread(() -> {
            String out;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("x-goog-api-key", key);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setDoOutput(true);

                JSONObject sys = new JSONObject().put("parts", new JSONArray().put(
                        new JSONObject().put("text",
                                "Tum ek madadgar voice assistant ho. Jawab Roman Urdu/Hindi mein do, "
                                + "chhota (2-3 jumle), bina star ya markdown ke.")));
                JSONObject content = new JSONObject().put("parts", new JSONArray().put(
                        new JSONObject().put("text", question)));
                JSONObject body = new JSONObject()
                        .put("system_instruction", sys)
                        .put("contents", new JSONArray().put(content));

                c.getOutputStream().write(body.toString().getBytes("UTF-8"));

                int code = c.getResponseCode();
                InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);

                if (code >= 400) {
                    String s = sb.toString();
                    out = "Error " + code + ": " + s.substring(0, Math.min(250, s.length()));
                } else {
                    JSONObject j = new JSONObject(sb.toString());
                    out = j.getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text");
                }
            } catch (Exception e) {
                out = "Masla: " + e.toString();
            }
            final String answer = out;
            runOnUiThread(() -> {
                status.setText("Aapne kaha: " + question + "\n\nAI: " + answer);
                if (!answer.startsWith("Error") && !answer.startsWith("Masla")) {
                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "id2");
                }
            });
        }).start();
    }
}
