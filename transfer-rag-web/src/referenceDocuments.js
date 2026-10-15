import { positiveInteger, readingUrl } from './sourceReading.js'

export function citationAnchor(scope, citationId) {
  return /^[A-Za-z0-9_-]+$/.test(scope) && /^S\d+$/.test(citationId || '') ? `${scope}-${citationId}` : null
}

// Deduplicate documents, never citation identities: every S-number keeps its original document/page.
export function referenceDocuments(sources) {
  const documents = new Map()
  for (const source of sources || []) {
    if (!source || typeof source !== 'object') continue
    const id = positiveInteger(source.documentId)
    const key = id ? `document:${id}` : `unknown:${documents.size}`
    if (!documents.has(key)) documents.set(key, { ...source, documentId: id, references: [] })
    const document = documents.get(key)
    if (!document.references.some(item => item.citationId === source.citationId)) {
      document.references.push({ citationId: source.citationId, chunkId: source.chunkId, sourcePage: source.sourcePage })
    }
  }
  return [...documents.values()]
}

// Metadata only: do not fetch the original text/PDF just to display a reference link.
// One in-flight check per document; grants are short-lived and file requests recheck on the server.
export function createPublicReadingCheck(fetcher = fetch, now = Date.now) {
  const entries = new Map()
  return async function check(source) {
    const id = positiveInteger(source.documentId)
    if (!id || source.sourceType === 'CURATED') return null
    let entry = entries.get(id)
    if (!entry || entry.expires <= now()) {
      if (entries.size >= 100) entries.delete(entries.keys().next().value)
      const promise = (async () => {
        try {
          const response = await fetcher(`/api/public/sources/${id}`, {
            method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error',
            signal: AbortSignal.timeout(5000),
          })
          if (!response.ok) return false
          const info = await response.json()
          return info.documentId === id && typeof info.title === 'string'
            && ['AVAILABLE', 'DOWNLOADABLE'].includes(info.availability)
            && ['md', 'txt', 'pdf', 'docx'].includes(info.format)
        } catch { return false }
      })()
      entry = { promise, expires: now() + 30000 }
      entries.set(id, entry)
    }
    return await entry.promise ? readingUrl(id, source.sourcePage) : null
  }
}

export const publicReadingCheck = createPublicReadingCheck()
