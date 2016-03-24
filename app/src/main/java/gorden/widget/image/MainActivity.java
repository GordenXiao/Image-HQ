package gorden.widget.image;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

public class MainActivity extends Activity {
    private HDImageView hdImageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hdImageView= (HDImageView) findViewById(R.id.hd);
        hdImageView.setImage(R.drawable.image2);
    }
}
