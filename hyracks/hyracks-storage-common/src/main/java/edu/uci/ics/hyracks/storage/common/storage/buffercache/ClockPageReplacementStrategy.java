package edu.uci.ics.hyracks.storage.common.storage.buffercache;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ClockPageReplacementStrategy implements IPageReplacementStrategy {
    private final Lock lock;
    private IBufferCacheInternal bufferCache;
    private int clockPtr;

    public ClockPageReplacementStrategy() {
        this.lock = new ReentrantLock();
        clockPtr = 0;
    }

    @Override
    public Object createPerPageStrategyObject(int cpid) {
        return new AtomicBoolean();
    }

    @Override
    public void setBufferCache(IBufferCacheInternal bufferCache) {
        this.bufferCache = bufferCache;
    }

    @Override
    public void notifyCachePageReset(ICachedPageInternal cPage) {
        getPerPageObject(cPage).set(false);
    }

    @Override
    public void notifyCachePageAccess(ICachedPageInternal cPage) {
        getPerPageObject(cPage).set(true);
    }

    @Override
    public ICachedPageInternal findVictim() {
        lock.lock();
        try {
            int startClockPtr = clockPtr;
            do {
                ICachedPageInternal cPage = bufferCache.getPage(clockPtr);

                /*
                 * We do two things here: 1. If the page has been accessed, then we skip it -- The CAS would return false if the current value is false which makes the page a possible candidate for replacement. 2. We check with the buffer manager if it feels its a good idea to use this page as a victim.
                 */
                AtomicBoolean accessedFlag = getPerPageObject(cPage);
                if (!accessedFlag.compareAndSet(true, false)) {
                    if (cPage.pinIfGoodVictim()) {
                        return cPage;
                    }
                }
                clockPtr = (clockPtr + 1) % bufferCache.getNumPages();
            } while (clockPtr != startClockPtr);
        } finally {
            lock.unlock();
        }
        return null;
    }

    private AtomicBoolean getPerPageObject(ICachedPageInternal cPage) {
        return (AtomicBoolean) cPage.getReplacementStrategyObject();
    }
}