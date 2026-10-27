package dev.codex.ricohfullzoom;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Small settings screen: LSPosed shows its launch button for modules that have one. */
public final class SettingsActivity extends Activity {
    private static final String CAMERA_PACKAGE = "com.oplus.camera";
    private static final String CAMERA_ACTIVITY = "com.oplus.camera.Camera";
    private static final String BUTTON_TEXT = "重启相机（需要 root）";

    private TextView status;
    private Button applyButton;
    private String rootError = "";
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0E0E0E"));
        getWindow().setNavigationBarColor(Color.parseColor("#0E0E0E"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0E0E0E"));
        int pad = dp(24);
        root.setPadding(pad, dp(48), pad, pad);

        TextView title = new TextView(this);
        title.setText("理光模式全焦段");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("模块生效前，请在 LSPosed 中启用并勾选相机作用域。\n"
                + "改动需要重启相机进程，并在理光 GR 焦段中确认。");
        desc.setTextColor(Color.parseColor("#B3B3B3"));
        desc.setTextSize(14);
        desc.setPadding(0, dp(12), 0, dp(28));
        root.addView(desc);

        applyButton = new Button(this);
        applyButton.setText(BUTTON_TEXT);
        applyButton.setAllCaps(false);
        applyButton.setTextSize(16);
        applyButton.setTextColor(Color.BLACK);
        applyButton.setBackgroundColor(Color.parseColor("#FFC107"));
        applyButton.setPadding(dp(8), dp(14), dp(8), dp(14));
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyNow();
            }
        });
        root.addView(applyButton);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#80CBC4"));
        status.setTextSize(13);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);

        TextView hint = new TextView(this);
        hint.setText("如已授予 Magisk 超级用户权限，按钮会重启相机。\n"
                + "未获得 root 时，请在系统设置中手动强制停止相机后重新打开。"
                + "LSPosed 的 Hook 只在相机进程启动时加载，是否生效请以理光 GR 焦段确认。");
        hint.setTextColor(Color.parseColor("#777777"));
        hint.setTextSize(12);
        hint.setPadding(0, dp(28), 0, 0);
        root.addView(hint);

        setContentView(root);
    }

    private void applyNow() {
        if (running) {
            return;
        }
        running = true;
        applyButton.setEnabled(false);
        applyButton.setText("正在重启…");
        status.setText("正在重启相机…");
        rootError = "";

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean rooted = false;
                try {
                    rooted = forceStopWithRoot();
                } finally {
                    final boolean success = rooted;
                    if (success) {
                        try {
                            Thread.sleep(600);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false);
                            if (success) {
                                openCamera();
                            } else {
                                showManualRestartRequired();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private boolean forceStopWithRoot() {
        String[] candidates = new String[]{"/product/bin/su", "su", "/debug_ramdisk/su"};
        for (String su : candidates) {
            Process process = null;
            try {
                process = Runtime.getRuntime().exec(
                        new String[]{su, "-c", "am force-stop " + CAMERA_PACKAGE});
                boolean finished = process.waitFor(6, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroy();
                    rootError = su + " timeout";
                    continue;
                }
                int code = process.exitValue();
                if (code == 0) {
                    return true;
                }
                rootError = su + " exit=" + code + " " + read(process.getErrorStream());
            } catch (Throwable throwable) {
                rootError = su + " " + throwable.getClass().getSimpleName();
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return false;
    }

    private static String read(InputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            int count;
            while ((count = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, count);
            }
            return buffer.toString("UTF-8").trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void setBusy(boolean busy) {
        running = busy;
        applyButton.setEnabled(!busy);
        applyButton.setText(busy ? "正在重启…" : BUTTON_TEXT);
    }

    private void showManualRestartRequired() {
        String message = "未获得 root，未自动重启相机。请手动强制停止相机后重新打开；"
                + "模块是否生效需确认 LSPosed 已启用，并在理光 GR 焦段中确认。";
        status.setText(message + "\n" + rootError);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void openCamera() {
        try {
            Intent intent = new Intent();
            intent.setClassName(CAMERA_PACKAGE, CAMERA_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            String message = "已重启相机。模块是否生效需确认 LSPosed 已启用，"
                    + "并在理光 GR 焦段中确认。";
            status.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Throwable throwable) {
            status.setText("打开相机失败：" + throwable.getClass().getSimpleName());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
