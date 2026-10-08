package com.yu.transferrag.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ImportTaskDispatcherTest {
 @Test void fullQueueLeavesTaskForNextScan() throws Exception {
  var state=mock(ImportTaskStateService.class); var worker=mock(EvidenceImportWorker.class);
  var executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.AbortPolicy());
  var started=new CountDownLatch(1); var release=new CountDownLatch(1); var firstTwo=new CountDownLatch(2);
  doAnswer(i->{started.countDown(); release.await(5,TimeUnit.SECONDS); firstTwo.countDown();return null;}).when(worker).run(1L);
  doAnswer(i->{firstTwo.countDown();return null;}).when(worker).run(2L);
  when(worker.runningIds()).thenReturn(Set.of());
  when(state.readyIds(32)).thenReturn(List.of(1L,2L,3L),List.of(3L));
  var dispatcher=new ImportTaskDispatcher(state,worker,executor);
  try {
   dispatcher.scan(); assertTrue(started.await(5,TimeUnit.SECONDS));
   verify(worker,never()).run(3L); verify(state,never()).claim(anyLong(),anyString());
   release.countDown(); assertTrue(firstTwo.await(5,TimeUnit.SECONDS));
   dispatcher.scan(); executor.shutdown(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
   verify(worker).run(3L);
  } finally { release.countDown(); executor.shutdownNow(); }
 }
}
