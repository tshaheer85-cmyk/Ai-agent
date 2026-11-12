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
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView status;
    private EditText keyBox;
    private EditText inputBox;
    private Button agentBtn;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private boolean agentOn = false;
    private int utterId = 0;

    private static final String[] MODELS = {
            "gemini-2.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-flash-latest"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("agent", MODE_PRIVATE);
        agentOn = prefs.getBoolean("agentOn", false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);

        keyBox = new EditText(this);
        keyBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyBox.setHint(prefs.getString("key", "").isEmpty()
                ? "Gemini API key yahan paste karein" : "Key save hai (badalne ke liye nayi paste karein)");

        Button saveBtn = new Button(this);
        saveBtn.setText("Key save karein");

        status = new TextView(this);
        status.setText("Likhein ya bolein");
        status.setTextSize(19);
        status.setPadding(10, 20, 10, 20);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);

        inputBox = new EditText(this);
        inputBox.setHint("Yahan likhein...");

        Button sendBtn = new Button(this);
        sendBtn.setText("Bhejein");

        Button micBtn = new Button(this);
        micBtn.setText("Bolna shuru karein");

        agentBtn = new Button(this);
        updateAgentButton();

        layout.addView(keyBox);
        layout.addView(saveBtn);
        layout.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        layout.addView(inputBox);
        layout.addView(sendBtn);
        layout.addView(micBtn);
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

        sendBtn.setOnClickListener(v -> {
            String q = inputBox.getText().toString().trim();
            if (q.isEmpty()) {
                status.setText("Pehle kuch likhein");
            } else {
                inputBox.setText("");
                askAI(q);
            }
        });

        micBtn.setOnClickListener(v -> {
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

    private void speakNow(String text) {
        String t = text.trim();
        if (t.isEmpty()) return;
        utterId++;
        tts.speak(t, TextToSpeech.QUEUE_ADD, null, "u" + utterId);
    }

    private void speakFinishedSentences(StringBuilder pending) {
        int last = -1;
        String s = pending.toString();
        String marks = ".?!\n\u0964";
        for (int i = 0; i < s.length(); i++) {
            if (marks.indexOf(s.charAt(i)) >= 0) last = i;
        }
        if (last >= 0) {
            speakNow(s.substring(0, last + 1));
            pending.delete(0, last + 1);
        }
    }

    private void streamGemini(String model, String key, final String question,
                              final StringBuilder full, final StringBuilder pending) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":streamGenerateContent?alt=sse");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key);
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);

        JSONObject sys = new JSONObject().put("parts", new JSONArray().put(
                new JSONObject().put("text",
                        "Tum ek madadgar voice assistant ho. Jawab Roman Urdu/Hindi mein do, "
                        + "chhota (2-3 jumle), seedha jawab se shuru karo, bina star ya markdown ke.")));
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(
                new JSONObject().put("text", question)));
        JSONObject body = new JSONObject()
                .put("system_instruction", sys)
                .put("contents", new JSONArray().put(content));

        c.getOutputStream().write(body.toString().getBytes("UTF-8"));

        int code = c.getResponseCode();
        if (code >= 400) {
            throw new Exception("HTTP " + code);
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String json = line.substring(5).trim();
            if (json.isEmpty()) continue;
            try {
                JSONObject j = new JSONObject(json);
                JSONArray cands = j.optJSONArray("candidates");
                if (cands == null || cands.length() == 0) continue;
                JSONObject cont = cands.getJSONObject(0).optJSONObject("content");
                if (cont == null) continue;
                JSONArray parts = cont.optJSONArray("parts");
                if (parts == null) continue;
                for (int p = 0; p < parts.length(); p++) {
                    String t = parts.getJSONObject(p).optString("text", "");
                    if (t.isEmpty()) continue;
                    full.append(t);
                    pending.append(t);
                    speakFinishedSentences(pending);
                    final String snap = full.toString();
                    runOnUiThread(() -> status.setText(
                            "Aapne kaha: " + question + "\n\nAI: " + snap));
                }
            } catch (Exception ignore) {
            }
        }
    }

    private void askAI(final String question) {
        final String key = prefs.getString("key", "");
        if (key.isEmpty()) {
            status.setText("Pehle upar key paste karke save karein");
            return;
        }
        tts.stop();
        status.setText("Aapne kaha: " + question + "\n\nAI: ...");
        new Thread(() -> {
            final StringBuilder full = new StringBuilder();
            final StringBuilder pending = new StringBuilder();
            StringBuilder errs = new StringBuilder();
            boolean ok = false;
            for (String m : MODELS) {
                try {
                    streamGemini(m, key, question, full, pending);
                    ok = true;
                    break;
                } catch (Exception e) {
                    errs.append(m).append(": ").append(e.getMessage()).append("\n");
                    if (full.length() > 0) break;
                }
            }
            if (pending.length() > 0) {
                final String rest = pending.toString();
                runOnUiThread(() -> speakNow(rest));
            }
            if (full.length() == 0) {
                final String msg = "Error: jawab nahi aaya.\n" + errs;
                runOnUiThread(() -> status.setText("Aapne kaha: " + question + "\n\nAI: " + msg));
            }
        }).start();
    }
}
