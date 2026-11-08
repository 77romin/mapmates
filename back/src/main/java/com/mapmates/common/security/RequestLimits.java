package com.mapmates.common.security;
import com.mapmates.common.exception.ApiException;import java.util.concurrent.*;
/** Bounded, process-local fixed windows. Independent account and network buckets. */
public final class RequestLimits {
 private record Window(long start,int used){}
 private static final ConcurrentHashMap<String,Window> buckets=new ConcurrentHashMap<>();
 public static synchronized void check(String key,int limit,long millis){long now=System.currentTimeMillis();if(buckets.size()>9000)buckets.entrySet().removeIf(e->now-e.getValue().start()>3600000);if(buckets.size()>=10000&&!buckets.containsKey(key))throw new ApiException(429,"RATE_LIMITED","잠시 후 다시 시도해주세요.");var w=buckets.get(key);if(w==null||now-w.start()>=millis){buckets.put(key,new Window(now,1));return;}if(w.used()>=limit)throw new ApiException(429,"RATE_LIMITED","요청이 많아요. 잠시 후 다시 시도해주세요.");buckets.put(key,new Window(w.start(),w.used()+1));}
}
