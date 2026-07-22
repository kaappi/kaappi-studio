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

    const stdoutLines = [];
    const stderrLines = [];

    const fds = [
      new OpenFile(new File([])),
      ConsoleStdout.lineBuffered(line => { stdoutLines.push(line); }),
      ConsoleStdout.lineBuffered(line => { stderrLines.push(line); }),
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
        stderrLines.push(e.message ?? String(e));
      } else if (e.code !== 0) {
        stderrLines.push(e.message ?? String(e));
      }
    }
    const elapsed = performance.now() - t0;

    const stdout = stdoutLines.join("\n") + (stdoutLines.length ? "\n" : "");
    const stderr = stderrLines.join("\n") + (stderrLines.length ? "\n" : "");

    self.postMessage({ stdout, stderr, elapsed });
  } catch (e) {
    self.postMessage({ stdout: "", stderr: String(e), elapsed: 0 });
  }
};
