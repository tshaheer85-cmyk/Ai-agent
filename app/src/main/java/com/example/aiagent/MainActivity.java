package com.example.aiagent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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

    private static final String[] MODELS = {
            "gemini-2.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-flash-latest"
    };

    private static final String SYSTEM_PROMPT =
            "Tum phone ke voice agent ho. Hamesha SIRF ek JSON object do, aur kuch nahi: "
            + "{\"action\":\"chat\" ya \"open_app\", "
            + "\"app\":\"app ka angrezi naam jaise WhatsApp, YouTube, Chrome, Camera, Settings, Gallery "
            + "(sirf open_app mein, warna khali)\", "
            + "\"say\":\"chhota jawab Roman Urdu/Hindi mein, 1 se 2 jumle, bina star ya markdown ke\"}. "
            + "Agar user koi app kholne ko kahe to action open_app. Warna action chat. "
            + "Tum abhi sirf app khol sakte ho. Message bhejna, call karna, tap ya type karna abhi nahi kar sakte, "
            + "agar maange to say mein saaf batao ke yeh abhi nahi ho sakta.";

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

    private String callGemini(String model, String key, String question) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key);
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);

        JSONObject sys = new JSONObject().put("parts", new JSONArray().put(
                new JSONObject().put("text", SYSTEM_PROMPT)));
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(
                new JSONObject().put("text", question)));
        JSONObject body = new JSONObject()
                .put("system_instruction", sys)
                .put("contents", new JSONArray().put(content))
                .put("generationConfig", new JSONObject()
                        .put("responseMimeType", "application/json"));

        c.getOutputStream().write(body.toString().getBytes("UTF-8"));

        int code = c.getResponseCode();
        if (code >= 400) {
            throw new Exception("HTTP " + code);
        }
        InputStream is = c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);

        JSONObject j = new JSONObject(sb.toString());
        return j.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text");
    }

    private boolean openApp(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String want = name.trim().toLowerCase();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        String pkg = null;
        for (ResolveInfo ri : list) {
            String label = ri.loadLabel(pm).toString().toLowerCase();
            if (label.equals(want)) {
                pkg = ri.activityInfo.packageName;
                break;
            }
        }
        if (pkg == null) {
            for (ResolveInfo ri : list) {
                String label = ri.loadLabel(pm).toString().toLowerCase();
                if (label.contains(want)) {
                    pkg = ri.activityInfo.packageName;
                    break;
                }
            }
        }
        if (pkg == null) return false;
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        return true;
    }

    private void askAI(final String question) {
        final String key = prefs.getString("key", "");
        if (key.isEmpty()) {
            status.setText("Pehle upar key paste karke save karein");
            return;
        }
        tts.stop();
        status.setText("Aapne kaha: " + question + "\n\nSoch raha hoon...");
        new Thread(() -> {
            String raw = null;
            StringBuilder errs = new StringBuilder();
            for (String m : MODELS) {
                try {
                    raw = callGemini(m, key, question);
                    break;
                } catch (Exception e) {
                    errs.append(m).append(": ").append(e.getMessage()).append("\n");
                }
            }
            if (raw == null) {
                final String msg = "Error: jawab nahi aaya.\n" + errs;
                runOnUiThread(() -> status.setText("Aapne kaha: " + question + "\n\n" + msg));
                return;
            }
            String action = "chat";
            String app = "";
            String say = raw;
            try {
                String clean = raw.trim();
                if (clean.startsWith("```")) {
                    clean = clean.replace("```json", "").replace("```", "").trim();
                }
                JSONObject j = new JSONObject(clean);
                action = j.optString("action", "chat");
                app = j.optString("app", "");
                say = j.optString("say", "");
            } catch (Exception e) {
                // JSON nahi bana, jawab jaisa hai waisa dikhayenge
            }
            final String fAction = action;
            final String fApp = app;
            final String fSay = say;
            runOnUiThread(() -> {
                String shown = "Aapne kaha: " + question + "\n\nAI: " + fSay;
                boolean opened = false;
                if (fAction.equals("open_app")) {
                    opened = openApp(fApp);
                    shown += opened ? "\n\n[App khuli: " + fApp + "]"
                                    : "\n\n[App nahi mili: " + fApp + "]";
                }
                status.setText(shown);
                String toSpeak = fSay;
                if (fAction.equals("open_app") && !opened) {
                    toSpeak = fApp + " app mujhe nahi mili";
                }
                if (!toSpeak.isEmpty()) {
                    tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "u1");
                }
            });
        }).start();
    }
}
