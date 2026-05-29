// Minimal browser-side SSE reader for the Goals chat stream.
//
// Spring's SseEmitter writes frames as `event:NAME\ndata:PAYLOAD\n\n`
// (a frame may carry multi-line `data:` lines, joined by "\n"). We read
// the ReadableStream, split on the blank-line frame boundary, and invoke
// `onEvent(name, data)` per frame. The browser's native EventSource only
// supports GET, so we parse the POST fetch body by hand.

export type SseHandler = (eventName: string, data: string) => void;

export async function readSseStream(
  body: ReadableStream<Uint8Array>,
  onEvent: SseHandler,
  signal?: AbortSignal,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  const flushFrame = (frame: string) => {
    let eventName = "message";
    const dataLines: string[] = [];
    for (const rawLine of frame.split("\n")) {
      const line = rawLine.replace(/\r$/, "");
      if (line.startsWith("event:")) {
        eventName = line.slice("event:".length).trim();
      } else if (line.startsWith("data:")) {
        // Per the SSE spec a leading single space after the colon is stripped.
        dataLines.push(line.slice("data:".length).replace(/^ /, ""));
      }
      // `id:` / `:comment` lines are ignored.
    }
    if (dataLines.length > 0 || eventName !== "message") {
      onEvent(eventName, dataLines.join("\n"));
    }
  };

  try {
    for (;;) {
      if (signal?.aborted) break;
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let boundary = buffer.indexOf("\n\n");
      while (boundary !== -1) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        if (frame.trim().length > 0) flushFrame(frame);
        boundary = buffer.indexOf("\n\n");
      }
    }
    // Flush a trailing frame with no terminating blank line.
    if (buffer.trim().length > 0) flushFrame(buffer);
  } finally {
    reader.releaseLock();
  }
}
