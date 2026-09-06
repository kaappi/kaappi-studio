let wasmModule = null;
let wasiShim = null;

self.onmessage = async ({ data: { code, wasmUrl } }) => {
  try {
    if (!wasiShim) {
      wasiShim = await import("./wasi-shim-bundle.mjs");
    }
    const { WASI, File, OpenFile, ConsoleStdout, PreopenDirectory } = wasiShim;

    if (!wasmModule) {
      const response = await fetch(wasmUrl);
      if (!response.ok) throw new Error(`Failed to fetch WASM: ${response.status}`);
      wasmModule = await WebAssembly.compile(await response.arrayBuffer());
    }

    // Raw write sinks (no line splitting): lineBuffered never flushes the
    // trailing partial line, so output without a final newline would be lost.
    const stdoutDecoder = new TextDecoder("utf-8", { fatal: false });
    const stderrDecoder = new TextDecoder("utf-8", { fatal: false });
    let stdoutText = "";
    let stderrText = "";

    const fds = [
      new OpenFile(new File([])),
      new ConsoleStdout(bytes => { stdoutText += stdoutDecoder.decode(bytes, { stream: true }); }),
      new ConsoleStdout(bytes => { stderrText += stderrDecoder.decode(bytes, { stream: true }); }),
      new PreopenDirectory(".", [
        ["program.scm", new File(new TextEncoder().encode(code))],
      ]),
    ];

    const wasi = new WASI(["kaappi", "program.scm"], [], fds);
    const instance = await WebAssembly.instantiate(wasmModule, {
      wasi_snapshot_preview1: wasi.wasiImport,
    });

    const t0 = performance.now();
    try {
      wasi.start(instance);
    } catch (e) {
      if (e instanceof WebAssembly.RuntimeError) {
        stderrText += (e.message ?? String(e)) + "\n";
      } else if (e.code !== 0) {
        stderrText += (e.message ?? String(e)) + "\n";
      }
    }
    const elapsed = performance.now() - t0;
    stdoutText += stdoutDecoder.decode();
    stderrText += stderrDecoder.decode();

    self.postMessage({ stdout: stdoutText, stderr: stderrText, elapsed });
  } catch (e) {
    self.postMessage({ stdout: "", stderr: String(e), elapsed: 0 });
  }
};
