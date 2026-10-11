import { useState } from "react";
import api from "./api";
import { LycorisMark } from "./components/LycorisMark";
import "./Auth.css";

function Auth({ onLogin }) {
    const [isRegister, setIsRegister] = useState(false);
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setMessage("");
        setLoading(true);

        try {
            const endpoint = isRegister ? "/auth/register" : "/auth/login";
            const data = isRegister
                ? { name: name.trim(), email: email.trim(), password }
                : { email: email.trim(), password };

            const response = await api.post(endpoint, data);
            const user = response.data;

            localStorage.setItem("token", user.token);
            localStorage.setItem("user", JSON.stringify({
                id: user.id,
                name: user.name,
                email: user.email
            }));

            onLogin(user);
        } catch (error) {
            const responseMessage = error.response?.data?.detail
                || error.response?.data?.message;

            setMessage(
                responseMessage ||
                (error.response?.status === 409
                    ? "An account with this email already exists."
                    : "Unable to sign in. Check your details and backend connection.")
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="lycoris-auth-experience">
            {/* Ambient atmospheric red chrome bloom */}
            <div className="lycoris-ambient-glow" aria-hidden="true" />
            <div className="lycoris-ambient-glow-secondary" aria-hidden="true" />

            {/* Top Bar */}
            <header className="lycoris-auth-topbar">
                <div className="lycoris-brand">
                    <LycorisMark size={30} />
                    <span className="lycoris-brand-title">HEALTHSUMMARIZER</span>
                    <span className="lycoris-brand-divider">/</span>
                    <span className="lycoris-brand-edition">CLINICAL INTELLIGENCE</span>
                </div>
            </header>

            {/* Main Hero & Auth Split Layout */}
            <main className="lycoris-auth-main">
                {/* Left: Editorial Hero */}
                <section className="lycoris-hero-pane">
                    <div className="lycoris-badge">
                        <span className="lycoris-badge-dot" />
                        <span>LYCORIS BOTANICAL EDITION • AI HEALTH SYSTEM</span>
                    </div>

                    <h1 className="lycoris-hero-title">
                        Your Health,<br />
                        <span className="lycoris-highlight-title">in a Different Light.</span>
                    </h1>

                    <p className="lycoris-hero-lead">
                        A dark editorial clinical intelligence workspace. Securely archive laboratory reports,
                        generate instant AI medical syntheses, and triage symptoms through an intuitive,
                        compassionate intake assistant.
                    </p>

                    {/* Feature Pillars */}
                    <div className="lycoris-pillars-grid">
                        <div className="lycoris-pillar">
                            <span className="pillar-num">01</span>
                            <h4>Clinical Synthesis</h4>
                            <p>Upload blood reports or TXT notes for instant key findings extraction.</p>
                        </div>
                        <div className="lycoris-pillar">
                            <span className="pillar-num">02</span>
                            <h4>Patient Intake</h4>
                            <p>Guided clinical triage preparing clear summaries for your doctor.</p>
                        </div>
                        <div className="lycoris-pillar">
                            <span className="pillar-num">03</span>
                            <h4>Private Archive</h4>
                            <p>Protected health record repository with JWT authorization.</p>
                        </div>
                    </div>
                </section>

                {/* Right: Authentication Form Card */}
                <section className="lycoris-form-pane">
                    <div className="lycoris-form-card">
                        {/* Tab Switcher */}
                        <div className="lycoris-tab-nav" role="tablist">
                            <button
                                type="button"
                                role="tab"
                                aria-selected={!isRegister}
                                className={`lycoris-tab-btn ${!isRegister ? "active" : ""}`}
                                onClick={() => {
                                    setIsRegister(false);
                                    setMessage("");
                                }}
                            >
                                Sign In
                            </button>
                            <button
                                type="button"
                                role="tab"
                                aria-selected={isRegister}
                                className={`lycoris-tab-btn ${isRegister ? "active" : ""}`}
                                onClick={() => {
                                    setIsRegister(true);
                                    setMessage("");
                                }}
                            >
                                Create Account
                            </button>
                        </div>

                        <div className="lycoris-form-header">
                            <h2>{isRegister ? "Begin Your Medical Archive" : "Access Your Sanctuary"}</h2>
                            <p>
                                {isRegister
                                    ? "Set up your private HealthSummarizer account to securely organize records."
                                    : "Enter your registered credentials to access your health documents."}
                            </p>
                        </div>

                        <form className="lycoris-form" onSubmit={handleSubmit}>
                            {isRegister && (
                                <div className="lycoris-field">
                                    <label htmlFor="auth-name">
                                        Full Name
                                        <span className="req">*</span>
                                    </label>
                                    <input
                                        id="auth-name"
                                        type="text"
                                        value={name}
                                        onChange={(e) => setName(e.target.value)}
                                        placeholder="e.g. Dr. Eleanor Vance"
                                        required
                                        maxLength={100}
                                        autoComplete="name"
                                    />
                                </div>
                            )}

                            <div className="lycoris-field">
                                <label htmlFor="auth-email">
                                    Email Address
                                    <span className="req">*</span>
                                </label>
                                <input
                                    id="auth-email"
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    placeholder="name@example.com"
                                    required
                                    maxLength={150}
                                    autoComplete="email"
                                />
                            </div>

                            <div className="lycoris-field">
                                <label htmlFor="auth-password">
                                    Password
                                    <span className="req">*</span>
                                </label>
                                <input
                                    id="auth-password"
                                    type="password"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    placeholder={isRegister ? "Minimum 8 characters" : "Enter your password"}
                                    required
                                    minLength={isRegister ? 8 : 1}
                                    maxLength={72}
                                    autoComplete={isRegister ? "new-password" : "current-password"}
                                />
                            </div>

                            {message && (
                                <div className="lycoris-message" role="alert">
                                    <span className="lycoris-msg-icon">!</span>
                                    <span>{message}</span>
                                </div>
                            )}

                            <button
                                type="submit"
                                className="lycoris-submit-btn"
                                disabled={loading}
                            >
                                {loading ? (
                                    <span className="btn-loading-flex">
                                        <span className="lycoris-spinner" />
                                        <span>Communicating with Clinical Vault...</span>
                                    </span>
                                ) : isRegister ? (
                                    "Create Account →"
                                ) : (
                                    "Sign In to HealthSummarizer →"
                                )}
                            </button>
                        </form>

                        <div className="lycoris-form-footer">
                            <span className="footer-prompt">
                                {isRegister ? "Already registered?" : "New to HealthSummarizer?"}
                            </span>
                            <button
                                type="button"
                                className="lycoris-switch-link"
                                onClick={() => {
                                    setIsRegister(!isRegister);
                                    setMessage("");
                                }}
                            >
                                {isRegister ? "Sign In Instead" : "Create a Free Account"}
                            </button>
                        </div>
                    </div>
                </section>
            </main>
        </div>
    );
}

export default Auth;
