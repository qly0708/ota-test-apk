package com.ota.test;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etDeviceName, etRoundCount;
    private Button btnStart, btnStop;
    private TextView tvStatus, tvStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etDeviceName = findViewById(R.id.etDeviceName);
        etRoundCount = findViewById(R.id.etRoundCount);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTest();
            }
        });
    }

    private void startTest() {
        String name = etDeviceName.getText().toString().trim();
        String roundsStr = etRoundCount.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请输入机器人名称", Toast.LENGTH_SHORT).show();
            return;
        }

        int rounds;
        try {
            rounds = Integer.parseInt(roundsStr);
            if (rounds < 1) {
                Toast.makeText(this, "轮数必须大于0", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("需要无障碍服务")
                .setMessage("请开启无障碍服务后重试")
                .setPositiveButton("去设置", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .setNegativeButton("取消", null)
                .show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new AlertDialog.Builder(this)
                    .setTitle("需要悬浮窗权限")
                    .setPositiveButton("去设置", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return;
            }
        }

        OTAAccessibilityService.deviceName = name;
        OTAAccessibilityService.totalRounds = rounds;
        OTAAccessibilityService.resetStats();

        etDeviceName.setEnabled(false);
        etRoundCount.setEnabled(false);
        btnStart.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
        tvStats.setVisibility(View.VISIBLE);

        OTAAccessibilityService service = OTAAccessibilityService.getInstance();
        if (service != null) {
            service.setActivity(this);
            service.startTest();
            tvStatus.setText("启动中...");
        } else {
            Toast.makeText(this, "无障碍服务未就绪", Toast.LENGTH_SHORT).show();
            resetUI();
        }
    }

    private void stopTest() {
        OTAAccessibilityService.running = false;
        OTAAccessibilityService service = OTAAccessibilityService.getInstance();
        if (service != null) {
            service.stopTest();
        }
        resetUI();
        tvStatus.setText("已停止");
    }

    private void resetUI() {
        etDeviceName.setEnabled(true);
        etRoundCount.setEnabled(true);
        btnStart.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.GONE);
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + OTAAccessibilityService.class.getCanonicalName();
        try {
            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        } catch (Exception e) {
            return false;
        }
    }

    public void updateStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(text);
            }
        });
    }

    public void updateStats() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int total = OTAAccessibilityService.successCount + OTAAccessibilityService.failureCount;
                double rate = total > 0 ? (OTAAccessibilityService.successCount * 100.0 / total) : 0;
                long elapsed = System.currentTimeMillis() - OTAAccessibilityService.startedAt;
                long mins = elapsed / 60000;
                long secs = (elapsed % 60000) / 1000;

                String text = "已运行: " + mins + "分" + secs + "秒\n";
                text += "轮次: " + OTAAccessibilityService.currentRound + "/" + OTAAccessibilityService.totalRounds + "\n";
                text += "成功: " + OTAAccessibilityService.successCount + "\n";
                text += "失败: " + OTAAccessibilityService.failureCount + "\n";
                text += "超时: " + OTAAccessibilityService.timeoutCount + "\n";
                text += "成功率: " + String.format("%.1f", rate) + "%";
                tvStats.setText(text);
            }
        });
    }

    public void showComplete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resetUI();
                int total = OTAAccessibilityService.successCount + OTAAccessibilityService.failureCount;
                double rate = total > 0 ? (OTAAccessibilityService.successCount * 100.0 / total) : 0;
                long elapsed = System.currentTimeMillis() - OTAAccessibilityService.startedAt;
                long mins = elapsed / 60000;
                long secs = (elapsed % 60000) / 1000;

                String report = "OTA测试完成\n\n";
                report += "总轮次: " + total + "\n";
                report += "成功: " + OTAAccessibilityService.successCount + "\n";
                report += "失败: " + OTAAccessibilityService.failureCount + "\n";
                report += "超时: " + OTAAccessibilityService.timeoutCount + "\n";
                report += "成功率: " + String.format("%.1f", rate) + "%\n";
                report += "耗时: " + mins + "分" + secs + "秒\n\n";
                report += "详情: /sdcard/OTA测试结果.csv";

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("测试完成")
                    .setMessage(report)
                    .setPositiveButton("确定", null)
                    .show();

                tvStatus.setText("测试完成");
            }
        });
    }
}
