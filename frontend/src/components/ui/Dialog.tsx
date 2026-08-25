import {
  useEffect,
  useId,
  useRef,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react'
import { createPortal } from 'react-dom'

export type OverlayProps = {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  children: ReactNode
  closeLabel?: string
  closeOnBackdrop?: boolean
  initialFocusRef?: RefObject<HTMLElement | null>
  className?: string
}

type OverlayVariant = 'dialog' | 'drawer'

type NativeOverlayProps = OverlayProps & {
  variant: OverlayVariant
}

const focusableSelector = [
  'a[href]',
  'area[href]',
  'button',
  'input',
  'select',
  'textarea',
  'iframe',
  'object',
  'embed',
  'audio[controls]',
  'video[controls]',
  'summary',
  '[contenteditable="true"]',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function getFocusableDescendants(dialog: HTMLDialogElement) {
  return Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelector)).filter((element) => {
    if (element.matches(':disabled') || element.getAttribute('tabindex') === '-1') {
      return false
    }

    if (element.hidden || element.closest('[hidden], [inert], [aria-hidden="true"]') !== null) {
      return false
    }

    if (element instanceof HTMLInputElement && element.type === 'hidden') {
      return false
    }

    const styles = getComputedStyle(element)
    return styles.display !== 'none' && styles.visibility !== 'hidden'
  })
}

function NativeOverlay({
  children,
  className,
  closeLabel = 'Close dialog',
  closeOnBackdrop = true,
  description,
  initialFocusRef,
  onClose,
  open,
  title,
  variant,
}: NativeOverlayProps) {
  const localDialogRef = useRef<HTMLDialogElement>(null)
  const dialogTitleId = useId()
  const dialogDescriptionId = useId()
  const wasOpenRef = useRef(false)
  const restoreFocusRef = useRef<HTMLElement | null>(null)
  const previousBodyOverflowRef = useRef<string | null>(null)
  const dialogRef = localDialogRef

  function handleTabBoundary(event: KeyboardEvent<HTMLDialogElement>) {
    if (!open || event.key !== 'Tab') {
      return
    }

    const dialog = dialogRef.current
    if (dialog === null) {
      return
    }

    const focusable = getFocusableDescendants(dialog)
    if (focusable.length === 0) {
      event.preventDefault()
      dialog.focus()
      return
    }

    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const activeElement = document.activeElement
    const focusIsInside = activeElement instanceof HTMLElement && dialog.contains(activeElement)

    if (event.shiftKey) {
      if (!focusIsInside || activeElement === dialog || activeElement === first) {
        event.preventDefault()
        last.focus()
      }
      return
    }

    if (!focusIsInside || activeElement === dialog || activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog === null) {
      return
    }

    if (open && !wasOpenRef.current) {
      restoreFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      previousBodyOverflowRef.current = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      dialog.showModal()
      wasOpenRef.current = true
      initialFocusRef?.current?.focus()
    }

    if (!open && wasOpenRef.current) {
      dialog.close()
      wasOpenRef.current = false
      document.body.style.overflow = previousBodyOverflowRef.current ?? ''
      previousBodyOverflowRef.current = null
      restoreFocusRef.current?.focus()
      restoreFocusRef.current = null
    }
  }, [dialogRef, initialFocusRef, open])

  useEffect(() => {
    const dialog = dialogRef.current

    return () => {
      if (dialog?.open) {
        dialog.close()
      }
      if (previousBodyOverflowRef.current !== null) {
        document.body.style.overflow = previousBodyOverflowRef.current
      }
      restoreFocusRef.current?.focus()
    }
  }, [dialogRef])

  const classes = [
    'ui-dialog',
    variant === 'drawer' ? 'ui-dialog-drawer' : undefined,
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return createPortal(
    <dialog
      aria-describedby={description ? dialogDescriptionId : undefined}
      aria-labelledby={dialogTitleId}
      className={classes}
      onKeyDown={handleTabBoundary}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        if (closeOnBackdrop && event.target === event.currentTarget) {
          onClose()
        }
      }}
      ref={dialogRef}
      tabIndex={-1}
    >
      <div className="ui-dialog-panel" onClick={(event) => event.stopPropagation()}>
        <header className="ui-dialog-header">
          <div>
            <h2 className="ui-dialog-title" id={dialogTitleId}>
              {title}
            </h2>
            {description ? (
              <p className="ui-dialog-description" id={dialogDescriptionId}>
                {description}
              </p>
            ) : null}
          </div>
          <button className="ui-dialog-close" onClick={onClose} type="button">
            {closeLabel}
          </button>
        </header>
        <div className="ui-dialog-body">{children}</div>
      </div>
    </dialog>,
    document.body,
  )
}

export function Dialog(props: OverlayProps) {
  return <NativeOverlay {...props} variant="dialog" />
}

export function Drawer(props: OverlayProps) {
  return <NativeOverlay {...props} variant="drawer" />
}
