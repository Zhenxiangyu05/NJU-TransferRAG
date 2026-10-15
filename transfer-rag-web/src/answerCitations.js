import { citationAnchor } from './referenceDocuments.js'

// Operates on already sanitized Markdown. Model-supplied URLs are not public-source authorization.
export function decorateCitations(html, sources, scope, dom = document) {
  const container = dom.createElement('div')
  container.innerHTML = html
  for (const link of container.querySelectorAll('a')) link.replaceWith(...link.childNodes)
  const ids = new Set((sources || []).map(source => source.citationId).filter(id => citationAnchor(scope, id)))
  const walker = dom.createTreeWalker(container, 4) // SHOW_TEXT
  const nodes = []
  while (walker.nextNode()) if (!walker.currentNode.parentElement.closest('code, pre')) nodes.push(walker.currentNode)
  for (const node of nodes) {
    const fragments = node.textContent.split(/(\[S\d+\])/g)
    if (fragments.length === 1) continue
    const replacements = fragments.map(text => {
      const id = text.slice(1, -1)
      if (!/^\[S\d+\]$/.test(text) || !ids.has(id)) return dom.createTextNode(text)
      const link = dom.createElement('a')
      link.className = 'body-citation'
      link.setAttribute('href', `#${citationAnchor(scope, id)}`)
      link.textContent = text
      return link
    })
    node.replaceWith(...replacements)
  }
  return container.innerHTML
}
