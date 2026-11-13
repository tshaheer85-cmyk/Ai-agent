package com.example.aiagent;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

public class AgentAccessibilityService extends AccessibilityService {

    public static AgentAccessibilityService instance;

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

    public String readScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "App: unknown\n(screen padh nahi saka)";
        StringBuilder sb = new StringBuilder();
        sb.append("App: ").append(root.getPackageName()).append("\n");
        int[] count = {0};
        collect(root, sb, count);
        return sb.toString();
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder sb, int[] count) {
        if (n == null || count[0] > 70) return;
        if (!n.isPassword()) {
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            String label = "";
            if (t != null && t.length() > 0) label = t.toString();
            else if (d != null && d.length() > 0) label = d.toString();
            if (label.length() > 80) label = label.substring(0, 80);
            if (!label.isEmpty() || n.isEditable()) {
                sb.append("- ").append(label);
                if (n.isEditable()) sb.append(" [text box]");
                else if (n.isClickable()) sb.append(" [button]");
                sb.append("\n");
                count[0]++;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChild(i), sb, count);
        }
    }

    public boolean clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || text == null || text.isEmpty()) return false;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        if (list == null) return false;
        for (AccessibilityNodeInfo n : list) {
            AccessibilityNodeInfo c = n;
            while (c != null && !c.isClickable()) c = c.getParent();
            if (c != null && !c.isPassword()) {
                return c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    public boolean typeText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null || !target.isEditable()) target = findEditable(root);
        if (target == null || target.isPassword()) return false;
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
