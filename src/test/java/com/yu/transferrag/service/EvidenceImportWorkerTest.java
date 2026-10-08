package com.yu.transferrag.service;
import com.yu.transferrag.entity.ImportTaskStage;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class EvidenceImportWorkerTest {
 @Test void heartbeatFailureDoesNotForgetAnActuallyRunningWorker() throws Exception {
  var state=mock(ImportTaskStateService.class);var processor=mock(EvidenceImportProcessor.class);
  var lease=new ImportTaskStateService.Lease(1,2,"owner",1,ImportTaskStage.INDEXING);
  when(state.claim(eq(1L),anyString())).thenReturn(Optional.of(lease));
  when(state.heartbeat(lease)).thenReturn(false);
  doThrow(new ImportTaskStateService.LostLease()).when(state).fail(any(),anyString(),anyBoolean());
  var entered=new java.util.concurrent.CountDownLatch(1);
  var interrupted=new java.util.concurrent.CountDownLatch(1);
  var release=new java.util.concurrent.CountDownLatch(1);
  when(processor.process(lease)).thenAnswer(call->{
   entered.countDown();boolean cancelled=false;
   while(release.getCount()!=0) {
    try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("test release timed out"); }
    catch(InterruptedException signal) {cancelled=true;interrupted.countDown();}
   }
   if(cancelled) Thread.currentThread().interrupt();
   return new EvidenceImportProcessor.Result(1,1);
  });
  var worker=new EvidenceImportWorker(state,processor,60);
  Thread thread=new Thread(()->worker.run(1));
  try {
   thread.start();
   org.junit.jupiter.api.Assertions.assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   worker.heartbeat();
   org.junit.jupiter.api.Assertions.assertTrue(interrupted.await(5,java.util.concurrent.TimeUnit.SECONDS));
   org.junit.jupiter.api.Assertions.assertTrue(worker.runningIds().contains(1L));
  } finally {release.countDown();thread.join(5000);}
  org.junit.jupiter.api.Assertions.assertFalse(thread.isAlive());
  org.junit.jupiter.api.Assertions.assertTrue(worker.runningIds().isEmpty());
  verify(state,never()).succeed(any(),anyInt(),anyInt());
 }
 @Test void failureIsSanitizedAndNeverSuccess() {
  var state=mock(ImportTaskStateService.class);var processor=mock(EvidenceImportProcessor.class);
  var lease=new ImportTaskStateService.Lease(1,2,"owner",1,ImportTaskStage.PARSING);
  when(state.claim(eq(1L),anyString())).thenReturn(Optional.of(lease));
  when(processor.process(lease)).thenThrow(new EvidenceImportProcessor.StageFailure("PARSING_FAILED",true));
  new EvidenceImportWorker(state,processor,60).run(1);
  verify(state).fail(lease,"PARSING_FAILED",true);verify(state,never()).succeed(any(),anyInt(),anyInt());
 }
 @Test void staleCompletionNeverOverwritesNewAttempt() {
  var state=mock(ImportTaskStateService.class);var processor=mock(EvidenceImportProcessor.class);
  var lease=new ImportTaskStateService.Lease(1,2,"owner",1,ImportTaskStage.INDEXING);
  when(state.claim(eq(1L),anyString())).thenReturn(Optional.of(lease));
  when(processor.process(lease)).thenReturn(new EvidenceImportProcessor.Result(1,1));
  doThrow(new ImportTaskStateService.LostLease()).when(state).succeed(lease,1,1);
  new EvidenceImportWorker(state,processor,60).run(1);
  verify(state,never()).fail(any(),anyString(),anyBoolean());
 }
}
