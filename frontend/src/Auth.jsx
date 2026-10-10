
import { useState } from "react";
import api from "./api";
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
            const endpoint = isRegister
                ? "/auth/register"
                : "/auth/login";

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
                    : "Unable to sign in. Check your details and backend.")
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page">
            <form className="auth-card" onSubmit={handleSubmit}>
                <h1>HealthSummarizer</h1>
                <p>Manage your health records securely.</p>

                <h2>{isRegister ? "Create account" : "Welcome back"}</h2>

                {isRegister && (
                    <label>
                        Full name
                        <input
                            type="text"
                            value={name}
                            onChange={(event) => setName(event.target.value)}
                            required
                            maxLength={100}
                        />
                    </label>
                )}

                <label>
                    Email
                    <input
                        type="email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        required
                        maxLength={150}
                    />
                </label>

                <label>
                    Password
                    <input
                        type="password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        required
                        minLength={isRegister ? 8 : 1}
                        maxLength={72}
                    />
                </label>

                {message && (
                    <p className="auth-message" role="alert">{message}</p>
                )}

                <button type="submit" disabled={loading}>
                    {loading
                        ? "Please wait..."
                        : isRegister ? "Create account" : "Sign in"}
                </button>

                <p className="auth-switch">
                    {isRegister
                        ? "Already have an account?"
                        : "New to HealthSummarizer?"}
                    {" "}
                    <button
                        type="button"
                        className="auth-link"
                        onClick={() => {
                            setIsRegister(!isRegister);
                            setMessage("");
                        }}
                    >
                        {isRegister ? "Sign in" : "Create account"}
                    </button>
                </p>
            </form>
        </div>
    );
}

export default Auth;
