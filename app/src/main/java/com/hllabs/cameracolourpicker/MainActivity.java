package com.hllabs.cameracolourpicker;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v8.renderscript.RenderScript;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Flash;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;
import com.otaliastudios.cameraview.WhiteBalance;

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
    private ImageButton switchCamBtn, flashBtn;
    private Spinner whiteBalanceSpinner;

    private Facing currentCameraFacing = Facing.BACK;
    private Flash currentFlash = Flash.OFF;
    private WhiteBalance currentWb = WhiteBalance.AUTO;

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
        switchCamBtn = findViewById(R.id.btn_switch_camera);
        flashBtn = findViewById(R.id.btn_flash);
        whiteBalanceSpinner = findViewById(R.id.wb_spinner);


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

        //listener to toggle between front and back camera
        switchCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(currentCameraFacing == Facing.BACK){
                    currentCameraFacing = Facing.FRONT;
                }
                else {
                    currentCameraFacing = Facing.BACK;
                }

                cameraView.setFacing(currentCameraFacing);
            }
        });

        //listener to toggle flash
        flashBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(currentFlash == Flash.OFF){
                    currentFlash = Flash.TORCH;
                    flashBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_off_24dp,getTheme()));
                }
                else{
                    currentFlash = Flash.OFF;
                    flashBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_on_24dp,getTheme()));
                }
                cameraView.setFlash(currentFlash);
            }
        });

        //white balance spinner listener
        whiteBalanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                ((TextView) view).setTextColor(Color.WHITE);
                String selection = adapterView.getItemAtPosition(i).toString();
                switch (selection){
                    case "Incandescent":
                        currentWb = WhiteBalance.INCANDESCENT;
                        break;
                    case "Fluorescent":
                        currentWb = WhiteBalance.FLUORESCENT;
                        break;
                    case "Daylight":
                        currentWb = WhiteBalance.DAYLIGHT;
                        break;
                    case "Cloudy":
                        currentWb = WhiteBalance.CLOUDY;
                        break;
                    default:
                        currentWb = WhiteBalance.AUTO;
                        break;
                }
                cameraView.setWhiteBalance(currentWb);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
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

        //copy hex value to clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("color", hexColor);
        clipboard.setPrimaryClip(clip);

        //get current background colour
        int currentColor = Color.TRANSPARENT;
        Drawable background = controlLayout.getBackground();
        if (background instanceof ColorDrawable)
            currentColor = ((ColorDrawable) background).getColor();
        //animate to new colour
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(currentColor, pixel);
        valueAnimator.setDuration(500);
        valueAnimator.setInterpolator(new LinearInterpolator());
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                controlLayout.setBackgroundColor((Integer)valueAnimator.getAnimatedValue());

            }
        });
        valueAnimator.start();
        //set status bar colour
        getWindow().setStatusBarColor(pixel);

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
