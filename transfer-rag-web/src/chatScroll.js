export function isNearBottom({ scrollHeight, scrollTop, clientHeight }, threshold = 96) {
  return scrollHeight - scrollTop - clientHeight <= threshold
}

// A single scroll owner. Preserve a visible turn's offset when references or the viewport resize.
export function connectChatScroll(view, content, changed, environment = window) {
  let following = true
  let lastTop = view.scrollTop
  let anchor = null
  let frame = null
  let intentUntil = 0
  const now = () => Date.now()
  function scrollIntent(event) {
    if (event.type !== 'keydown' || ['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) intentUntil = now() + 1500
  }
  function capture() {
    const top = view.getBoundingClientRect().top
    const element = [...content.querySelectorAll('.chat-turn')].find(item => item.getBoundingClientRect().bottom > top)
    anchor = element ? { element, offset: element.getBoundingClientRect().top - top } : null
  }
  function bottom() {
    intentUntil = 0
    following = true
    view.scrollTop = Math.max(0, view.scrollHeight - view.clientHeight)
    lastTop = view.scrollTop
    capture()
    changed(true)
  }
  function refresh() {
    if (following) bottom()
    else if (anchor?.element.isConnected) {
      view.scrollTop += anchor.element.getBoundingClientRect().top - view.getBoundingClientRect().top - anchor.offset
      lastTop = view.scrollTop
    }
  }
  function schedule() {
    if (frame !== null) return
    frame = environment.requestAnimationFrame(() => { frame = null; refresh() })
  }
  function scroll() {
    if (Math.abs(view.scrollTop - lastTop) > 1) {
      // Resize/focus/layout can fire trusted scroll events too. They are not a user reading gesture.
      if (now() > intentUntil) { if (following) schedule(); return }
      intentUntil = now() + 1500
      following = isNearBottom(view)
      lastTop = view.scrollTop
      capture()
      changed(following)
    }
  }
  view.addEventListener('scroll', scroll, { passive: true })
  for (const name of ['wheel', 'touchstart', 'pointerdown', 'keydown']) view.addEventListener(name, scrollIntent, { passive: true })
  const observer = new environment.ResizeObserver(schedule)
  observer.observe(content)
  observer.observe(view)
  capture()
  return {
    refresh: schedule, bottom,
    destroy() {
      observer.disconnect(); view.removeEventListener('scroll', scroll)
      for (const name of ['wheel', 'touchstart', 'pointerdown', 'keydown']) view.removeEventListener(name, scrollIntent)
      if (frame !== null) environment.cancelAnimationFrame(frame)
    },
  }
}
