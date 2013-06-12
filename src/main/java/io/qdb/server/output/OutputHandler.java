package io.qdb.server.output;

import io.qdb.server.model.Output;
import io.qdb.server.model.Queue;

import java.io.Closeable;

/**
 * Processes messages from a queue. Public fields are automatically populated with parameters from the output
 * before message processing starts.
 */
public interface OutputHandler extends Closeable {

    /**
     * This is called once before the first call to {@link #processMessage(long, String, long, byte[])}. Note that
     * the output instance will become stale i.e. once processing has started it will no longer reflect the current
     * state of the output. Throw IllegalArgumentException for permanent errors (e.g. missing or invalid parameters)
     * which will cause the output to stop until it is updated. Throwing other exceptions will cause the output
     * to be retried after a delay defined by its backoff policy.
     */
    public void init(Queue q, Output output, String outputPath) throws Exception;

    /**
     * Process the message and return the id of the message that processing should start after. This will usually
     * be the id of the message just processed, unless messages are being processed asynchronously.
     */
    public long processMessage(long messageId, String routingKey, long timestamp, byte[] payload) throws Exception;

    /**
     * This is called when processing progress is being recorded with the new output instance. Handlers might want
     * to update fields of the output at this time. In particular the timestamp field will be set to
     * the timestamp of the last message processed and this may not be the same as the last completed message if
     * messages are being processed asynchronously. This method must be fast.
     */
    public void updateOutput(Output output);

}