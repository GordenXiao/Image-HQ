package gorden.widget.image;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.OverScroller;

/**
 * Created by gorden on 2016/3/22.
 */
public class ImageViewAttacher implements View.OnTouchListener,ScaleGestureDetector.OnScaleGestureListener,ViewTreeObserver.OnGlobalLayoutListener{
    private static final String TAG="ImageViewAttacher";
    private RectF currentRectF;
    private ImageView mImageView;
    private Context mContext;
    private FlingRunnable mCurrentFlingRunnable;


    /**
     * 缩放中
     */
    private boolean isAutoScale;
    private boolean isScaling;
    public static float SCALE_MAX = 4.0f;
    private static float SCALE_MID = 1.0f;

    /**
     * 初始化时的缩放比例，如果图片宽或高大于屏幕
     */
    private float initScale = 2.0f;

    /**
     * 用于存放矩阵9个值
     */
    private final float[] matrixValues = new float[9];
    private Matrix mScaleMatrix = new Matrix();

    private ScaleGestureDetector mScaleGestureDetector = null;
    private GestureDetector mGestureDetector;
    private OnViewTapListener viewTapListener;
    private OnMatrixChangedListener mMatrixChangeListener;
    private OnScaleChangeListener mScaleChangeListener;
    private int lastPointerCount;
    private float mLastX;
    private float mLastY;


    public ImageViewAttacher(ImageView imageView){
        this.mImageView=imageView;
        mContext=mImageView.getContext();
        mImageView.setDrawingCacheEnabled(true);
        mImageView.setOnTouchListener(this);
        ViewTreeObserver observer = imageView.getViewTreeObserver();
        if (null != observer)
            observer.addOnGlobalLayoutListener(this);
        mImageView.setScaleType(android.widget.ImageView.ScaleType.MATRIX);
        if (imageView.isInEditMode()) { //可视化编辑器报错解决
            return;
        }

        mGestureDetector=new GestureDetector(mContext,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isAutoScale == true)
                    return true;
                float x = e.getX();
                float y = e.getY();
                if(getScale()!=initScale){
                    mImageView.post(
                            new AutoScaleRunnable(initScale, x, y));
                    isAutoScale = true;
                }else{
                    mImageView.post(
                            new AutoScaleRunnable(SCALE_MAX, x, y));
                    isAutoScale = true;
                }

                return true;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                mCurrentFlingRunnable=new FlingRunnable(mContext);
                mCurrentFlingRunnable.fling(mImageView.getWidth(),mImageView.getHeight(),-(int) velocityX, -(int) velocityY);
                mImageView.post(mCurrentFlingRunnable);
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if(null!=viewTapListener){
                    viewTapListener.onViewTap(mImageView,e.getX(),e.getY());
                }
                return true;
            }
        });
        mScaleGestureDetector=new ScaleGestureDetector(mContext,this);
    }

    /**
     * 自动缩放
     */
    private class AutoScaleRunnable implements Runnable{

        static final float BIGGER = 1.1f;
        static final float SMALLER = 0.9f;
        private float mTargetScale;
        private float tmpScale;

        private float x;
        private float y;
        /**
         * 传入目标缩放值，根据目标值与当前值，判断应该放大还是缩小
         *
         * @param targetScale
         */
        public AutoScaleRunnable(float targetScale, float x, float y)
        {
            this.mTargetScale = targetScale;
            if (getScale() < mTargetScale)
            {
                tmpScale = BIGGER;
            } else {
                tmpScale = SMALLER;
            }
            this.x = x;
            this.y = y;

        }
        @Override
        public void run() {
            cancelFling();
            checkBorderScale(tmpScale, x, y);
            if (null != mScaleChangeListener) {
                mScaleChangeListener.onScaleChange(tmpScale,x,y);
            }
            setImageViewMatrix(mScaleMatrix);
            float currentScale = getScale();
            if (((tmpScale > 1f) && (currentScale < mTargetScale))
                    || ((tmpScale < 1f) && (mTargetScale < currentScale)))
            {
                mImageView.post(this);
            }else{
                float deltaScale = mTargetScale / currentScale;
                checkBorderScale(deltaScale, x, y);
                if (null != mScaleChangeListener) {
                    mScaleChangeListener.onScaleChange(deltaScale,x,y);
                }
                setImageViewMatrix(mScaleMatrix);
                isAutoScale = false;
            }
        }
    }

    public void setViewTapListener(OnViewTapListener viewTapListener) {
        this.viewTapListener = viewTapListener;
    }
    public void setOnMatrixChangeListener(OnMatrixChangedListener listener) {
        mMatrixChangeListener = listener;
    }
    public void setOnScaleChangeListener(OnScaleChangeListener onScaleChangeListener) {
        this.mScaleChangeListener = onScaleChangeListener;
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(mGestureDetector.onTouchEvent(event))
            return true;
        mScaleGestureDetector.onTouchEvent(event);

        float x = 0, y = 0;
        // 拿到触摸点的个数
        final int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++)
        {
            x += event.getX(i);
            y += event.getY(i);
        }
        x = x / pointerCount;
        y = y / pointerCount;
        if (pointerCount != lastPointerCount)
        {
            mLastX = x;
            mLastY = y;
        }
        lastPointerCount = pointerCount;
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                cancelFling();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastX;
                float dy = y - mLastY;
                if(!isScaling){
                    checkBorderTranslate(dx,dy);
                    setImageViewMatrix(mScaleMatrix);
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastPointerCount = 0;
                break;
        }
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scale=getScale();
        float scaleFactor = detector.getScaleFactor();
        if(scale>=SCALE_MID){
            if (scaleFactor * scale < SCALE_MID)
            {
                scaleFactor = SCALE_MID / scale;
            }
            checkBorderScale(scaleFactor,detector.getFocusX(),detector.getFocusY());
            if (null != mScaleChangeListener) {
                mScaleChangeListener.onScaleChange(scaleFactor, detector.getFocusX(), detector.getFocusY());
            }
            setImageViewMatrix(mScaleMatrix);
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        isScaling=true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        float scale=getScale();
        if(scale>SCALE_MAX){
            mImageView.post(new AutoScaleRunnable(SCALE_MAX, detector.getFocusX(), detector.getFocusY()));
        }else if(scale<initScale){
            mImageView.post(new AutoScaleRunnable(initScale,detector.getFocusX(),detector.getFocusY()));
        }
        isScaling=false;

    }

    @Override
    public void onGlobalLayout() {
        Log.e(TAG, "onGlobalLayout");
        initLayout();
    }

    public void cleanup() {
        if (null == mImageView) {
            return; // cleanup already done
        }
        ViewTreeObserver observer = mImageView.getViewTreeObserver();
        if (null != observer && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(this);
        }
        mImageView.setOnTouchListener(null);
        cancelFling();
        mMatrixChangeListener=null;
        mScaleChangeListener=null;
    }

    private void checkBorderScale(float scale,float px,float py){
        RectF rect=getCurrentRectF();
        int width = mImageView.getWidth();
        int height = mImageView.getHeight();
        if(rect.width()<=width){
            px=width/2;
        }
        if(rect.height()<=height){
            py=height/2;
        }
        mScaleMatrix.postScale(scale, scale, px, py);
        checkBorderMove();
    }
    private void checkBorderTranslate(float dx,float dy){
        RectF rect=getCurrentRectF();
        if (rect.width()<= mImageView.getWidth())
        {
            dx = 0;
        }else{
            dx=rect.left+dx>0?-rect.left:dx;
            dx=rect.right+dx<mImageView.getWidth()?mImageView.getWidth()-rect.right:dx;
        }
        if (rect.height()<= mImageView.getHeight())
        {
            dy = 0;
        }else{
            dy=rect.top+dy>0?-rect.top:dy;
            dy=rect.bottom+dy<mImageView.getHeight()?mImageView.getHeight()-rect.bottom:dy;
        }
        mScaleMatrix.postTranslate(dx, dy);
    }
    private void checkBorderMove(){
        RectF rect=getCurrentRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = mImageView.getWidth();
        int height = mImageView.getHeight();
        if(rect.width()<=width){
            deltaX=width/2-rect.width()/2-rect.left;
        }else {
            deltaX=rect.left>0?-rect.left:0;
            deltaX=rect.right<width?width-rect.right:deltaX;
        }
        if(rect.height()<=height){
            deltaY=height/2-rect.height()/2-rect.top;
        }else {
            deltaY=rect.top>0?-rect.top:0;
            deltaY=rect.bottom<height?height-rect.bottom:deltaY;
        }
        mScaleMatrix.postTranslate(deltaX, deltaY);
    }
    /**
     * 轻点击
     */
    public interface OnViewTapListener {
        void onViewTap(View view, float x, float y);
    }

    public interface OnMatrixChangedListener{
        void onMatrixChanged(RectF rect);
    }

    public interface OnScaleChangeListener {
        void onScaleChange(float scaleFactor, float focusX, float focusY);
    }
    /**
     * 获得当前的缩放比
     *
     * @return
     */
    public final float getScale()
    {
        mScaleMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private void setImageViewMatrix(Matrix matrix){
        mImageView.setImageMatrix(matrix);
        if (null != mMatrixChangeListener) {
            RectF displayRect = getCurrentRectF();
            if (null != displayRect) {
                mMatrixChangeListener.onMatrixChanged(displayRect);
            }
        }
    }
    private void cancelFling() {
        if (null != mCurrentFlingRunnable) {
            mCurrentFlingRunnable.cancelFling();
            mCurrentFlingRunnable = null;
        }
    }

    private class FlingRunnable implements Runnable {

        private final OverScroller mScroller;
        private int mCurrentX, mCurrentY;

        public FlingRunnable(Context context) {
            mScroller = new OverScroller(context);
        }

        public void cancelFling() {
            mScroller.forceFinished(true);
        }

        public void fling(int viewWidth, int viewHeight, int velocityX,
                          int velocityY) {
            final RectF rect = getCurrentRectF();
            if (null == rect) {
                return;
            }

            final int startX = Math.round(-rect.left);
            final int minX, maxX, minY, maxY;

            if (viewWidth < rect.width()) {
                minX = 0;
                maxX = Math.round(rect.width() - viewWidth);
            } else {
                minX = maxX = startX;
            }

            final int startY = Math.round(-rect.top);
            if (viewHeight < rect.height()) {
                minY = 0;
                maxY = Math.round(rect.height() - viewHeight);
            } else {
                minY = maxY = startY;
            }

            mCurrentX = startX;
            mCurrentY = startY;
            // If we actually can move, fling the scroller
            if (startX != maxX || startY != maxY) {
                mScroller.fling(startX, startY, velocityX, velocityY, minX,
                        maxX, minY, maxY, 0, 0);
            }
        }

        @Override
        public void run() {
            if (mScroller.isFinished()) {
                return; // remaining post that should not be handled
            }


            if ( mScroller.computeScrollOffset()) {

                final int newX = mScroller.getCurrX();
                final int newY = mScroller.getCurrY();
                mScaleMatrix.postTranslate(mCurrentX - newX, mCurrentY - newY);
                setImageViewMatrix(mScaleMatrix);
                mCurrentX = newX;
                mCurrentY = newY;
                mImageView.postOnAnimation(this);
            }
        }
    }

    public void initLayout(){
        Log.e(TAG,"initLayout");
        int viewWidth = mImageView.getWidth();
        int viewHeight = mImageView.getHeight();
        int imgWidth=mImageView.getImageWidth();
        int imgHeight=mImageView.getImageHeight();
        if(viewHeight*viewWidth*imgWidth*imgHeight==0)
            return;
        float scale=1.0f;
        if(imgWidth>viewWidth||imgHeight>viewHeight){
            scale=Math.max((float) imgWidth / viewWidth, (float) imgHeight / viewHeight);
        }
        mScaleMatrix.reset();
        mScaleMatrix.postTranslate((viewWidth - imgWidth) / 2, (viewHeight - imgHeight) / 2);
        mScaleMatrix.postScale(1 / scale, 1 / scale, viewWidth / 2, viewHeight / 2);
        currentRectF=new RectF(0,0,imgWidth,imgHeight);
        mScaleMatrix.mapRect(currentRectF);
        setImageViewMatrix(mScaleMatrix);
        initScale=1/scale;
        if(initScale>0.5){
            SCALE_MAX=2;
        }else{
            SCALE_MAX=1;
        }
        SCALE_MID=initScale/2;
    }
    public RectF getCurrentRectF(){
        currentRectF=new RectF(0,0,mImageView.getImageWidth(),mImageView.getImageHeight());
        mScaleMatrix.mapRect(currentRectF);
        return currentRectF;
    }
}
