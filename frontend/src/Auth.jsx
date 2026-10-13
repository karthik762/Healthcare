import { useState } from "react";
import api from "./api";
import { LycorisMark } from "./components/LycorisMark";
import LycorisBackgroundFlower from "./components/LycorisBackgroundFlower";
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
                    : "Unable to sign in. Please verify your credentials and backend connection.")
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="lycoris-auth-experience">
            {/* Ambient atmospheric red bloom */}
            <div className="lycoris-ambient-glow" aria-hidden="true" />
            <div className="lycoris-ambient-glow-secondary" aria-hidden="true" />

            {/* Decorative Subtle Lycoris Red Spider Lily Background */}
            <LycorisBackgroundFlower opacity={0.42} />

            {/* Top Bar */}
            <header className="lycoris-auth-topbar">
                <div className="lycoris-brand">
                    <LycorisMark size={30} />
                    <span className="lycoris-brand-title">HEALTHSUMMARIZER</span>
                    <span className="lycoris-brand-divider">/</span>
                    <span className="lycoris-brand-edition">DOCUMENT MANAGER & AI</span>
                </div>
            </header>

            {/* Main Hero & Auth Split Layout */}
            <main className="lycoris-auth-main">
                {/* Left: Editorial Hero */}
                <section className="lycoris-hero-pane">
                    <div className="lycoris-badge">
                        <span className="lycoris-badge-dot" />
                        <span>THIRD-YEAR CSE PROJECT • HEALTHSUMMARIZER</span>
                    </div>

                    <h1 className="lycoris-hero-title">
                        Your Health Documents,<br />
                        <span className="lycoris-highlight-title">Clearly Summarized.</span>
                    </h1>

                    <p className="lycoris-hero-lead">
                        A clean, secure web application to manage personal health records, extract text
                        from lab reports and prescriptions, and generate structured clinical summaries with an intelligent AI assistant.
                    </p>

                    {/* Feature Pillars */}
                    <div className="lycoris-pillars-grid">
                        <div className="lycoris-pillar">
                            <span className="pillar-num">01</span>
                            <h4>Document Archive</h4>
                            <p>Store, view, and edit medical reports with secure user-isolated access.</p>
                        </div>
                        <div className="lycoris-pillar">
                            <span className="pillar-num">02</span>
                            <h4>AI Summarization</h4>
                            <p>Generate unified, patient-centered summaries combining records and intake symptoms.</p>
                        </div>
                        <div className="lycoris-pillar">
                            <span className="pillar-num">03</span>
                            <h4>Patient Intake</h4>
                            <p>Guided pre-consultation questions with emergency keyword safety checks.</p>
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
                            <h2>{isRegister ? "Create an Account" : "Welcome Back"}</h2>
                            <p>
                                {isRegister
                                    ? "Register with your name and email to start managing your health documents."
                                    : "Enter your registered email and password to access your documents."}
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
                                        placeholder="e.g. John Doe"
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
                                    placeholder="user@example.com"
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
                                        <span>Signing in...</span>
                                    </span>
                                ) : isRegister ? (
                                    "Register Account →"
                                ) : (
                                    "Sign In →"
                                )}
                            </button>
                        </form>

                        <div className="lycoris-form-footer">
                            <span className="footer-prompt">
                                {isRegister ? "Already registered?" : "New user?"}
                            </span>
                            <button
                                type="button"
                                className="lycoris-switch-link"
                                onClick={() => {
                                    setIsRegister(!isRegister);
                                    setMessage("");
                                }}
                            >
                                {isRegister ? "Sign In Instead" : "Create Account"}
                            </button>
                        </div>
                    </div>
                </section>
            </main>
        </div>
    );
}

export default Auth;
