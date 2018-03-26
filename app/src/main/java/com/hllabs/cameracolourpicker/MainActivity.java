package com.hllabs.cameracolourpicker;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v8.renderscript.RenderScript;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Flash;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;
import com.otaliastudios.cameraview.WhiteBalance;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
    private TextView colorValHexText,colorValRgbText;
    private ImageButton switchCamBtn, flashBtn, saveBtn,historyBtn;
    private Spinner whiteBalanceSpinner;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Menu navMenu;

    private Facing currentCameraFacing = Facing.BACK;
    private Flash currentFlash = Flash.OFF;
    private WhiteBalance currentWb = WhiteBalance.AUTO;

    //currently selected color
    private int currentSelectedColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_main);

        //initialise views
        controlLayout = findViewById(R.id.control_layout);
        colorValHexText = findViewById(R.id.text_color_hex);
        colorValRgbText = findViewById(R.id.text_color_rgb);
        cameraView = findViewById(R.id.camera);
        switchCamBtn = findViewById(R.id.btn_switch_camera);
        flashBtn = findViewById(R.id.btn_flash);
        saveBtn = findViewById(R.id.btn_save);
        whiteBalanceSpinner = findViewById(R.id.wb_spinner);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        historyBtn = findViewById(R.id.btn_history);


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

        //save the currently selected colour to shared preferences
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //get the shared preferences
                SharedPreferences pref = getApplicationContext().getSharedPreferences("colors",MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();

                //get the list of colors from the shared preferences
                Gson gson = new Gson();
                String json = pref.getString("list", null);
                List<Integer> colors;
                if(json != null) {
                    Type type = new TypeToken<ArrayList<Integer>>() {}.getType();
                    colors = gson.fromJson(json, type);
                }
                else colors = new ArrayList<>();
                //if there are 25 or above items, remove the first one
                if(colors.size() >= 25) colors.remove(0);
                //add the current value
                colors.add(currentSelectedColor);
                //generate the new json
                String newJson = gson.toJson(colors);
                //save it to the shared preferences
                editor.putString("list", newJson);
                editor.apply();

                Toast.makeText(getApplicationContext(),String.valueOf("Colour saved"),Toast.LENGTH_SHORT).show();

                refreshHistoryMenu();

            }
        });

        //open the drawer
        historyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawerLayout.openDrawer(Gravity.END);
            }
        });

        //refresh the history menu
        refreshHistoryMenu();

        //When a saved color is tapped, copy it to the clipboard
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(item.getItemId() != Menu.NONE){
                    String val = item.getTitle().toString();
                    copyToClipboard(val);
                    Toast.makeText(getApplicationContext(),val + " copied", Toast.LENGTH_SHORT).show();
                    return true;
                }
                else return false;
            }
        });

    }


    //clear out the history menu and refill it
    private void refreshHistoryMenu(){
        navMenu = navigationView.getMenu();
        //clear the menu
        navMenu.clear();
        //Add the
        navMenu.add(R.id.text_tap,Menu.NONE,Menu.NONE,"History(Tap to Copy)");

        //get the shared preferences
        SharedPreferences pref = getApplicationContext().getSharedPreferences("colors",MODE_PRIVATE);

        //get the list of colors from the shared preferences
        Gson gson = new Gson();
        String json = pref.getString("list", null);
        List<Integer> colors;

        if(json != null) {
            Type type = new TypeToken<ArrayList<Integer>>() {}.getType();
            //Get the color list, and add it to the nav menu
            colors = gson.fromJson(json, type);
            for(int i = 0; i < colors.size(); i++ ){

                int c = colors.get(i);
                //get the hex color and set it as item title
                String hexColor = String.format("#%06X", (0xFFFFFF & c));
                MenuItem m = navMenu.add(R.id.color_list,c,i,hexColor);
                //make the icon a circle of the color
                ShapeDrawable bgShape = new ShapeDrawable(new OvalShape());
                bgShape.setIntrinsicWidth(24);
                bgShape.setIntrinsicHeight(24);
                bgShape.setColorFilter(c, PorterDuff.Mode.SRC_IN);
                //set icon
                m.setIcon(bgShape);
            }

        }
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

        //get hex,rgb colour values
        String hexColor = String.format("#%06X", (0xFFFFFF & pixel));
        String rgbColor = "RGB(" + Color.red(pixel) + "," + Color.green(pixel) + "," + Color.blue(pixel) + ")";

        //set color text
        colorValHexText.setText(hexColor);
        colorValRgbText.setText(rgbColor);

        copyToClipboard(hexColor);

        //animate to new colour
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(currentSelectedColor, pixel);
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

        //set it as the current color
        currentSelectedColor = pixel;
    }


    private void copyToClipboard(String text){
        //copy hex value to clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("color", text);
        clipboard.setPrimaryClip(clip);

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
