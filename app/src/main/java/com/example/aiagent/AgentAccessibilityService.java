package com.example.aiagent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class AgentAccessibilityService extends AccessibilityService {

    public static AgentAccessibilityService instance;
    private final List<AccessibilityNodeInfo> nodes = new ArrayList<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Toast.makeText(this, "AI Agent: Accessibility chalu", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    // Screen ki list: har cheez ko number milta hai
    public synchronized String readScreen() {
        nodes.clear();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "App: unknown\n(screen padh nahi saka)";
        DisplayMetrics dm = getResources().getDisplayMetrics();
        StringBuilder sb = new StringBuilder();
        sb.append("App: ").append(root.getPackageName()).append("\n");
        sb.append("Screen size: ").append(dm.widthPixels).append("x")
                .append(dm.heightPixels).append("\n");
        collect(root, sb);
        return sb.toString();
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder sb) {
        if (n == null || nodes.size() >= 80) return;
        if (!n.isPassword() && n.isVisibleToUser()) {
            String label = "";
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            if (t != null && t.length() > 0) label = t.toString();
            else if (d != null && d.length() > 0) label = d.toString();
            label = label.replace("\n", " ");
            if (label.length() > 60) label = label.substring(0, 60);
            boolean edit = n.isEditable();
            boolean click = n.isClickable();
            if (!label.isEmpty() || edit || click) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    int idx = nodes.size();
                    nodes.add(n);
                    sb.append("[").append(idx).append("] ");
                    sb.append(label.isEmpty() ? "(bina naam)" : label);
                    if (edit) sb.append(" {text box}");
                    else if (click) sb.append(" {button}");
                    sb.append(" @").append(r.centerX()).append(",").append(r.centerY()).append("\n");
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChild(i), sb);
        }
    }

    // Asli ungli ki tarah tap
    public boolean tapAt(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 80));
        return dispatchGesture(b.build(), null, null);
    }

    private boolean tapNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        n.refresh();
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (r.width() > 0 && r.height() > 0 && tapAt(r.centerX(), r.centerY())) {
            return true;
        }
        AccessibilityNodeInfo c = n;
        for (int k = 0; k < 6 && c != null; k++) {
            if (c.isClickable() && !c.isPassword()
                    && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            c = c.getParent();
        }
        return false;
    }

    public synchronized boolean clickIndex(int i) {
        if (i < 0 || i >= nodes.size()) return false;
        return tapNode(nodes.get(i));
    }

    public boolean clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || text == null || text.isEmpty()) return false;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        if (list == null) return false;
        for (AccessibilityNodeInfo n : list) {
            if (!n.isPassword() && tapNode(n)) return true;
        }
        return false;
    }

    public synchronized boolean typeText(int index, String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo target = null;
        if (index >= 0 && index < nodes.size()) target = nodes.get(index);
        if (target == null || !target.isEditable()) {
            if (root != null) {
                target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (target == null || !target.isEditable()) target = findEditable(root);
            }
        }
        if (target == null || target.isPassword()) return false;
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && !n.isPassword()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEditable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    public boolean scrollDown() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo s = findScrollable(root);
        if (s == null) return false;
        return s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isScrollable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    public boolean goBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean goHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }
}
