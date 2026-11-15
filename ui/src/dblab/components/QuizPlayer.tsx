import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type AnswerResult } from '../api'
import { renderMarkdown } from '../../shared/markdown'

export default function QuizPlayer({ section, lecture }: { section: string; lecture: string }) {
  const qc = useQueryClient()
  const quiz = useQuery({ queryKey: ['quiz', section, lecture], queryFn: () => api.quiz(section, lecture) })
  const [index, setIndex] = useState(0)
  const [choice, setChoice] = useState<number | null>(null)
  const [result, setResult] = useState<AnswerResult | null>(null)
  const [session, setSession] = useState({ answered: 0, correct: 0 })

  const questions = quiz.data?.questions ?? []
  const q = questions[index]

  const submit = useMutation({
    mutationFn: () => api.answer(section, lecture, q.id, choice as number),
    onSuccess: (r) => {
      setResult(r)
      setSession((s) => ({ answered: s.answered + 1, correct: s.correct + (r.correct ? 1 : 0) }))
      qc.invalidateQueries({ queryKey: ['overview'] })
      qc.invalidateQueries({ queryKey: ['quiz', section, lecture] })
    },
  })

  function restart() {
    setIndex(0)
    setChoice(null)
    setResult(null)
    setSession({ answered: 0, correct: 0 })
  }

  if (quiz.isLoading) return <p className="muted">Loading quiz…</p>
  if (quiz.isError) return <p className="error">{(quiz.error as Error).message}</p>
  if (questions.length === 0) return <p className="muted">No questions.</p>

  if (index >= questions.length) {
    return (
      <div className="card">
        <h3 className="cardtitle">Quiz finished</h3>
        <p>
          You answered {session.correct} of {session.answered} correctly in this run.
        </p>
        <button className="btn primary" onClick={restart}>
          Run it again
        </button>
      </div>
    )
  }

  return (
    <div className="card">
      <div className="metaline">
        <span className="label">
          Question {index + 1} of {questions.length}
        </span>
        {q.kind === 'predict' && <span className="chip neutral">Predict the output</span>}
        {q.answered && <span className={`chip ${q.correct ? 'ok' : 'warn'}`}>{q.correct ? 'answered correctly before' : 'missed before'}</span>}
      </div>
      <div className="prose" dangerouslySetInnerHTML={{ __html: renderMarkdown(q.prompt) }} />
      {q.code && <pre className="code">{q.code}</pre>}
      <div className="options" role="radiogroup" aria-label="Answers">
        {q.options.map((o, i) => {
          const state = result ? (i === result.correctIndex ? 'right' : i === choice ? 'wrong' : '') : i === choice ? 'picked' : ''
          return (
            <label key={i} className={`option ${state}`}>
              <input type="radio" name={`q-${q.id}`} checked={choice === i} disabled={result !== null} onChange={() => setChoice(i)} />
              <span>{o}</span>
            </label>
          )
        })}
      </div>
      {result && (
        <div className={`banner ${result.correct ? 'okbanner' : 'warnbanner'}`}>
          <strong>{result.correct ? 'Correct.' : 'Not quite.'}</strong>
          <div className="prose" dangerouslySetInnerHTML={{ __html: renderMarkdown(result.explanation) }} />
        </div>
      )}
      {submit.isError && <p className="error">{(submit.error as Error).message}</p>}
      <div className="actions">
        {!result ? (
          <button className="btn primary" disabled={choice === null || submit.isPending} onClick={() => submit.mutate()}>
            Check answer
          </button>
        ) : (
          <button
            className="btn primary"
            onClick={() => {
              setIndex(index + 1)
              setChoice(null)
              setResult(null)
            }}
          >
            {index + 1 < questions.length ? 'Next question' : 'Finish'}
          </button>
        )}
      </div>
    </div>
  )
}
