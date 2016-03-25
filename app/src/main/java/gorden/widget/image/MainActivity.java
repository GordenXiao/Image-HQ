package gorden.widget.image;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

public class MainActivity extends Activity {
    public static final int REQUESTCODE_SELECTIMAGE = 666;

    private gorden.widget.image.ImageView hdImageView;
    private Button select_Pic,open_gesture,open_hdBrower;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hdImageView= (gorden.widget.image.ImageView) findViewById(R.id.hd);
        select_Pic= (Button) findViewById(R.id.select_Pic);
        open_hdBrower= (Button) findViewById(R.id.open_hdBrower);
        open_gesture= (Button) findViewById(R.id.open_gesture);

        open_hdBrower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hdImageView.isHdBrowseModel()){
                    hdImageView.openHdBrowse(false);
                    open_hdBrower.setText("打开高清预览");
                }else{
                    hdImageView.openHdBrowse(true);
                    open_hdBrower.setText("关闭高清预览");
                    open_gesture.setText("关闭手势控制");
                }
            }
        });
        open_gesture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hdImageView.isOpenGestureControl()){
                    hdImageView.setGestureControl(false);
                    open_gesture.setText("打开手势控制");
                }else{
                    hdImageView.setGestureControl(true);
                    open_gesture.setText("关闭手势控制");
                }
            }
        });
        select_Pic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent();
                    intent.setType("image/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(intent, REQUESTCODE_SELECTIMAGE);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
        hdImageView.setImageResource(R.drawable.image2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUESTCODE_SELECTIMAGE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri selectedImage = data.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String picturePath = cursor.getString(columnIndex);
//                hdImageView.setImage(picturePath);
                hdImageView.setImagePath(picturePath);
            }
        }

    }
}
