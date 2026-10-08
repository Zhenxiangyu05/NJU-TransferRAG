export function positiveInteger(value) {
  if (typeof value !== 'number' && typeof value !== 'string') return null
  if (!/^[1-9]\d*$/.test(String(value))) return null
  const number = Number(value)
  return Number.isSafeInteger(number) ? number : null
}

export function readingUrl(documentId, sourcePage) {
  const id = positiveInteger(documentId)
  if (!id) return null
  const page = positiveInteger(sourcePage)
  return `/sources/${id}${page ? `?page=${page}` : ''}`
}

export function pdfUrl(documentId, page) {
  const id = positiveInteger(documentId)
  if (!id) return null
  const sourcePage = positiveInteger(page)
  return `/api/public/sources/${id}/file${sourcePage ? `#page=${sourcePage}` : ''}`
}

export function sourceTypeText(value) {
  return {
    OFFICIAL: '官方资料',
    OFFICIAL_PDF: '官方 PDF',
    PERSONAL: '个人整理 / 经验资料',
    CURATED: '整理知识（非原始 Evidence）',
    COMMUNITY: '社区资料',
    GITHUB: 'GitHub 资料',
  }[value] || '参考资料'
}

export async function loadPublicSource(documentId, fetcher = fetch, signal) {
  const id = positiveInteger(documentId)
  if (!id) throw new Error('原文链接无效，请返回问答页面。')
  const options = { signal, credentials: 'omit' }
  const response = await fetcher(`/api/public/sources/${id}`, options)
  if (!response.ok) throw new Error('该原文尚未确认公开展示权限，或暂不可用。已有问答不受影响。')
  const info = await response.json()
  if (info.documentId !== id || typeof info.title !== 'string'
      || typeof info.format !== 'string' || typeof info.availability !== 'string') {
    throw new Error('原文信息暂不可用，请稍后再试。')
  }
  let text = ''
  if (info.availability === 'AVAILABLE' && ['md', 'txt'].includes(info.format)) {
    const textResponse = await fetcher(`/api/public/sources/${id}/text`, options)
    if (!textResponse.ok) throw new Error('原文文本暂不可用，请稍后再试。')
    const data = await textResponse.json()
    if (typeof data.text !== 'string') throw new Error('原文文本暂不可用，请稍后再试。')
    text = data.text
  }
  return { info, text }
}
