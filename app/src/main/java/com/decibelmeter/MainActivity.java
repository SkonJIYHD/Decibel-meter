package com.decibelmeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;
    private static final String CORRECT_PASSWORD = "宋繁一是傻逼";
    
    private TextView decibelTextView;
    private TextView statusTextView;
    private LinearLayout mainLayout;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Handler handler = new Handler();
    
    private int clickCount = 0;
    private long lastClickTime = 0;
    private boolean isUnlocked = false;
    private GestureDetector gestureDetector;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupGestureDetector();
        checkPermissions();
    }
    
    private void initViews() {
        decibelTextView = findViewById(R.id.decibelTextView);
        statusTextView = findViewById(R.id.statusTextView);
        mainLayout = findViewById(R.id.mainLayout);
        
        // 设置点击监听
        mainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick();
            }
        });
        
        // 设置长按监听
        mainLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                handleLongClick();
                return true;
            }
        });
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return true;
            }
        });
    }
    
    private void handleClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) { // 500ms内的连续点击
            clickCount++;
        } else {
            clickCount = 1; // 重置计数
        }
        lastClickTime = currentTime;
        
        if (clickCount >= 10) {
            // 达到10次点击，准备触发密码框
        }
    }
    
    private void handleLongClick() {
        if (clickCount >= 10) {
            showPasswordDialog();
            clickCount = 0; // 重置计数
        }
    }
    
    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入密码");
        
        final EditText input = new EditText(this);
        input.setHint("密码");
        builder.setView(input);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = input.getText().toString();
            if (CORRECT_PASSWORD.equals(password)) {
                isUnlocked = true;
                statusTextView.setText("已解锁 - 正常模式");
                Toast.makeText(this, "解锁成功！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.RECORD_AUDIO}, 
                    PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            startRecording();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用分贝仪功能", Toast.LENGTH_LONG).show();
                statusTextView.setText("需要麦克风权限");
            }
        }
    }
    
    private void startRecording() {
        int sampleRate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT);
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                sampleRate, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT, 
                bufferSize);
        
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            isRecording = true;
            audioRecord.startRecording();
            statusTextView.setText("正在监听...");
            startDecibelMonitoring();
        }
    }
    
    private void startDecibelMonitoring() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] buffer = new short[1024];
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        double decibels = calculateDecibels(buffer, read);
                        final double displayDecibels = isUnlocked ? decibels : applyDisguise(decibels);
                        
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateDecibelDisplay(displayDecibels);
                            }
                        });
                    }
                }
            }
        }).start();
    }
    
    private double calculateDecibels(short[] buffer, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        double rms = Math.sqrt(sum / length);
        return 20 * Math.log10(rms / 32768.0) + 90; // 转换为分贝
    }
    
    private double applyDisguise(double actualDecibels) {
        // 当实际分贝在60-80之间时，显示为50-70
        if (actualDecibels >= 60 && actualDecibels <= 80) {
            return 50 + (actualDecibels - 60) * (20.0 / 20.0); // 线性映射到50-70
        }
        return actualDecibels;
    }
    
    private void updateDecibelDisplay(double decibels) {
        decibelTextView.setText(String.format("%.1f dB", decibels));
        
        // 根据分贝值改变背景颜色
        int color = getColorForDecibel(decibels);
        mainLayout.setBackgroundColor(color);
    }
    
    private int getColorForDecibel(double decibels) {
        if (decibels < 40) {
            return 0xFF4CAF50; // 绿色 - 安静
        } else if (decibels < 70) {
            return 0xFFFFEB3B; // 黄色 - 中等
        } else if (decibels < 90) {
            return 0xFFFF9800; // 橙色 - 吵闹
        } else {
            return 0xFFF44336; // 红色 - 很吵
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
        }
    }
}