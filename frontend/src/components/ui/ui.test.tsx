import {
  cleanup,
  createEvent,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react'
import { createRef } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Badge } from './Badge'
import { Button } from './Button'
import { Card } from './Card'
import { Dialog, Drawer } from './Dialog'
import { EmptyState } from './EmptyState'
import { ErrorState } from './ErrorState'
import { Input } from './Input'
import { Progress } from './Progress'
import { Select } from './Select'
import { Skeleton } from './Skeleton'
import { Textarea } from './Textarea'

afterEach(() => {
  cleanup()
  document.body.style.overflow = ''
  vi.restoreAllMocks()
})

describe('Button', () => {
  it('renders the approved native variants and defaults to a non-submit button', () => {
    render(
      <>
        <Button>Primary</Button>
        <Button variant="secondary">Secondary</Button>
        <Button variant="tertiary">Tertiary</Button>
      </>,
    )

    expect(screen.getByRole('button', { name: 'Primary' })).toHaveAttribute('type', 'button')
    expect(screen.getByRole('button', { name: 'Primary' })).toHaveClass('ui-button-primary')
    expect(screen.getByRole('button', { name: 'Secondary' })).toHaveClass('ui-button-secondary')
    expect(screen.getByRole('button', { name: 'Tertiary' })).toHaveClass('ui-button-tertiary')
  })

  it('supports native interaction and disabled semantics', () => {
    const onClick = vi.fn()
    render(
      <>
        <Button onClick={onClick}>Available</Button>
        <Button disabled onClick={onClick}>
          Disabled
        </Button>
      </>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Available' }))
    fireEvent.click(screen.getByRole('button', { name: 'Disabled' }))

    expect(onClick).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Disabled' })).toBeDisabled()
  })
})

describe('Input, Textarea, and Select', () => {
  it('associates an input label, generated description, and error', () => {
    render(
      <Input
        description="Use your institutional email."
        error="Email is required."
        label="Email"
      />,
    )

    const input = screen.getByLabelText('Email')
    const inputId = input.getAttribute('id')

    expect(inputId).toBeTruthy()
    expect(input).toHaveAttribute('aria-describedby', `${inputId}-description ${inputId}-error`)
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })

  it('preserves explicit IDs and native required and disabled semantics', () => {
    render(<Textarea disabled label="Response" required />)
    render(<Input id="student-id" label="Student ID" required />)

    expect(screen.getByLabelText('Response')).toBeDisabled()
    expect(screen.getByLabelText('Response')).toBeRequired()
    expect(screen.getByLabelText('Student ID')).toHaveAttribute('id', 'student-id')
    expect(screen.getByLabelText('Student ID')).toBeRequired()
  })

  it('uses native select options and field descriptions', () => {
    render(
      <Select description="Choose one option." label="Subject">
        <option value="">Choose a subject</option>
        <option value="anatomy">Anatomy</option>
      </Select>,
    )

    const select = screen.getByLabelText('Subject')
    expect(within(select).getAllByRole('option')).toHaveLength(2)
    expect(select).toHaveAttribute('aria-describedby')
  })
})

describe('Card and Badge', () => {
  it('keeps Card as a plain grouping primitive', () => {
    render(<Card>Study content</Card>)

    const card = screen.getByText('Study content')
    expect(card).toHaveClass('ui-card')
    expect(card).not.toHaveAttribute('role')
  })

  it('renders only approved qualitative Badge tones with visible meaning', () => {
    render(
      <>
        <Badge>Neutral</Badge>
        <Badge tone="strong">Strong</Badge>
        <Badge tone="developing">Developing</Badge>
        <Badge tone="needs-attention">Needs Attention</Badge>
        <Badge tone="insufficient-evidence">Insufficient Evidence</Badge>
      </>,
    )

    expect(screen.getByText('Strong')).toHaveClass('ui-badge-strong')
    expect(screen.getByText('Developing')).toHaveClass('ui-badge-developing')
    expect(screen.getByText('Needs Attention')).toHaveClass('ui-badge-needs-attention')
    expect(screen.getByText('Insufficient Evidence')).toHaveClass(
      'ui-badge-insufficient-evidence',
    )
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()
  })
})

describe('Progress and Skeleton', () => {
  it('uses native quantitative progress semantics without adding percentage text', () => {
    render(<Progress label="Material upload" max={200} value={80} />)

    const progress = screen.getByRole('progressbar', { name: 'Material upload' })
    expect(progress).toHaveAttribute('value', '80')
    expect(progress).toHaveAttribute('max', '200')
    expect(screen.queryByText(/80%/)).not.toBeInTheDocument()
  })

  it('keeps decorative skeletons hidden from assistive technology', () => {
    const { container } = render(<Skeleton className="content-placeholder" />)

    expect(container.querySelector('.content-placeholder')).toHaveAttribute('aria-hidden', 'true')
    expect(container.querySelector('.content-placeholder')).not.toHaveAttribute('role')
  })

  it('can expose a concrete loading status when explicitly labelled', () => {
    render(<Skeleton label="Loading material" />)

    expect(screen.getByRole('status', { name: 'Loading material' })).toBeInTheDocument()
  })
})

describe('EmptyState and ErrorState', () => {
  it('renders required content and an optional action', () => {
    render(
      <EmptyState
        action={<Button>Start studying</Button>}
        description="There is no material here yet."
        title="Nothing to study"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Nothing to study' })).toBeInTheDocument()
    expect(screen.getByText('There is no material here yet.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start studying' })).toBeInTheDocument()
  })

  it('renders ErrorState as safe presentation content', () => {
    render(
      <ErrorState
        action={<Button variant="tertiary">Try again</Button>}
        description="The page could not be loaded."
        title="Unable to display this page"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Unable to display this page' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.queryByText(/stack|exception|ProblemDetail/i)).not.toBeInTheDocument()
  })
})

describe('Dialog and Drawer', () => {
  it('uses the native dialog lifecycle and accessible associations', () => {
    const showModal = vi.spyOn(HTMLDialogElement.prototype, 'showModal')
    const close = vi.spyOn(HTMLDialogElement.prototype, 'close')
    const onClose = vi.fn()
    const trigger = document.createElement('button')
    document.body.append(trigger)
    trigger.focus()

    const { rerender } = render(
      <Dialog description="Dialog details" onClose={onClose} open title="Dialog title">
        Dialog content
      </Dialog>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Dialog title' })
    expect(showModal).toHaveBeenCalledTimes(1)
    expect(dialog).toHaveAttribute('aria-labelledby')
    expect(dialog).toHaveAttribute('aria-describedby')
    expect(document.body.style.overflow).toBe('hidden')

    fireEvent(dialog, new Event('cancel', { bubbles: true, cancelable: true }))
    expect(onClose).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }))
    expect(onClose).toHaveBeenCalledTimes(2)

    rerender(
      <Dialog description="Dialog details" onClose={onClose} open={false} title="Dialog title">
        Dialog content
      </Dialog>,
    )

    expect(close).toHaveBeenCalledTimes(1)
    expect(document.body.style.overflow).toBe('')
    expect(trigger).toHaveFocus()
  })

  it('honors initial focus and optional backdrop dismissal', () => {
    const initialFocusRef = createRef<HTMLInputElement>()
    const onClose = vi.fn()
    const { rerender } = render(
      <Dialog
        closeOnBackdrop={false}
        initialFocusRef={initialFocusRef}
        onClose={onClose}
        open
        title="Focused dialog"
      >
        <input ref={initialFocusRef} aria-label="Focused field" />
      </Dialog>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Focused dialog' })
    expect(initialFocusRef.current).toHaveFocus()
    fireEvent.click(dialog)
    expect(onClose).not.toHaveBeenCalled()

    rerender(
      <Dialog
        initialFocusRef={initialFocusRef}
        onClose={onClose}
        open
        title="Focused dialog"
      >
        <input ref={initialFocusRef} aria-label="Focused field" />
      </Dialog>,
    )

    fireEvent.click(screen.getByRole('dialog', { name: 'Focused dialog' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('wraps forward Tab from the last eligible descendant to the first', () => {
    render(
      <Dialog onClose={vi.fn()} open title="Tab dialog">
        <a href="/first">First link</a>
        <button type="button">Last action</button>
      </Dialog>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Tab dialog' })
    const first = screen.getByRole('button', { name: 'Close dialog' })
    const last = screen.getByRole('button', { name: 'Last action' })
    last.focus()
    const event = createEvent.keyDown(dialog, { key: 'Tab' })

    fireEvent(dialog, event)

    expect(event.defaultPrevented).toBe(true)
    expect(first).toHaveFocus()
  })

  it('wraps reverse Tab from the first eligible descendant to the last', () => {
    render(
      <Dialog onClose={vi.fn()} open title="Reverse tab dialog">
        <a href="/first">First link</a>
        <button type="button">Last action</button>
      </Dialog>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Reverse tab dialog' })
    const first = screen.getByRole('button', { name: 'Close dialog' })
    const last = screen.getByRole('button', { name: 'Last action' })
    first.focus()
    const event = createEvent.keyDown(dialog, { key: 'Tab', shiftKey: true })

    fireEvent(dialog, event)

    expect(event.defaultPrevented).toBe(true)
    expect(last).toHaveFocus()
  })

  it('keeps focus on the dialog when no eligible descendant exists', () => {
    render(
      <Dialog onClose={vi.fn()} open title="Empty tab dialog">
        {null}
      </Dialog>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Empty tab dialog' })
    screen.getByRole('button', { name: 'Close dialog' }).setAttribute('tabindex', '-1')
    dialog.focus()
    const event = createEvent.keyDown(dialog, { key: 'Tab' })

    fireEvent(dialog, event)

    expect(event.defaultPrevented).toBe(true)
    expect(dialog).toHaveFocus()
  })

  it('uses the same focus boundary behavior for Drawer', () => {
    render(
      <Drawer onClose={vi.fn()} open title="Tab drawer">
        <a href="/first">First link</a>
        <button type="button">Last action</button>
      </Drawer>,
    )

    const drawer = screen.getByRole('dialog', { name: 'Tab drawer' })
    const first = screen.getByRole('button', { name: 'Close dialog' })
    const last = screen.getByRole('button', { name: 'Last action' })
    last.focus()
    const forwardEvent = createEvent.keyDown(drawer, { key: 'Tab' })
    fireEvent(drawer, forwardEvent)

    expect(forwardEvent.defaultPrevented).toBe(true)
    expect(first).toHaveFocus()

    first.focus()
    const reverseEvent = createEvent.keyDown(drawer, { key: 'Tab', shiftKey: true })
    fireEvent(drawer, reverseEvent)

    expect(reverseEvent.defaultPrevented).toBe(true)
    expect(last).toHaveFocus()
  })

  it('reuses the native dialog foundation for Drawer as a visual variant', () => {
    render(
      <Drawer onClose={vi.fn()} open title="Source drawer">
        Source content
      </Drawer>,
    )

    expect(screen.getByRole('dialog', { name: 'Source drawer' })).toHaveClass('ui-dialog-drawer')
  })
})
