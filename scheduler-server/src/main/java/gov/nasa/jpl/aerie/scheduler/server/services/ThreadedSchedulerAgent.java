package gov.nasa.jpl.aerie.scheduler.server.services;

import gov.nasa.jpl.aerie.scheduler.server.ResultsProtocol;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class ThreadedSchedulerAgent implements SchedulerAgent {

  private sealed interface SchedulingRequest {
    record Schedule(ScheduleRequest request, ResultsProtocol.WriterRole writer) implements SchedulingRequest {}

    record Terminate() implements SchedulingRequest {}
  }

  private final BlockingQueue<SchedulingRequest> requestQueue;

  private ThreadedSchedulerAgent(final BlockingQueue<SchedulingRequest> requestQueue) {
    this.requestQueue = Objects.requireNonNull(requestQueue);
  }

  @Override
  public void schedule(final ScheduleRequest request, final ResultsProtocol.WriterRole writer)
  throws InterruptedException
  {
    this.requestQueue.put(new SchedulingRequest.Schedule(request, writer));
  }

  public void terminate() throws InterruptedException {
    this.requestQueue.put(new SchedulingRequest.Terminate());
  }

  public static ThreadedSchedulerAgent spawn(final String threadName, final SchedulerAgent schedulerAgent) {
    final var requestQueue = new LinkedBlockingQueue<SchedulingRequest>();

    final var thread = new Thread(new Worker(requestQueue, schedulerAgent));
    thread.setName(threadName);
    thread.start();

    return new ThreadedSchedulerAgent(requestQueue);
  }

  private static final class Worker implements Runnable {
    private final BlockingQueue<SchedulingRequest> requestQueue;
    private final SchedulerAgent schedulerAgent;

    public Worker(
        final BlockingQueue<SchedulingRequest> requestQueue,
        final SchedulerAgent schedulerAgent)
    {
      this.requestQueue = Objects.requireNonNull(requestQueue);
      this.schedulerAgent = Objects.requireNonNull(schedulerAgent);
    }

    @Override
    public void run() {
      while (true) {
        try {
          final var request = this.requestQueue.take();

          if (request instanceof SchedulingRequest.Schedule req) {
            try {
              this.schedulerAgent.schedule(req.request(), req.writer());
            } catch (final Throwable ex) {
              ex.printStackTrace(System.err);
              req.writer().failWith(b -> b
                  .type("UNEXPECTED_SCHEDULER_EXCEPTION")
                  .message("Something went wrong while scheduling")
                  .trace(ex));
            }
            // continue
          } else if (request instanceof SchedulingRequest.Terminate) {
            break;
          } else {
            throw new UnexpectedSubtypeError(SchedulingRequest.class, request);
          }
        } catch (final Exception ex) {
          ex.printStackTrace(System.err);
          // continue
        }
      }
    }
  }
}
