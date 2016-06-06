package com.medprimetech.camv1;

/**
 * Created by medprime5 on 6/6/16.
 */

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;



/**
 * Created by medprime5 on 5/6/16.
 */
public class RenameActivity extends Activity {
    private String DirName = "";
    private  String TAG="RenameActivity";
    private File imageFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.rename_layout);
        Log.d("Rename Activity", "Start");


        String [] myStrings;
        myStrings = getIntent().getStringArrayExtra("File_Address");
        imageFile = new File(myStrings[0]);

        DirName=myStrings[1];

        TextView fileNameView = (TextView) findViewById(R.id.textView2);
        fileNameView.setText(myStrings[1]);


        TextView fileNameView1 = (TextView) findViewById(R.id.textView3);
        fileNameView1.setText(imageFile.getName());


        if(imageFile.exists()){

            Bitmap myBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            ImageView myImage = (ImageView) findViewById(R.id.imageView);
            myImage.setRotation(90);
            myImage.setImageBitmap(myBitmap);

        }
    }

    public void cancelRename(View view) {
        Log.d(TAG, "cancelRename: ");

        File sdcard = new File(DirName);
        File from =  imageFile;
        File to = new File(sdcard,"to.jpg");
        from.renameTo(to);

    }

    public void okRename(View view) {


        EditText filename =(EditText) findViewById(R.id.editText);

        String s = filename.getText().toString();
        boolean hasNonAlpha = String.valueOf(s).matches("^.*[^a-zA-Z0-9 ].*$");
        Log.d(TAG, String.valueOf(hasNonAlpha));
        Log.d(TAG, String.valueOf(s));

        boolean hashas =s != null && !s.isEmpty() && !s.trim().isEmpty();
        Log.d(TAG, String.valueOf(hashas));
    }
}
