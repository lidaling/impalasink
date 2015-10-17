package cn.lidl;

import com.google.common.base.Throwables;
import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SinkCounter;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class LocalFileLogSink extends AbstractSink implements Configurable {
    private static final Logger logger = LoggerFactory
            .getLogger(LocalFileLogSink.class);
    private static final String PROP_KEY_ROOTPATH = "rootPath";
    private String rootPath;
    private int batchSize = 10;
    private SinkCounter sinkCounter;



    @Override
    public void configure(Context context) {
        String rootPath = context.getString(PROP_KEY_ROOTPATH);
        if (sinkCounter == null) {
            sinkCounter = new SinkCounter(getName());
        }
    }

    @Override
    public synchronized void start() {
        logger.info("Starting localFileLog sink");
        sinkCounter.start();
        try {
            /**
             * do connection or file handle init
             */
            sinkCounter.incrementConnectionCreatedCount();
        } catch (Exception e) {
            sinkCounter.incrementConnectionFailedCount();
            sinkCounter.incrementConnectionClosedCount();
        }
        super.start();
        logger.info("localFileLog sink started");
    }

    @Override
    public synchronized void stop() {
        logger.info("Stopping localFileLog sink");
        sinkCounter.incrementConnectionClosedCount();
        sinkCounter.stop();
        super.stop();
        logger.info("localFileLog sink stopped");
    }


    @Override
    public Status process() throws EventDeliveryException {
        Status status = Status.READY;
        logger.debug("Do process");
        Channel channel= getChannel();
        Transaction transaction = channel.getTransaction();
        List<String> stringList=new ArrayList<String>();
        try {
            transaction.begin();
            long count;
            for (count = 0; count < batchSize; ++count) {
                Event event = channel.take();
                if (event == null) {
                    status=Status.BACKOFF;//or it does not has to do
                    break;
                }
                String eventStr = new String(event.getBody(), StandardCharsets.UTF_8);
                stringList.add(eventStr);
                logger.info("got message:"+eventStr);
            }
            if (count <= 0) {
                sinkCounter.incrementBatchEmptyCount();
                status = Status.BACKOFF;
            } else {
                if (count < batchSize) {
                    sinkCounter.incrementBatchUnderflowCount();
                    status = Status.BACKOFF;
                } else {
                    sinkCounter.incrementBatchCompleteCount();
                }
                sinkCounter.addToEventDrainAttemptCount(count);
                dologgot(stringList);
            }
            transaction.commit();
            sinkCounter.addToEventDrainSuccessCount(count);
        } catch (Throwable t) {
            try {
                transaction.rollback();
            } catch (Exception e) {
                logger.error("Exception during transaction rollback.", e);
            }

            logger.error("Failed to commit transaction. Transaction rolled back.", t);
            if (t instanceof Error || t instanceof RuntimeException) {
                Throwables.propagate(t);
            } else {
                throw new EventDeliveryException(
                        "Failed to commit transaction. Transaction rolled back.", t);
            }
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
        return status;
    }
    private void dologgot(List<String> list){
        for (String str:list){
            logger.info("dologgot:"+str);
        }
    }
}
