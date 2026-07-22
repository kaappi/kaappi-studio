const TIMEOUT_MS = 10000;

export function createRunner() {
  const workerUrl = new URL("./worker.js", import.meta.url);
  const wasmUrl = new URL("./kaappi.wasm", import.meta.url).href;
  let worker = new Worker(workerUrl, { type: "module" });

  function run(code) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        worker.terminate();
        worker = new Worker(workerUrl, { type: "module" });
        reject(new Error(`Timed out (${TIMEOUT_MS / 1000}s limit)`));
      }, TIMEOUT_MS);

      worker.onmessage = ({ data }) => {
        clearTimeout(timer);
        resolve(data);
      };
      worker.onerror = (e) => {
        clearTimeout(timer);
        reject(new Error(e.message || "Worker error"));
      };
      worker.postMessage({ code, wasmUrl });
    });
  }

  return {
    run,
    terminate: () => worker.terminate(),
  };
}
