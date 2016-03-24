package gorden.widget.image;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.io.InputStream;
import java.util.List;

/**
 * Created by gorden on 2016/3/24.
 */
public class HDImageView extends View implements HDManager.OnImageLoadListenner{
    private static final String TAG="HDImageView";
    private HDManager imageManager;
    private HDAttacher hdAttacher;
    private Rect currentRect;

    public HDImageView(Context context) {
        this(context, null);
    }

    public HDImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HDImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public HDImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView();
    }

    private void initView() {
        this.isInEditMode();
        imageManager = new HDManager(getContext());
        hdAttacher=new HDAttacher(this);
    }
    public int getImageWidth() {
        if (imageManager != null) {
            return imageManager.getWidth();
        }
        return 0;
    }
    public int getImageHeight() {
        if (imageManager != null) {
            return imageManager.getHeight();
        }
        return 0;
    }
    public Scale getScale() {
        return mScale;
    }

    private Scale mScale = new Scale(1, 0, 0);
    private Drawable drawable;

    public void setDefaulImage(Drawable drawable) {
        this.drawable = drawable;
    }
    public void setImage(InputStream inputStream){
        mScale.setScale(1);
        mScale.fromX = 0;
        mScale.fromY = 0;
        imageManager.load(inputStream);
    }
    public void setImage(String filePath){
        mScale.setScale(1);
        mScale.fromX = 0;
        mScale.fromY = 0;
        imageManager.load(filePath);
    }
    public void setImage(int resId){
        setImage(getResources().openRawResource(resId));
    }
    public void setScale(float scale, float offsetX, float offsetY) {
        mScale.setScale(scale);
        mScale.setFromX((int) offsetX);
        mScale.setFromY((int) offsetY);
        notifyInvalidate();
    }
    private boolean lock;
    int[] mLocation = new int[2];
    boolean mVisible = false;
    private Rect mVisiableRect;
    boolean mRequestedVisible = false;
    protected void lock() {
        lock = true;
    }
    protected void unLock() {
        lock = false;
    }

    boolean mWindowVisibility = false;
    boolean mViewVisibility = false;
    private boolean mGlobalListenersAdded;
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisibility = visibility == VISIBLE;
        mRequestedVisible = mWindowVisibility && mViewVisibility;
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mViewVisibility = visibility == VISIBLE;
        boolean newRequestedVisible = mWindowVisibility && mViewVisibility;
        if (newRequestedVisible != mRequestedVisible) {
            requestLayout();
        }
        mRequestedVisible = newRequestedVisible;
    }
    final ViewTreeObserver.OnScrollChangedListener mScrollChangedListener = new ViewTreeObserver.OnScrollChangedListener() {

        @Override
        public void onScrollChanged() {
            updateWindow(false, false);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mViewVisibility = getVisibility() == VISIBLE;

        if (!mGlobalListenersAdded) {
            ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnScrollChangedListener(mScrollChangedListener);
            mGlobalListenersAdded = true;
        }
        imageManager.start(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        imageManager.destroy();
        if (mGlobalListenersAdded) {
            ViewTreeObserver observer = getViewTreeObserver();
            observer.removeOnScrollChangedListener(mScrollChangedListener);
            mGlobalListenersAdded = false;
        }
        mRequestedVisible = false;
        updateWindow(false, false);
        super.onDetachedFromWindow();
    }

    /**
     * 更新
     * @param force true 则会update
     * @param redrawNeeded   true 则会update
     */
    private void updateWindow(boolean force, boolean redrawNeeded) {
        if (lock) {
            return;
        }
        int[] tempLocationInWindow = new int[2];
        getLocationInWindow(tempLocationInWindow);
        final boolean visibleChanged = mVisible != mRequestedVisible;
        if (force || visibleChanged || tempLocationInWindow[0] != mLocation[0] || tempLocationInWindow[1] != mLocation[1] || redrawNeeded) {
            this.mLocation = tempLocationInWindow;
            Rect visiableRect = getVisiableRect();
            if (mVisiableRect == null || !mVisiableRect.equals(visiableRect)) {
                this.mVisiableRect = visiableRect;
                onUpdateWindow(visiableRect);
            }
        }
    }
    protected void onUpdateWindow(Rect visiableRect){
        preInvalidateTime = SystemClock.uptimeMillis();
        runnable = null;
        invalidate(getVisiableRect());
    }
    private volatile long preInvalidateTime;
    private volatile Runnable runnable;
    private static final int LOOP_TIME = 17;//更新间隔  S
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

    @Override
    protected void onDraw(Canvas canvas) {
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

        float width = mScale.scale * getWidth();
        int imgWidth = imageManager.getWidth();
        float height=mScale.scale*getHeight();
        int imgHeight=imageManager.getHeight();
        float imageScale =Math.max(imgWidth / width, imgHeight / height);
        // 需要显示的图片的实际宽度。
        Rect imageRect = new Rect();
        imageRect.left = (int) Math.ceil((visiableRect.left + mScale.fromX) * imageScale);
        imageRect.top = (int) Math.ceil((visiableRect.top + mScale.fromY) * imageScale);
        imageRect.right = (int) Math.ceil((visiableRect.right + mScale.fromX) * imageScale);
        imageRect.bottom = (int) Math.ceil((visiableRect.bottom + mScale.fromY) * imageScale);
        startTime = SystemClock.uptimeMillis();
//        Log.e(TAG,imageRect.left+" : "+imageRect.top+" : "+imageRect.right+" : "+imageRect.bottom+"     width:"+visiableRect.width());
        List<HDManager.DrawData> drawData = imageManager.getDrawData(imageScale, imageRect);

        startTime = SystemClock.uptimeMillis();
        for (HDManager.DrawData data : drawData) {
            currentRect=data.imageRect;
            Rect drawRect = data.imageRect;
            drawRect.left = (int) (drawRect.left / imageScale - mScale.fromX);
            drawRect.top = (int) (drawRect.top / imageScale - mScale.fromY);
            drawRect.right = (int) (Math.ceil(drawRect.right / imageScale) - mScale.fromX);
            drawRect.bottom = (int) (Math.ceil(drawRect.bottom / imageScale) - mScale.fromY);
//            Log.e(TAG,currentRect.left+" : "+currentRect.top+" : "+data.imageRect.right+" : "+data.imageRect.bottom);
            canvas.drawBitmap(data.bitmap, data.srcRect, drawRect, null);
        }
        canvas.restoreToCount(saveCount);
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

    @Override
    public void onBlockImageLoadFinished() {
        notifyInvalidate();
    }

    @Override
    public void onImageLoadFinished(final int imageWidth, final int imageHeight) {
        Log.e(TAG,imageWidth+":"+imageHeight);
        hdAttacher.init();
        notifyInvalidate();
    }
    public class Scale {
        volatile float scale;
        volatile int fromX;
        volatile int fromY;

        public Scale(float scale, int fromX, int fromY) {
            super();
            this.scale = scale;
            this.fromX = fromX;
            this.fromY = fromY;
        }

        public float getScale() {
            return scale;
        }

        void setScale(float scale) {
            this.scale = scale;
        }

        public int getFromX() {
            return fromX;
        }

        void setFromX(int fromX) {
            this.fromX = fromX;
        }

        public int getFromY() {
            return fromY;
        }

        void setFromY(int fromY) {
            this.fromY = fromY;
        }
    }

    public Rect getImgRect(){
        return currentRect;
    }

}
