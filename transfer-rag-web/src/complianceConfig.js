const configuredEmail = (import.meta.env.VITE_CONTACT_EMAIL || '').trim()

export const contactEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(configuredEmail)
  ? configuredEmail
  : ''

export const feedbackIssuesUrl = 'https://github.com/Zhenxiangyu05/NJU-TransferRAG/issues/new'

export const icpNumber = (import.meta.env.VITE_ICP_NUMBER || '').trim()
export const policeRecordNumber = (import.meta.env.VITE_POLICE_RECORD_NUMBER || '').trim()
