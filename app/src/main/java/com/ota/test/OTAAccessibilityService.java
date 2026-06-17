package com.ota.test;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OTAAccessibilityService extends AccessibilityService {

    private static OTAAccessibilityService instance;

    public static String deviceName = "42";
    public static int totalRounds = 100;
    public static volatile boolean running = false;

    public static int currentRound = 0;
    public static int successCount = 0;
    public static int failureCount = 0;
    public static int timeoutCount = 0;
    public static long startedAt = 0;

    private MainActivity activity;

    public static OTAAccessibilityService getInstance() {
        return instance;
    }

    public static void resetStats() {
        currentRound = 0;
        successCount = 0;
        failureCount = 0;
        timeoutCount = 0;
        startedAt = System.currentTimeMillis();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Not used - polling in test thread
    }

    @Override
    public void onInterrupt() {
    }

    public void setActivity(MainActivity a) {
        this.activity = a;
    }

    private void notifyStatus(String s) {
        if (activity != null) activity.updateStatus(s);
    }

    private void notifyStats() {
        if (activity != null) activity.updateStats();
    }

    private void notifyComplete() {
        if (activity != null) activity.showComplete();
    }

    // ========== PUBLIC API ==========

    public void startTest() {
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                runTest();
            }
        }).start();
    }

    public void stopTest() {
        running = false;
    }

    // ========== FIND NODES ==========

    private AccessibilityNodeInfo findText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.getText() != null && n.getText().toString().contains(text)) {
                        return n;
                    }
                }
                return nodes.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            root.recycle();
        }
        return null;
    }

    private AccessibilityNodeInfo findClickable(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.getText() != null && n.getText().toString().contains(text)) {
                        // Try clickable parent
                        AccessibilityNodeInfo target = n;
                        while (target != null && !target.isClickable()) {
                            target = target.getParent();
                        }
                        if (target != null && target.isClickable()) {
                            return target;
                        }
                        return n;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            root.recycle();
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            if (node.isClickable()) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            AccessibilityNodeInfo p = node.getParent();
            while (p != null) {
                if (p.isClickable()) {
                    return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                p = p.getParent();
            }
            // Gesture click
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && node.getBoundsInScreen() != null) {
                android.graphics.Rect r = new android.graphics.Rect();
                node.getBoundsInScreen().getRect(r);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(r.centerX(), r.centerY());
                return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
                        .build(), null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private AccessibilityNodeInfo waitForClickable(String text, long timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout && running) {
            AccessibilityNodeInfo n = findClickable(text);
            if (n != null) return n;
            sleep(300);
        }
        return null;
    }

    private boolean waitForText(String text, long timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout && running) {
            AccessibilityNodeInfo n = findText(text);
            if (n != null) return true;
            sleep(300);
        }
        return false;
    }

    // ========== CORE TEST LOGIC ==========

    private void runTest() {
        try {
            sleep(1000);
            notifyStatus("等待设备: " + deviceName);

            // Wait for device name
            boolean matched = waitForText(deviceName, 120000);
            if (!matched) {
                notifyStatus("未匹配到设备");
                running = false;
                return;
            }
            notifyStatus("已匹配: " + deviceName);
            sleep(1500);

            // Main loop
            for (int i = 1; i <= totalRounds && running; i++) {
                currentRound = i;
                notifyStatus("第" + i + "轮开始");
                notifyStats();
                long rs = System.currentTimeMillis();

                // Step 1: Click "进入设备"
                AccessibilityNodeInfo enterBtn = waitForClickable("进入设备", 60000);
                if (enterBtn == null) {
                    recordResult(i, rs, "失败", "no enter");
                    failureCount++;
                    notifyStats();
                    sleep(5000);
                    continue;
                }
                click(enterBtn);
                sleep(2000);

                // Step 2: Click "更新"
                AccessibilityNodeInfo updateBtn = waitForClickable("更新", 60000);
                if (updateBtn == null) {
                    recordResult(i, rs, "失败", "no update");
                    failureCount++;
                    notifyStats();
                    sleep(5000);
                    continue;
                }
                click(updateBtn);
                notifyStatus("第" + i + "轮升级中...");

                // Step 3: Wait for result
                String result = waitForResult();
                String label;
                if ("success".equals(result)) {
                    label = "成功";
                    successCount++;
                } else if ("failure".equals(result)) {
                    label = "失败";
                    failureCount++;
                } else {
                    label = "失败(超时)";
                    failureCount++;
                    timeoutCount++;
                }

                recordResult(i, rs, label, "");
                notifyStats();

                // Step 4: Dismiss
                dismissResult();

                notifyStatus("第" + i + "轮完成");
                if (i < totalRounds) sleep(5000);
            }

            notifyStatus("测试完成");
            notifyComplete();

        } catch (Exception e) {
            e.printStackTrace();
            notifyStatus("错误: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    private String waitForResult() {
        long start = System.currentTimeMillis();
        long timeout = 300000;
        while (System.currentTimeMillis() - start < timeout && running) {
            if (findText("已升级至最新版本") != null) return "success";
            if (findText("COMPLETED") != null) return "success";
            if (findText("下载超时，请重试") != null) return "failure";
            if (findText("TRY AGAIN") != null) return "failure";
            if (findText("连接超时") != null) return "failure";
            sleep(500);
        }
        return "timeout";
    }

    private void dismissResult() {
        String[] texts = {"COMPLETED", "TRY AGAIN", "确定", "OK", "完成"};
        for (int a = 0; a < 10 && running; a++) {
            if (findText("进入设备") != null) return;
            for (String t : texts) {
                AccessibilityNodeInfo btn = findClickable(t);
                if (btn != null) {
                    click(btn);
                    sleep(800);
                    break;
                }
            }
            sleep(1000);
        }
    }

    private void recordResult(int round, long startTime, String result, String note) {
        long endTime = System.currentTimeMillis();
        long secs = (endTime - startTime) / 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String line = round + "," + sdf.format(new Date(startTime)) + "," +
                sdf.format(new Date(endTime)) + "," + secs + "," + result + "," + note;
        try {
            File f = new File("/sdcard/OTA测试结果.csv");
            FileWriter fw = new FileWriter(f, f.exists());
            if (!f.exists()) fw.write("轮次,开始时间,结束时间,耗时(秒),结果,备注\n");
            fw.write(line + "\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
