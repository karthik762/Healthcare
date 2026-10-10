
import { useState } from "react";
import api from "./api";
import "./PatientIntake.css";

const API_URL = "/intake";

function PatientIntake() {
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState("");
  const [error, setError] = useState("");

  const startChat = async () => {
    try {
      setLoading(true);
      setError("");
      setSummary("");

      const response = await api.post(`${API_URL}/start`);

      setSessionId(response.data.sessionId);
      setMessages([
        {
          sender: "assistant",
          text: response.data.reply
        }
      ]);
    } catch (err) {
      setError("Could not start the conversation. Check that the backend is running.");
    } finally {
      setLoading(false);
    }
  };

  const sendMessage = async (event) => {
    event.preventDefault();

    if (!input.trim() || !sessionId || loading) return;

    const patientMessage = input.trim();

    setMessages((previous) => [
      ...previous,
      { sender: "patient", text: patientMessage }
    ]);

    setInput("");
    setLoading(true);
    setError("");

    try {
      const response = await api.post(
        `${API_URL}/${sessionId}/message`,
        { message: patientMessage }
      );

      setMessages((previous) => [
        ...previous,
        {
          sender: "assistant",
          text: response.data.reply
        }
      ]);

      if (response.data.status === "URGENT") {
        setError(
          "Please seek urgent medical attention as advised. This chat cannot assess emergencies reliably."
        );
      }
    } catch (err) {
      setError("Message failed to send. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const generateSummary = async () => {
    if (!sessionId || loading) return;

    try {
      setLoading(true);
      setError("");

      const response = await api.post(
        `${API_URL}/${sessionId}/summary`
      );

      setSummary(response.data.summary);
    } catch (err) {
      setError(
        err.response?.data?.message ||
        "Could not generate the summary. Check the backend."
      );
    } finally {
      setLoading(false);
    }
  };

  const startNewChat = () => {
    setSessionId(null);
    setMessages([]);
    setInput("");
    setSummary("");
    setError("");
  };

  return (
    <div className="intake-page">
      <div className="intake-card">
        <header className="intake-header">
          <div>
            <h2>Patient Intake Assistant</h2>
            <p>Share your symptoms to prepare information for your doctor.</p>
          </div>

          {sessionId && (
            <button
              className="secondary-button"
              onClick={startNewChat}
              disabled={loading}
            >
              New Chat
            </button>
          )}
        </header>

        {!sessionId ? (
          <div className="intake-welcome">
            <h3>Let's get started</h3>
            <p>
              I'll ask a few questions to help organise your
              information for a healthcare professional.
            </p>
            <button onClick={startChat} disabled={loading}>
              {loading ? "Starting..." : "Start Conversation"}
            </button>
          </div>
        ) : (
          <>
            <div className="chat-messages">
              {messages.map((message, index) => (
                <div
                  key={index}
                  className={`message-row ${message.sender}`}
                >
                  <div className="message-bubble">
                    <span className="message-label">
                      {message.sender === "patient"
                        ? "You"
                        : "Assistant"}
                    </span>
                    <p>{message.text}</p>
                  </div>
                </div>
              ))}

              {loading && (
                <div className="typing-indicator">
                  Assistant is thinking...
                </div>
              )}
            </div>

            <form className="chat-input-form" onSubmit={sendMessage}>
              <input
                type="text"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder="Type your response..."
                disabled={loading || Boolean(summary)}
              />

              <button
                type="submit"
                disabled={loading || !input.trim() || Boolean(summary)}
              >
                Send
              </button>
            </form>

            <button
              className="summary-button"
              onClick={generateSummary}
              disabled={loading || Boolean(summary)}
            >
              {loading ? "Please wait..." : "Generate Doctor Summary"}
            </button>
          </>
        )}

        {error && <div className="intake-error">{error}</div>}

        {summary && (
          <section className="doctor-summary">
            <h3>Doctor Intake Summary</h3>
            <pre>{summary}</pre>
            <p>
              This is an AI-generated intake summary. A healthcare
              professional must review it.
            </p>
          </section>
        )}

        <footer className="intake-disclaimer">
          This assistant does not diagnose conditions or replace
          medical care. For a possible emergency, seek immediate
          medical attention.
        </footer>
      </div>
    </div>
  );
}

export default PatientIntake;
