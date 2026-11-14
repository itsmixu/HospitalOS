class PcmCapture extends AudioWorkletProcessor {
  constructor() {
    super();
    this.sum = 0;
    this.count = 0;
    this.position = 0;
    this.samples = [];
  }
  process(inputs) {
    const channel = inputs[0]?.[0];
    if (!channel) return true;
    for (const sample of channel) {
      this.sum += sample;
      this.count++;
      this.position += 16000;
      if (this.position >= sampleRate) {
        this.position -= sampleRate;
        this.samples.push(this.sum / this.count);
        this.sum = 0;
        this.count = 0;
      }
      if (this.samples.length === 1600) {
        const frame = new Float32Array(this.samples);
        this.port.postMessage(frame, [frame.buffer]);
        this.samples = [];
      }
    }
    return true;
  }
}
registerProcessor("pcm-capture", PcmCapture);
