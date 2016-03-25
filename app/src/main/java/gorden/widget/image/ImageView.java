package gorden.widget.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;

import java.io.InputStream;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Created by gorden on 2016/3/19.
 */
public class ImageView extends android.widget.ImageView implements HDManager.OnImageLoadListenner{
    private static final String TAG="gorden.imageview";
    private ImageViewAttacher attacher;
    private HDManager imageManager;
    private boolean hdLock=true;
    private boolean hDBrowse=false;
    private boolean gestureControl=false;

    public ImageView(Context context) {
        super(context);
        initImageView();
    }

    public ImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initImageView();


    }
    private void initImageView() {
        if(gestureControl&&attacher==null){
            attacher=new ImageViewAttacher(this);
            Log.e(TAG,"open");
        }else if(!gestureControl&&attacher!=null){
            Log.e(TAG,"close");
            attacher.cleanup();
            attacher=null;
        }
        if(hDBrowse&&imageManager==null){
            imageManager = new HDManager(getContext());
            imageManager.start(this);
        }else if(!hDBrowse&&imageManager!=null){
            imageManager.destroy();
            imageManager=null;
        }
    }
    @Override
    public void setImageDrawable(Drawable drawable) {
        Log.e(TAG, "setImageDrawable" + drawable.getIntrinsicWidth() + ":" + drawable.getIntrinsicHeight());
        super.setImageDrawable(drawable);
    }

    @Override
    protected void onAttachedToWindow() {
        Log.e(TAG,"onAttachedToWindow");
        super.onAttachedToWindow();
        if(hDBrowse){
            imageManager.start(this);
        }
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(null!=imageManager){
            imageManager.destroy();
        }
        Log.e(TAG, "onDetachedFromWindow");

    }

    @Override
    public void setImageResource(int resId) {
        if(hDBrowse){
            openDecoder(getResources().openRawResource(+resId));
        }else{
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getResources().openRawResource(+resId), null, options);
            if(Math.max(options.outHeight,options.outWidth)<getMaxTextureSize()){
            //正常加载
            Bitmap bitmap=BitmapFactory.decodeStream(getResources().openRawResource(+resId));
            setImageBitmap(bitmap);
            }else{
                // Bitmap too large 缩略
            options.inJustDecodeBounds=false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            int inSampleSize = Math.max(options.outHeight,options.outWidth)/getMaxTextureSize()+1;
            Log.e(TAG,inSampleSize+"倍压缩"+options.outWidth+":"+options.outHeight);
            options.inSampleSize=inSampleSize;
            Bitmap bitmap = BitmapFactory.decodeStream(getResources().openRawResource(+resId), null, options);
            setImageBitmap(bitmap);
            }
        }
    }

    public void setImagePath(String path){
        Log.e(TAG,path+"  l");
        if(hDBrowse){
            openDecoder(path);
        }else{
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            if(Math.max(options.outHeight,options.outWidth)<getMaxTextureSize()){
                //正常加载
                Bitmap bitmap=BitmapFactory.decodeFile(path);
                setImageBitmap(bitmap);
            }else{
                // Bitmap too large 缩略
                options.inJustDecodeBounds=false;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                int inSampleSize = Math.max(options.outHeight,options.outWidth)/getMaxTextureSize()+1;
                Log.e(TAG,inSampleSize+"倍压缩"+options.outWidth+":"+options.outHeight);
                options.inSampleSize=inSampleSize;
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                setImageBitmap(bitmap);
            }
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        //会调setImageDrawable
        Log.e(TAG, "setImageBitmap");
        super.setImageBitmap(bm);
    }

    public void openHdBrowse(boolean open){
        this.hDBrowse=open;
        if(open)
            this.gestureControl=true;
        initImageView();
    }

    public void setGestureControl(boolean gestureControl) {
        this.gestureControl = gestureControl;
        initImageView();
    }

    public boolean isHdBrowseModel(){
        return hDBrowse;
    }
    public boolean isOpenGestureControl(){
        return gestureControl;
    }
    /**
     * 获取位图最大支持值
     * @return
     */
    public static int getMaxTextureSize() {
        // Safe minimum default size
        final int IMAGE_MAX_BITMAP_DIMENSION = 2048;

        // Get EGL Display
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        // Initialise
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        // Query total number of configurations
        int[] totalConfigurations = new int[1];
        egl.eglGetConfigs(display, null, 0, totalConfigurations);

        // Query actual list configurations
        EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
        egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

        int[] textureSize = new int[1];
        int maximumTextureSize = 0;

        // Iterate through all the configurations to located the maximum texture size
        for (int i = 0; i < totalConfigurations[0]; i++) {
            // Only need to check for width since opengl textures are always squared
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

            // Keep track of the maximum texture size
            if (maximumTextureSize < textureSize[0])
                maximumTextureSize = textureSize[0];
        }

        // Release
        egl.eglTerminate(display);

        // Return largest texture size found, or default
        return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION);
    }

    public void setOnViewTapListener(ImageViewAttacher.OnViewTapListener listener){
        if(null!=attacher){
            attacher.setViewTapListener(listener);
        }
    }
    public void setOnMatrixChangeListener(ImageViewAttacher.OnMatrixChangedListener listener) {
        if(null!=attacher){
            attacher.setOnMatrixChangeListener(listener);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(hDBrowse){
            drawBitmapHD(canvas);
        }else{
            super.onDraw(canvas);
        }
//        Log.e(TAG, "ondraw");
//        super.onDraw(canvas);

//        notifyInvalidate(canvas);

    }

    @Override
    public void setImageMatrix(Matrix matrix) {
        if(hDBrowse){
            notifyInvalidate();
        }else{
            super.setImageMatrix(matrix);
        }
    }

    private volatile long preInvalidateTime;
    private volatile Runnable runnable;
    private static final int LOOP_TIME = 1;//更新间隔  S
    private void notifyInvalidate(){
        long deltaTime = SystemClock.uptimeMillis() - preInvalidateTime;
        if (runnable != null) {
            return;
        }
        if(deltaTime<LOOP_TIME){
            postDelayed(runnable = new Runnable() {
                @Override
                public void run() {
                    preInvalidateTime = SystemClock.uptimeMillis();
                    runnable = null;
                    invalidate(getVisiableRect());
                }
            },LOOP_TIME-deltaTime);
        }else{
            if (Looper.getMainLooper() == Looper.myLooper()) {
                preInvalidateTime = SystemClock.uptimeMillis();
                runnable = null;
                invalidate(getVisiableRect());
            } else {
                post(runnable = new Runnable() {
                    @Override
                    public void run() {
                        preInvalidateTime = SystemClock.uptimeMillis();
                        runnable = null;
                        invalidate(getVisiableRect());
                    }
                });
            }
        }
    }

    private Drawable drawable;
    public void drawBitmapHD(final Canvas canvas){
        if(!hdLock){
            if(getWidth()==0)
                return;
            long startTime = SystemClock.uptimeMillis();
            Rect visiableRect = getVisiableRect();
            startTime = SystemClock.uptimeMillis();
            if (!imageManager.hasLoad()) {
                if (drawable != null) {
                    int saveCount = canvas.save();
                    drawable.draw(canvas);
                    canvas.restoreToCount(saveCount);
                }
                return;
            }
            int saveCount = canvas.save();
            startTime = SystemClock.uptimeMillis();

            int imgWidth = imageManager.getWidth();
            int imgHeight=imageManager.getHeight();
            RectF rectF=attacher.getCurrentRectF();
            float height=rectF.height();
            float width=rectF.width();
            float imageScale=Math.max(imgHeight/height,imgWidth/width);
            Rect imageRect = new Rect();
            imageRect.left= (int) Math.ceil((getVisiableRect().left-rectF.left)*imageScale);
            imageRect.right= (int) Math.ceil((getVisiableRect().right-rectF.left)*imageScale);
            imageRect.top= (int) Math.ceil((getVisiableRect().top-rectF.top)*imageScale);
            imageRect.bottom= (int) Math.ceil((getVisiableRect().bottom-rectF.top)*imageScale);
            startTime = SystemClock.uptimeMillis();

            List<HDManager.DrawData> drawData = imageManager.getDrawData(imageScale, imageRect);
            startTime = SystemClock.uptimeMillis();
            for (HDManager.DrawData data : drawData) {
                Rect drawRect = data.imageRect;
                drawRect.left = (int) (drawRect.left / imageScale + rectF.left);
                drawRect.top = (int) (drawRect.top / imageScale + rectF.top);
                drawRect.right = (int) (drawRect.right / imageScale + rectF.left);
                drawRect.bottom = (int) (drawRect.bottom / imageScale + rectF.top);
                canvas.drawBitmap(data.bitmap, data.srcRect, drawRect, null);
            }
            canvas.restoreToCount(saveCount);
        }
    }


    private void openDecoder(InputStream inputStream){
        hdLock=false;
        imageManager.load(inputStream);
    }
    private void openDecoder(String filePath){
        if(filePath==null){
            return;
        }
        hdLock=false;
        imageManager.load(filePath);
    }

    @Override
    public void onBlockImageLoadFinished() {
        notifyInvalidate();
    }

    @Override
    public void onImageLoadFinished(int imageWidth, int imageHeight) {
        notifyInvalidate();
        attacher.initLayout();
        Log.e(TAG, "onImageLoadFinished");
    }

    public int getImageWidth() {
        if(hDBrowse){
            if (imageManager != null) {
                return imageManager.getWidth();
            }
        }else{
            return getDrawable().getIntrinsicWidth();
        }

        return 0;
    }
    public int getImageHeight() {
        if(hDBrowse){
            if (imageManager != null) {
                return imageManager.getHeight();
            }
        }else{
            return getDrawable().getIntrinsicHeight();
        }

        return 0;
    }

    /**
     * 获取当前显示区域相对原始数据的坐标
     * @return
     */
    protected Rect getVisiableRect(){
        Rect visiableRect = new Rect();
        getGlobalVisibleRect(visiableRect);
        int[] location = new int[2];
        getLocationOnScreen(location);
        visiableRect.left = visiableRect.left - location[0];
        visiableRect.right = visiableRect.right - location[0];
        visiableRect.top = visiableRect.top - location[1];
        visiableRect.bottom = visiableRect.bottom - location[1];
//        Log.e(TAG,visiableRect.left+" : "+visiableRect.top+" : "+visiableRect.right+" : "+visiableRect.bottom);
        return visiableRect;
    }
}
