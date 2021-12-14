package io.gravitee.policy.trafficshadowing;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.gateway.api.stream.WriteStream;

/**
 * A WriteStream handling back pressure.
 *
 * @author GraviteeSource Team
 */
public class PressureSafeWriteStream extends SimpleReadWriteStream<Buffer> {

    private WriteStream<Buffer> writeStream;

    public PressureSafeWriteStream(WriteStream<Buffer> writeStream) {
        this.writeStream = writeStream;
    }

    @Override
    public SimpleReadWriteStream<Buffer> write(Buffer chunk) {
        writeStream.write(chunk);

        if (writeStream.writeQueueFull()) {
            pause();
            writeStream.drainHandler(aVoid -> resume());
        }
        return super.write(chunk);
    }

    @Override
    public void end() {
        writeStream.end();
        super.end();
    }
}
