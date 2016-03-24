package gorden.widget.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by gorden on 2016/3/24.
 */
public class HDManager {
    private final int BASE_BLOCKSIZE;
    private Context context;
    private HandlerThread handlerThread;
    private LoadHandler handler;
    private volatile LoadData mLoadData;

    public HDManager(Context context) {
        this.context=context;
        int width = context.getResources().getDisplayMetrics().widthPixels / 2;
        BASE_BLOCKSIZE = width;
    }

    public void start(OnImageLoadListenner invalidateListener){
        this.onImageLoadListenner = invalidateListener;
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        handlerThread=new HandlerThread("111");
        handlerThread.start();
        handler = new LoadHandler(handlerThread.getLooper());
        LoadData loadData = mLoadData;
        if (loadData != null && loadData.mFactory != null) {
            load(loadData.mFactory);
        }
    }
    public boolean isStart() {
        return handlerThread != null;
    }

    public int getNearScale(float imageScale) {
        int scale = (int) imageScale;
        int startS = 1;
        if (scale > 2) {
            do {
                startS *= 2;
            } while ((scale = scale / 2) > 2);
        }
        if (Math.abs(startS - imageScale) < Math.abs(startS * 2 - imageScale)) {
            scale = startS;
        } else {
            scale = startS * 2;
        }
        return scale;
    }

    public boolean hasLoad() {
        LoadData loadData = mLoadData;
        return loadData != null && loadData.mDecoder != null;
    }
    private Rect madeRect(Bitmap bitmap, int row, int col, int scaleKey, float imageScale) {
        int size = scaleKey * BASE_BLOCKSIZE;
        Rect rect = new Rect();
        rect.left = (col * size);
        rect.top = (row * size);
        rect.right = rect.left + bitmap.getWidth() * scaleKey;
        rect.bottom = rect.top + bitmap.getHeight() * scaleKey;
        return rect;
    }

    public List<DrawData> getDrawData(float imageScale, Rect imageRect) {
        long startTime = SystemClock.uptimeMillis();
        LoadData loadData = mLoadData;
        if (loadData == null || loadData.mDecoder == null) {
            return new ArrayList<DrawData>(0);
        }
        int imageWidth = loadData.mImageWidth;
        int imageHeight = loadData.mImageHeight;
        List<CacheData> cacheDatas = loadData.mCacheDatas;
        Bitmap cacheImageData = loadData.mCacheImageData;
        int cacheImageScale = loadData.mCacheImageScale;

        List<DrawData> drawDatas = new ArrayList<DrawData>();
        if (imageRect.left < 0) {
            imageRect.left = 0;
        }
        if (imageRect.top < 0) {
            imageRect.top = 0;
        }
        if (imageRect.right > loadData.mImageWidth) {
            imageRect.right = loadData.mImageWidth;
        }
        if (imageRect.bottom > loadData.mImageHeight) {
            imageRect.bottom = loadData.mImageHeight;
        }

        if (cacheImageData == null) {
            try {
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                int s = (int) Math.sqrt(imageWidth * imageHeight / (screenWidth / 2) / (screenHeight / 2));
                cacheImageScale = getNearScale(s);
                if (cacheImageScale < s) {
                    cacheImageScale *= 2;
                }
                handler.sendMessage(handler.obtainMessage(MESSAGE_PIC, cacheImageScale));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Rect cacheImageRect = new Rect(imageRect);
            int cache = dip2px(context, 100);
            cache = (int) (cache * imageScale);
            cacheImageRect.right += cache;
            cacheImageRect.top -= cache;
            cacheImageRect.left -= cache;
            cacheImageRect.bottom += cache;

            if (cacheImageRect.left < 0) {
                cacheImageRect.left = 0;
            }
            if (cacheImageRect.top < 0) {
                cacheImageRect.top = 0;
            }
            if (cacheImageRect.right > imageWidth) {
                cacheImageRect.right = imageWidth;
            }
            if (cacheImageRect.bottom > imageHeight) {
                cacheImageRect.bottom = imageHeight;
            }
            Rect r = new Rect();
            r.left = (int) Math.abs(1.0f * cacheImageRect.left / cacheImageScale);
            r.right = (int) Math.abs(1.0f * cacheImageRect.right / cacheImageScale);
            r.top = (int) Math.abs(1.0f * cacheImageRect.top / cacheImageScale);
            r.bottom = (int) Math.abs(1.0f * cacheImageRect.bottom / cacheImageScale);
            drawDatas.add(new DrawData(cacheImageData, r, cacheImageRect));

            Log.d("vvvv", "imageRect:" + imageRect + " tempImageScale:" + cacheImageScale);
            Log.d("vvvv", "rect:" + r);
        }
        int scale = getNearScale(imageScale);
        if (cacheImageScale <= scale && cacheImageData != null) {
            return drawDatas;
        }
        // if (true) {
        // return drawDatas;
        // }
        Log.d("dddd", "scale: " + scale);
        int blockSize = BASE_BLOCKSIZE * scale;
        int maxRow = imageHeight / blockSize + (imageHeight % blockSize == 0 ? 0 : 1);
        int maxCol = imageWidth / blockSize + (imageWidth % blockSize == 0 ? 0 : 1);

        // 该scale下对应的position范围
        int startRow = imageRect.top / blockSize + (imageRect.top % blockSize == 0 ? 0 : 1) - 1;
        int endRow = imageRect.bottom / blockSize + (imageRect.bottom % blockSize == 0 ? 0 : 1);
        int startCol = imageRect.left / blockSize + (imageRect.left % blockSize == 0 ? 0 : 1) - 1;
        int endCol = imageRect.right / blockSize + (imageRect.right % blockSize == 0 ? 0 : 1);
        if (startRow < 0) {
            startRow = 0;
        }
        if (startCol < 0) {
            startCol = 0;
        }
        if (endRow > maxRow) {
            endRow = maxRow;
        }
        if (endCol > maxCol) {
            endCol = maxCol;
        }

        int cacheStartRow = startRow - 1;
        int cacheEndRow = endRow + 1;
        int cacheStartCol = startCol - 1;
        int cacheEndCol = endCol + 1;
        if (cacheStartRow < 0) {
            cacheStartRow = 0;
        }
        if (cacheStartCol < 0) {
            cacheStartCol = 0;
        }
        if (cacheEndRow > maxRow) {
            cacheEndRow = maxRow;
        }
        if (cacheEndCol > maxCol) {
            cacheEndCol = maxCol;
        }

        Log.d("countTime", "preTime :" + (SystemClock.uptimeMillis() - startTime));
        startTime = SystemClock.uptimeMillis();

        Set<Position> needShowPositions = new HashSet<Position>();

        // 移除掉之前的任务
        handler.removeMessages(preMessageWhat);
        int what = preMessageWhat == MESSAGE_BLOCK_1 ? MESSAGE_BLOCK_2 : MESSAGE_BLOCK_1;
        preMessageWhat = what;

        if (loadData.mCurrentCacheData != null && loadData.mCurrentCacheData.scale != scale) {
            cacheDatas.add(new CacheData(loadData.mCurrentCacheData.scale, new HashMap<Position, Bitmap>(loadData.mCurrentCacheData.images)));
            loadData.mCurrentCacheData = null;
        }
        if (loadData.mCurrentCacheData == null) {
            Iterator<CacheData> iterator = cacheDatas.iterator();
            while (iterator.hasNext()) {
                CacheData cacheData = iterator.next();
                if (scale == cacheData.scale) {
                    loadData.mCurrentCacheData = new CacheData(scale, new ConcurrentHashMap<Position, Bitmap>(cacheData.images));
                    iterator.remove();
                }
            }
        }
        if (loadData.mCurrentCacheData == null) {
            loadData.mCurrentCacheData = new CacheData(scale, new ConcurrentHashMap<Position, Bitmap>());
            for (int row = startRow; row <= endRow; row++) {
                for (int col = startCol; col <= endCol; col++) {
                    Position position = new Position(row, col);
                    needShowPositions.add(position);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }

            /**
             * <pre>
             * #########  1
             * #       #
             * #       #
             * #########
             *
             * <pre>
             */

            // 上 #########
            for (int row = cacheStartRow; row < startRow; row++) {
                for (int col = cacheStartCol; col <= cacheEndCol; col++) {
                    Position position = new Position(row, col);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            // 下 #########
            for (int row = endRow + 1; row < cacheEndRow; row++) {
                for (int col = cacheStartCol; col <= cacheEndCol; col++) {
                    Position position = new Position(row, col);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            // # 左
            // #
            for (int row = startRow; row < endRow; row++) {
                for (int col = cacheStartCol; col < startCol; col++) {
                    Position position = new Position(row, col);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            // # 右
            // #
            for (int row = startRow; row < endRow; row++) {
                for (int col = endRow + 1; col < cacheEndRow; col++) {
                    Position position = new Position(row, col);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
        } else {
			/*
			 * 找出该scale所有存在的切图，和记录所有不存在的position
			 */
            Set<Position> usePositions = new HashSet<Position>();
            for (int row = startRow; row <= endRow; row++) {
                for (int col = startCol; col <= endCol; col++) {
                    Position position = new Position(row, col);
                    Bitmap bitmap = loadData.mCurrentCacheData.images.get(position);
                    if (bitmap == null) {
                        needShowPositions.add(position);
                        handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                    } else {
                        usePositions.add(position);
                        Rect rect = madeRect(bitmap, row, col, scale, imageScale);
                        drawDatas.add(new DrawData(bitmap, null, rect));
                    }
                }
            }
            // 上 #########
            for (int row = cacheStartRow; row < startRow; row++) {
                for (int col = cacheStartCol; col <= cacheEndCol; col++) {
                    Position position = new Position(row, col);
                    usePositions.add(position);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            // 下 #########
            for (int row = endRow + 1; row < cacheEndRow; row++) {
                for (int col = cacheStartCol; col <= cacheEndCol; col++) {
                    Position position = new Position(row, col);
                    usePositions.add(position);
                    Log.d("9999", "下 " + position);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            // # 左
            // #
            for (int row = startRow; row < endRow; row++) {
                for (int col = cacheStartCol; col < startCol; col++) {
                    Position position = new Position(row, col);
                    usePositions.add(position);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            // # 右
            // #
            for (int row = startRow; row < endRow; row++) {
                for (int col = endRow + 1; col < cacheEndRow; col++) {
                    Position position = new Position(row, col);
                    usePositions.add(position);
                    handler.sendMessage(handler.obtainMessage(what, new MessageData(position, scale)));
                }
            }
            loadData.mCurrentCacheData.images.keySet().retainAll(usePositions);
        }

        Log.d("countTime", "currentScale time :" + (SystemClock.uptimeMillis() - startTime));
        startTime = SystemClock.uptimeMillis();

        //
        if (!needShowPositions.isEmpty()) {
            Collections.sort(cacheDatas, new NearComparator(scale));
            Iterator<CacheData> iterator = cacheDatas.iterator();
            while (iterator.hasNext()) {
                CacheData cacheData = iterator.next();
                int scaleKey = cacheData.scale;
                if (scaleKey / scale == 2) {// 这里都是大于scale的.,缩小的图，单位区域大

                    Log.d("countTime", ",缩小的图 time :" + (SystemClock.uptimeMillis() - startTime));
                    startTime = SystemClock.uptimeMillis();

                    int ds = scaleKey / scale;

                    // 单位图片的真实区域
                    int size = scale * BASE_BLOCKSIZE;

                    // 显示区域范围
                    int startRowKey = cacheStartRow / 2;
                    int endRowKey = cacheEndRow / 2;
                    int startColKey = cacheStartCol / 2;
                    int endColKey = cacheEndCol / 2;

                    Iterator<Map.Entry<Position, Bitmap>> imageiterator = cacheData.images.entrySet().iterator();
                    while (imageiterator.hasNext()) {
                        Map.Entry<Position, Bitmap> entry = imageiterator.next();
                        Position position = entry.getKey();
                        if (!(startRowKey <= position.row && position.row <= endRowKey && startColKey <= position.col && position.col <= endColKey)) {
                            imageiterator.remove();
                        }
                    }

                    Iterator<Map.Entry<Position, Bitmap>> imagesIterator = cacheData.images.entrySet().iterator();
                    while (imagesIterator.hasNext()) {
                        Map.Entry<Position, Bitmap> entry = imagesIterator.next();
                        Position position = entry.getKey();
                        int startPositionRow = position.row * ds;
                        int endPositionRow = startPositionRow + ds;
                        int startPositionCol = position.col * ds;
                        int endPositionCol = startPositionCol + ds;
                        Bitmap bitmap = entry.getValue();
                        int iW = bitmap.getWidth();
                        int iH = bitmap.getHeight();

                        // 单位图片的大小
                        int blockImageSize = BASE_BLOCKSIZE / ds;

                        Log.d("nnnn", " bitmap.getWidth():" + bitmap.getWidth() + " imageHeight:" + iH);
                        for (int row = startPositionRow, i = 0; row <= endPositionRow; row++, i++) {
                            int top = i * blockImageSize;
                            if (top >= iH) {
                                break;
                            }
                            for (int col = startPositionCol, j = 0; col <= endPositionCol; col++, j++) {
                                int left = j * blockImageSize;
                                if (left >= iW) {
                                    break;
                                }
                                if (needShowPositions.remove(new Position(row, col))) {
                                    int right = left + blockImageSize;
                                    int bottom = top + blockImageSize;
                                    if (right > iW) {
                                        right = iW;
                                    }
                                    if (bottom > iH) {
                                        bottom = iH;
                                    }
                                    Rect rect = new Rect();
                                    rect.left = col * size;
                                    rect.top = row * size;
                                    rect.right = rect.left + (right - left) * scaleKey;
                                    rect.bottom = rect.top + (bottom - top) * scaleKey;
                                    drawDatas.add(new DrawData(bitmap, new Rect(left, top, right, bottom), rect));
                                }
                            }
                        }
                    }

                    Log.d("countTime", ",缩小的图 time :" + (SystemClock.uptimeMillis() - startTime));
                } else if (scale / scaleKey == 2) {// 放大的图，单位区域小
                    int size = scaleKey * BASE_BLOCKSIZE;

                    Log.d("countTime", " 放大的图  time :" + (SystemClock.uptimeMillis() - startTime));
                    startTime = SystemClock.uptimeMillis();

                    int startRowKey = imageRect.top / size + (imageRect.top % size == 0 ? 0 : 1) - 1;
                    int endRowKey = imageRect.bottom / size + (imageRect.bottom % size == 0 ? 0 : 1);
                    int startColKey = imageRect.left / size + (imageRect.left % size == 0 ? 0 : 1) - 1;
                    int endColKey = imageRect.right / size + (imageRect.right % size == 0 ? 0 : 1);

                    Log.d("nnnn", "startRowKey" + startRowKey + " endRowKey:+" + endRowKey + " endColKey:" + endColKey);

                    Position tempPosition = new Position();
                    Iterator<Map.Entry<Position, Bitmap>> imageiterator = cacheData.images.entrySet().iterator();
                    while (imageiterator.hasNext()) {
                        Map.Entry<Position, Bitmap> entry = imageiterator.next();
                        Position position = entry.getKey();
                        if (!(startRowKey <= position.row && position.row <= endRowKey && startColKey <= position.col && position.col <= endColKey)) {
                            imageiterator.remove();
                            Log.d("nnnn", "position:" + position + " remove");
                        } else {
                            Bitmap bitmap = entry.getValue();
                            tempPosition.set(position.row / 2 + (position.row % 2 == 0 ? 0 : 1), position.col / 2 + (position.col % 2 == 0 ? 0 : 1));
                            if (needShowPositions.contains(tempPosition)) {
                                Rect rect = new Rect();
                                rect.left = position.col * size;
                                rect.top = position.row * size;
                                rect.right = rect.left + bitmap.getWidth() * scaleKey;
                                rect.bottom = rect.top + bitmap.getHeight() * scaleKey;
                                drawDatas.add(new DrawData(bitmap, null, rect));
                            }
                        }
                    }

                    Log.d("countTime", " 放大的图  time :" + (SystemClock.uptimeMillis() - startTime));
                    startTime = SystemClock.uptimeMillis();
                } else {
                    iterator.remove();
                }
            }
        }
        return drawDatas;
    }

    public int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    private class NearComparator implements Comparator<CacheData> {
        private int scale;

        public NearComparator(int scale) {
            super();
            this.scale = scale;
        }

        @Override
        public int compare(CacheData lhs, CacheData rhs) {
            int dScale = Math.abs(scale - lhs.scale) - Math.abs(scale - rhs.scale);
            if (dScale == 0) {
                if (lhs.scale > rhs.scale) {
                    return -1;
                } else {
                    return 1;
                }
            } else if (dScale < 0) {
                return -1;
            } else {
                return 0;
            }
        }
    }
    public static int getBitmapSize(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // API 19
            return bitmap.getAllocationByteCount();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {// API
            return bitmap.getByteCount();
        }
        return bitmap.getRowBytes() * bitmap.getHeight(); // earlier version
    }
    private class MessageData {
        Position position;
        int scale;

        public MessageData(Position position, int scale) {
            super();
            this.position = position;
            this.scale = scale;
        }

    }
    private int preMessageWhat = 1;
    private class CacheData {
        int scale;
        Map<Position, Bitmap> images;

        public CacheData(int scale, Map<Position, Bitmap> images) {
            super();
            this.scale = scale;
            this.images = images;
        }
    }
    public class DrawData {
        Bitmap bitmap;
        Rect srcRect;
        Rect imageRect;

        public DrawData(Bitmap bitmap, Rect srcRect, Rect imageRect) {
            super();
            this.bitmap = bitmap;
            this.srcRect = srcRect;
            this.imageRect = imageRect;
        }

    }

    private class Position {
        int row;
        int col;

        public Position() {
            super();
        }

        public Position(int row, int col) {
            super();
            this.row = row;
            this.col = col;
        }

        public Position set(int row, int col) {
            this.row = row;
            this.col = col;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Position) {
                Position position = (Position) o;
                return row == position.row && col == position.col;
            }
            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder().append(col).append(row);
            return builder.toHashCode();
        }

        @Override
        public String toString() {
            return "row:" + row + " col:" + col;
        }
    }
    private OnImageLoadListenner onImageLoadListenner;
    public interface OnImageLoadListenner {

        public void onBlockImageLoadFinished();

        public void onImageLoadFinished(int imageWidth, int imageHeight);
    }

    public void destroy() {
        handlerThread.quit();
        handlerThread = null;
        handler = null;
        release(this.mLoadData);
    }
    public int getWidth() {
        return mLoadData == null ? 0 : mLoadData.mImageWidth;
    }

    public int getHeight() {
        return mLoadData == null ? 0 : mLoadData.mImageHeight;
    }
    public void load(InputStream inputStream) {
        load(new InputStreamBitmapRegionDecoderFactory(inputStream));
    }
    public void load(String filePath) {
        load(new PathBitmapRegionDecoderFactory(filePath));
    }


    private interface BitmapRegionDecoderFactory {
        public BitmapRegionDecoder made() throws IOException;
    }
    private class InputStreamBitmapRegionDecoderFactory implements BitmapRegionDecoderFactory {
        private InputStream inputStream;

        public InputStreamBitmapRegionDecoderFactory(InputStream inputStream) {
            super();
            this.inputStream = inputStream;
        }

        @Override
        public BitmapRegionDecoder made() throws IOException {
            return BitmapRegionDecoder.newInstance(inputStream, false);
        }

    }
    private void load(BitmapRegionDecoderFactory factory) {
        release(this.mLoadData);
        this.mLoadData = new LoadData(factory);
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler.sendEmptyMessage(MESSAGE_LOAD);
        }
    }

    private void release(LoadData loadData) {
        if (loadData == null) {
            return;
        }
        if (loadData.mDecoder != null) {
            try {
                loadData.mDecoder.recycle();
            } catch (Exception e) {
                e.printStackTrace();
            }
            loadData.mDecoder = null;
        }
    }
    private class PathBitmapRegionDecoderFactory implements BitmapRegionDecoderFactory {
        private String path;

        public PathBitmapRegionDecoderFactory(String filePath) {
            super();
            this.path = filePath;
        }

        @Override
        public BitmapRegionDecoder made() throws IOException {
            return BitmapRegionDecoder.newInstance(path, false);
        }

    }



    public static final int MESSAGE_PIC = 665;
    public static final int MESSAGE_LOAD = 666;
    public static final int MESSAGE_BLOCK_1 = 1;
    public static final int MESSAGE_BLOCK_2 = 2;
    private class LoadData {
        public LoadData(BitmapRegionDecoderFactory factory) {
            this.mFactory = factory;
        }

        private volatile CacheData mCurrentCacheData;
        /** 保存显示区域中的各个缩放的图片切换 */
        private List<CacheData> mCacheDatas = new LinkedList<CacheData>();

        /** 保存预先显示的整张图 */
        private volatile Bitmap mCacheImageData;
        /** 保存整张图图片缩放级别 */
        private volatile int mCacheImageScale;
        private volatile int mImageHeight;
        private volatile int mImageWidth;
        private volatile BitmapRegionDecoderFactory mFactory;
        private volatile BitmapRegionDecoder mDecoder;
    }
    private class LoadHandler extends Handler{
        public LoadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            LoadData loadData = mLoadData;
            if (msg.what == MESSAGE_LOAD) {
                if (loadData.mFactory != null) {
                    try {
                        loadData.mDecoder = loadData.mFactory.made();
                        loadData.mImageWidth = loadData.mDecoder.getWidth();
                        loadData.mImageHeight = loadData.mDecoder.getHeight();
                        if (onImageLoadListenner != null) {
                            onImageLoadListenner.onImageLoadFinished(loadData.mImageWidth, loadData.mImageHeight);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else if (msg.what == MESSAGE_PIC) {
                Integer scale = (Integer) msg.obj;
                BitmapFactory.Options decodingOptions = new BitmapFactory.Options();
                decodingOptions.inSampleSize = scale;
                try {
                    loadData.mCacheImageData = loadData.mDecoder.decodeRegion(new Rect(0, 0, loadData.mImageWidth, loadData.mImageHeight),
                            decodingOptions);
                    loadData.mCacheImageScale = scale;
                    Log.d("vvvv", " cacheImageData: " + getBitmapSize(loadData.mCacheImageData));
                    if (onImageLoadListenner != null) {
                        onImageLoadListenner.onBlockImageLoadFinished();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                MessageData data = (MessageData) msg.obj;
                CacheData cacheData = loadData.mCurrentCacheData;
                if (cacheData == null || cacheData.scale != data.scale) {
                    return;
                }
                Position position = data.position;
                Bitmap imageData = cacheData.images.get(position);
                // 不存在才需要加载，（这里避免之前的任务重复被执行）
                if (imageData == null) {
                    int imageBlockSize = BASE_BLOCKSIZE * data.scale;
                    int left = imageBlockSize * position.col;
                    int right = left + imageBlockSize;
                    int top = imageBlockSize * position.row;
                    int bottom = top + imageBlockSize;
                    if (right > loadData.mImageWidth) {
                        right = loadData.mImageWidth;
                    }
                    if (bottom > loadData.mImageHeight) {
                        bottom = loadData.mImageHeight;
                    }
                    Rect clipImageRect = new Rect(left, top, right, bottom);
                    BitmapFactory.Options decodingOptions = new BitmapFactory.Options();
                    decodingOptions.inSampleSize = data.scale;
                    // 加载clipRect的区域的图片块
                    try {
                        imageData = loadData.mDecoder.decodeRegion(clipImageRect, decodingOptions);
                        if (imageData != null) {
                            cacheData.images.put(position, imageData);
                            if (onImageLoadListenner != null) {
                                onImageLoadListenner.onBlockImageLoadFinished();
                            }
                        }
                    } catch (Exception e) {
                        Log.d("nnnn", position.toString() + " " + clipImageRect.toShortString());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class HashCodeBuilder {
        public HashCodeBuilder() {

        }
        private int result = 17;

        public HashCodeBuilder append(boolean field) {
            result = 37 * result + (field ? 1 : 0);
            return this;
        }

        public HashCodeBuilder append(byte field) {
            result = 37 * result + (int) field;
            return this;
        }

        public HashCodeBuilder append(char field) {
            result = 37 * result + (int) field;
            return this;
        }

        public HashCodeBuilder append(short field) {
            result = 37 * result + (int) field;
            return this;
        }

        public HashCodeBuilder append(int field) {
            result = 37 * result + field;
            return this;
        }

        public HashCodeBuilder append(long field) {
            result = 37 * result + (int) (field ^ (field >>> 32));
            return this;
        }

        public HashCodeBuilder append(float field) {
            result = 37 * result + Float.floatToIntBits(field);
            return this;
        }

        public HashCodeBuilder append(double field) {
            append(Double.doubleToLongBits(field));
            return this;
        }

        public HashCodeBuilder append(Object field) {
            if(field == null)
                result = 0;
            else if (field.getClass().isArray()) {
                for (int i = Array.getLength(field) - 1; i >= 0; i--) {
                    append(Array.get(field, i));
                }
            } else
                append(field.hashCode());
            return this;
        }

        public int toHashCode() {
            return result;
        }

        @Override
        public int hashCode() {
            return result;
        }

        @Override
        public String toString() {
            return String.valueOf(result);
        }
    }
}
