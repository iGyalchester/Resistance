import { useSearchParams } from 'react-router-dom';
import AssistantConversation from '../components/assistant/AssistantConversation';

/** The full-page chat. ?q= arrives from the Help page's "Ask the assistant" links. */
export default function AssistantPage() {
  const [params] = useSearchParams();
  const q = params.get('q') ?? undefined;

  return (
    <>
      <h1>Assistant</h1>
      <p className="muted">
        It only sees your own applications and contacts, and it never changes anything on its own: a suggested change
        is a card you apply or dismiss.
      </p>
      <section className="card assistant-page">
        <AssistantConversation initialMessage={q} />
      </section>
    </>
  );
}
