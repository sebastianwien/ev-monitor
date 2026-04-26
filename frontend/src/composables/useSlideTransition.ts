export function useSlideTransition(durationMs = 280) {
  const ease = `${durationMs}ms cubic-bezier(0.4, 0, 0.2, 1)`

  function onEnter(el: Element) {
    const e = el as HTMLElement
    e.style.height = '0'
    e.style.overflow = 'hidden'
    e.style.transition = `height ${ease}`
    requestAnimationFrame(() => {
      e.style.height = e.scrollHeight + 'px'
    })
  }

  function onAfterEnter(el: Element) {
    const e = el as HTMLElement
    e.style.height = ''
    e.style.overflow = ''
    e.style.transition = ''
  }

  function onLeave(el: Element) {
    const e = el as HTMLElement
    e.style.height = e.scrollHeight + 'px'
    e.style.overflow = 'hidden'
    e.style.transition = `height ${ease}`
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        e.style.height = '0'
      })
    })
  }

  function onAfterLeave(el: Element) {
    const e = el as HTMLElement
    e.style.height = ''
    e.style.overflow = ''
    e.style.transition = ''
  }

  return { onEnter, onAfterEnter, onLeave, onAfterLeave }
}
