package lyx.testrecord;

import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    public static final int SCREEN_SHOT = 1000;

    //开始录制
    private Button btnStartRecord;
    //停止录制
    private Button btnEndRecord;

    private ScreenRecorderTest mRecorder;

    private MediaProjectionManager projectionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        btnStartRecord = (Button) findViewById(R.id.btn_start_record);
        btnEndRecord = (Button) findViewById(R.id.btn_end_record);
        btnStartRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_SHOT);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            switch (requestCode){
                case SCREEN_SHOT:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                        if (mediaProjection == null) {
                            Log.e("@@", "media projection is null");
                            return;
                        }
                        // video size
                        final int width = 1280;
                        final int height = 720;
                        File file = new File(Environment.getExternalStorageDirectory(),
                                "record-" + width + "x" + height + "-" + System.currentTimeMillis() + ".mp4");
                        final int bitrate = 6000000;
                        mRecorder = new ScreenRecorderTest(width, height, bitrate, 1, mediaProjection, file.getAbsolutePath());
                        mRecorder.start();

                        Toast.makeText(this, "Screen recorder is running...", Toast.LENGTH_SHORT).show();
                        moveTaskToBack(true);
                    }
            }
        }

    }
}
