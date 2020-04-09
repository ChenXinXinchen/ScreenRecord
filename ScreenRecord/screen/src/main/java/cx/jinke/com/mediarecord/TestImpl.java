package cx.jinke.com.mediarecord;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.N)
public class TestImpl {
//    使用Executors方式创建
    ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    ExecutorService fixedThreadPool = Executors.newFixedThreadPool(2);
    ScheduledExecutorService singleThreadScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(2);
    ExecutorService workStealingPool = Executors.newWorkStealingPool();
    // 原始创建方式
    ThreadPoolExecutor tp = new ThreadPoolExecutor(10,
            10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
}
