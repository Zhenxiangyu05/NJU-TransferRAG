const STATES = new Set(['QUEUED', 'PROCESSING', 'SUCCEEDED', 'FAILED'])
export function validateTask(task) {
  if (!task || !Number.isSafeInteger(task.taskId) || task.taskId <= 0
    || task.documentId == null || !STATES.has(task.status)) throw new Error('Invalid import task response')
  return task
}
// Poll only the existing protected admin proxy. No public task endpoint, storage, or automatic resubmission.
export async function pollImportTask(initial, { fetcher = fetch, signal, onUpdate = () => {},
  intervalMs = 1500, maxPolls = 1200, sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms)) } = {}) {
  let task = validateTask(initial)
  const taskId = task.taskId
  const documentId = task.documentId
  onUpdate(task)
  for (let n = 0; n < maxPolls; n++) {
    if (['SUCCEEDED', 'FAILED'].includes(task.status)) return task
    if (signal?.aborted) throw new Error('Polling cancelled')
    await sleep(intervalMs)
    if (signal?.aborted) throw new Error('Polling cancelled')
    const response = await fetcher(`api/import-tasks/${taskId}`, { signal })
    if (!response.ok) throw new Error('Import task polling unavailable')
    task = validateTask(await response.json())
    if (task.taskId !== taskId || task.documentId !== documentId) throw new Error('Import task identity changed')
    onUpdate(task)
  }
  throw new Error('Import task polling time limit reached')
}
