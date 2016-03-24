package gorden.widget.image;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Created by gorden on 2016/3/24.
 */
public class HDAttacher implements View.OnTouchListener,ScaleGestureDetector.OnScaleGestureListener,ViewTreeObserver.OnGlobalLayoutListener{
    private static final String TAG="HDAttacher";

    private static float SCALE_MAX = 2.0f;
    private static float SCALE_MIN = 0.5f;
    private float initScale = 1.0f;

    /**
     * 缩放中
     */
    private boolean isAutoScale;

    private HDImageView imageView;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector gestureDetector;

    private float mScaleFactor = 1;
    private float mRotationDegrees = 0;
    private float mFocusX = 0;
    private float mFocusY = 0;

    public HDAttacher(final HDImageView imageView) {
        this.imageView = imageView;
        Context context = imageView.getContext();
        ViewTreeObserver observer = imageView.getViewTreeObserver();
        if (null != observer)
            observer.addOnGlobalLayoutListener(this);
        imageView.setOnTouchListener(this);
        mScaleGestureDetector=new ScaleGestureDetector(context,this);
        gestureDetector=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//                Log.e(TAG,"onScroll"+distanceX+":"+distanceY);
                mFocusX += distanceX;
                mFocusY += distanceY;
                imageView.setScale(mScaleFactor,mFocusX,mFocusY);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.e(TAG,"onDoubleTap"+imageView.getScale().fromX+"    "+imageView.getScale().fromY+"     "+e.getX()+"  :  "+e.getY());
                if (isAutoScale == true)
                    return true;
                if(getScale()!=initScale){
                    imageView.post(
                            new AutoScaleRunnable(initScale,mFocusX, mFocusY));
                    isAutoScale = true;
                }else{
                    imageView.post(
                            new AutoScaleRunnable(SCALE_MAX, mFocusX, mFocusY));
                    isAutoScale = true;
                }
                return true;
            }
        });
    }

    @Override
    public void onGlobalLayout() {
        initScaleSize(imageView.getWidth(), imageView.getHeight(), imageView.getImageWidth(), imageView.getImageHeight());
    }


    private class AutoScaleRunnable implements Runnable{
        final float BIGGER = 1.1f;
        final float SMALLER = 0.9f;
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
            if (imageView.getScale().scale < mTargetScale)
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
            mScaleFactor=getScale()*tmpScale;
//            imageView.setScale(mScaleFactor,mFocusX,mFocusY);
            setScale(tmpScale,x,y);
            float currentScale =getScale();
            if (((tmpScale > 1f) && (currentScale < mTargetScale))
                    || ((tmpScale < 1f) && (mTargetScale < currentScale)))
            {
                imageView.post(this);
            }else{
//                imageView.setScale(mTargetScale,mFocusX,mFocusY);
                setScale(mTargetScale / currentScale,x,y);
            }
            isAutoScale=false;

        }
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        mScaleGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        mScaleFactor *= detector.getScaleFactor();
        mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 100.0f));
        imageView.setScale(mScaleFactor, mFocusX, mFocusY);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }
    /**
     * 获得当前的缩放比
     *
     * @return
     */
    public final float getScale()
    {
        if(imageView==null)
            return 0;
        return imageView.getScale().getScale();
    }

    public void init(){
        initScaleSize(imageView.getWidth(),imageView.getHeight(),imageView.getImageWidth(), imageView.getImageHeight());
    }

    private void initScaleSize(int viewWidth,int viewHeight,int imgWidth,int imgHeight){
        if(viewWidth*viewHeight*imgWidth*imgHeight!=0){
            if(imgWidth>viewWidth||imgHeight>viewHeight){
                SCALE_MAX=Math.max((float) imgWidth / viewWidth, (float) imgHeight / viewHeight);
            }
            if(imgWidth<=viewWidth&&imgHeight<=viewHeight){
                SCALE_MAX=initScale*2;
            }
        }
    }

    private void setScale(float scale,float px,float py){
        Rect rect=imageView.getImgRect();
        Log.e(TAG,rect.left+"  "+rect.top+"  "+rect.right+"  "+rect.bottom);
        int width = imageView.getWidth();
        int height = imageView.getHeight();
//        if(rect.width()<=width){
//            px=width/2;
//        }
//        if(rect.height()<=height){
//            py=height/2;
//        }
        float currentFx=imageView.getScale().fromX;
        float currentFy=imageView.getScale().fromY;
        if(scale>0){
            mFocusX=currentFx+(px+currentFx)*(scale-1);
            mFocusY=currentFy+(py+currentFy)*(scale-1);
        }else{
            mFocusX=currentFx-(px+currentFx)*(1-scale);
            mFocusY=currentFy-(py+currentFy)*(1-scale);
        }
        mScaleFactor=getScale()*scale;
        imageView.setScale(mScaleFactor, mFocusX,mFocusY);
//        Log.e(TAG,mScaleFactor+"  "+mFocusX+":"+mFocusY);
    }
}
