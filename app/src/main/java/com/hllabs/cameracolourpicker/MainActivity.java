package com.hllabs.cameracolourpicker;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v8.renderscript.RenderScript;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;

import io.github.silvaren.easyrs.tools.Nv21Image;


/*
Main Activity
 */
public class MainActivity extends AppCompatActivity {
    //Camera
    CameraView cameraView;
    //the byte array that stores the most recent frame
    byte[] bytes;
    //touch co-ordinates relative to the view
    float touchX,touchY;
    //views
    private RelativeLayout controlLayout;
    private TextView colorValHexText,colorValRgbText,colorValHsvText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initialise views
        controlLayout = findViewById(R.id.control_layout);
        colorValHexText = findViewById(R.id.text_color_hex);
        colorValRgbText = findViewById(R.id.text_color_rgb);
        colorValHsvText = findViewById(R.id.text_color_hsv);
        cameraView = findViewById(R.id.camera);

        //map gestures
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        cameraView.mapGesture(Gesture.LONG_TAP,GestureAction.FOCUS);
        cameraView.mapGesture(Gesture.LONG_TAP, GestureAction.EXPOSURE_CORRECTION);
        cameraView.setPlaySounds(false); // turn off all sounds


        //every time the preview frame is updated, get a copy of the byte array.
        //This constant updating means that when the user taps, we can find the colour value very fast
        cameraView.addFrameProcessor(new FrameProcessor() {
            @Override
            public void process(@NonNull Frame frame) {
                bytes = frame.getData();
            }
        });

        cameraView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN){
                    //get the touch co-ordinates
                    touchX = motionEvent.getX();
                    touchY = motionEvent.getY();
                    //find the colour code
                    findColor();
                    return true;
                }
                return false;
            }
        });

    }

    //find the colour code, and show it on the screen
    private void findColor(){
        //get the size of the camera preview frame
        int previewWidth = cameraView.getPreviewSize().getWidth();
        int previewHeight = cameraView.getPreviewSize().getHeight();
        //get the size of the view (DIFFERENT FROM THE ABOVE SIZE!!!)
        int viewWidth = cameraView.getWidth();
        int viewHeight = cameraView.getHeight();

        //use renderscript to convert the NV21 byte array into a bitmap
        //this is really fast( <100ms )
        RenderScript rs = RenderScript.create(getApplicationContext());
        Bitmap outputBitmap = Nv21Image.nv21ToBitmap(rs, bytes, previewWidth, previewHeight);

        /* THE CAMERAVIEW IS PORTRAIT, BUT THE BITMAP WE GET IS LANDSCAPE(IE. 90 DEGREE ROTATION).
        SO WE ROTATE THE CO-ORDINATES OF THE TOUCH EVENT AND THE VIEW SIZE*/

        //rotate the touch co-ordinates
        float rotatedTouchX = touchY;
        float rotatedTouchY = touchX;
        //rotate view size
        int rotatedViewWidth = viewHeight;
        int rotatedViewHeight = viewWidth;

        //find scaled touch co-ordinates
        int scaledX = (int)(rotatedTouchX * previewWidth)/rotatedViewWidth;
        int scaledY = previewHeight - (int)(rotatedTouchY * previewHeight)/rotatedViewHeight;

        //get pixel at co-ordinates, and convert it into hex codes
        int pixel = outputBitmap.getPixel(scaledX,scaledY);
        String hexColor = String.format("#%06X", (0xFFFFFF & pixel));

        String rgbColor = "RGB(" + Color.red(pixel) + "," + Color.green(pixel) + "," + Color.blue(pixel) + ")";

        float[] hsv = new float[3];
        Color.colorToHSV(pixel,hsv);
        String hsvColor = "HSV(" + (int)hsv[0] + "," + (int)hsv[1] + "," + (int)hsv[2] + ")";


        //set color text
        colorValHexText.setText(hexColor);
        colorValRgbText.setText(rgbColor);
        colorValHsvText.setText(hsvColor);

        //change colour of layout and status bar
        controlLayout.setBackgroundColor(pixel);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(pixel);
        }

    }


    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.destroy();
    }

}
