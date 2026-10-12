import { useEffect, useState } from "react";

import {
    getDocuments,
    getDocument,
    createDocument,
    updateDocument,
    deleteDocument,
    uploadDocument,
    summarizeDocument,
    generateConsolidatedSummary,
    askRagQuestion
} from "./services/documentService";
import Auth from "./Auth";
import PatientIntake from "./PatientIntake";
import { LycorisMark } from "./components/LycorisMark";
import "./App.css";

function App() {
    const [currentUser, setCurrentUser] = useState(() => {
        const token = localStorage.getItem("token");
        const savedUser = localStorage.getItem("user");

        if (!token || !savedUser) {
            return null;
        }

        try {
            return JSON.parse(savedUser);
        } catch {
            localStorage.removeItem("token");
            localStorage.removeItem("user");
            return null;
        }
    });

    const handleLogin = (user) => {
        setCurrentUser(user);
    };

    const handleLogout = () => {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setCurrentUser(null);
        setDocuments([]);
        setConsolidatedSummary("");
        setQaHistory([]);
    };

    const [documents, setDocuments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activePage, setActivePage] = useState("summary"); // default unified page

    // Document forms and modal state
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [viewingDocument, setViewingDocument] = useState(null);

    const [title, setTitle] = useState("");
    const [documentType, setDocumentType] = useState("Blood Report");
    const [content, setContent] = useState("");

    const [message, setMessage] = useState("");
    const [saving, setSaving] = useState(false);

    // Single document modal summary
    const [singleSummary, setSingleSummary] = useState("");
    const [summarizingId, setSummarizingId] = useState(null);

    // File upload state
    const [uploadTitle, setUploadTitle] = useState("");
    const [uploadType, setUploadType] = useState("Blood Report");
    const [selectedFile, setSelectedFile] = useState(null);

    // Consolidated Summary state
    const [consolidatedSummary, setConsolidatedSummary] = useState("");
    const [summaryGeneratedAt, setSummaryGeneratedAt] = useState("");
    const [consolidatedLoading, setConsolidatedLoading] = useState(false);
    const [consolidatedError, setConsolidatedError] = useState("");

    // RAG Q&A state
    const [qaHistory, setQaHistory] = useState([]);
    const [questionInput, setQuestionInput] = useState("");
    const [qaLoading, setQaLoading] = useState(false);
    const [qaError, setQaError] = useState("");

    const suggestedQuestions = [
        "What medications are mentioned in my documents?",
        "Summarize my latest blood test results.",
        "What symptoms have been recorded?",
        "Which medical conditions are mentioned across my reports?",
        "Are there any follow-up instructions in my documents?"
    ];

    // Load all documents
    const loadDocuments = async () => {
        try {
            const response = await getDocuments();
            setDocuments(response.data);
        } catch {
            setMessage("Could not load documents. Check the backend connection.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (currentUser) {
            loadDocuments();
        }
    }, [currentUser]);

    // Clear document form
    const resetForm = () => {
        setTitle("");
        setDocumentType("Blood Report");
        setContent("");
        setEditingId(null);
        setShowForm(false);
    };

    // Open form for a new document
    const handleAdd = () => {
        resetForm();
        setMessage("");
        setShowForm(true);
    };

    // View complete document content
    const handleView = async (id) => {
        setMessage("");
        try {
            const response = await getDocument(id);
            setViewingDocument(response.data);
        } catch {
            setMessage("Could not load this document.");
        }
    };

    // Edit existing document
    const handleEdit = async (id) => {
        setMessage("");
        try {
            const response = await getDocument(id);
            const document = response.data;

            setTitle(document.title || "");
            setDocumentType(document.documentType || "Other");
            setContent(document.content || "");
            setEditingId(document.id);
            setShowForm(true);
            setViewingDocument(null);
        } catch {
            setMessage("Could not load the document for editing.");
        }
    };

    // Save or update manual document
    const handleSubmit = async (event) => {
        event.preventDefault();
        setMessage("");

        if (!title.trim() || !content.trim()) {
            setMessage("Please enter a title and document content.");
            return;
        }

        setSaving(true);

        try {
            const documentData = {
                title: title.trim(),
                documentType,
                content: content.trim()
            };

            if (editingId !== null) {
                await updateDocument(editingId, documentData);
                setMessage("Document updated successfully.");
            } else {
                await createDocument(documentData);
                setMessage("Document added successfully.");
            }

            resetForm();
            await loadDocuments();
        } catch {
            setMessage(
                editingId !== null
                    ? "Could not update the document."
                    : "Could not save the document."
            );
        } finally {
            setSaving(false);
        }
    };

    // Upload PDF or TXT document
    const handleUpload = async (event) => {
        event.preventDefault();
        setMessage("");

        if (!uploadTitle.trim() || !selectedFile) {
            setMessage("Please enter a title and select a file.");
            return;
        }

        const fileName = selectedFile.name.toLowerCase();
        if (!fileName.endsWith(".pdf") && !fileName.endsWith(".txt")) {
            setMessage("Only PDF and TXT files are supported.");
            return;
        }

        setSaving(true);

        try {
            await uploadDocument(uploadTitle.trim(), uploadType, selectedFile);
            setMessage("Document uploaded successfully!");

            setUploadTitle("");
            setUploadType("Blood Report");
            setSelectedFile(null);
            event.currentTarget.reset();

            await loadDocuments();
        } catch (error) {
            const errorMessage = error.response?.data;
            setMessage(
                typeof errorMessage === "string"
                    ? errorMessage
                    : "Upload failed. Please check the backend."
            );
        } finally {
            setSaving(false);
        }
    };

    // Delete document
    const handleDelete = async (id) => {
        const confirmed = window.confirm("Are you sure you want to delete this document?");
        if (!confirmed) return;

        setMessage("");
        try {
            await deleteDocument(id);
            if (viewingDocument?.id === id) setViewingDocument(null);
            if (editingId === id) resetForm();

            setMessage("Document deleted successfully.");
            await loadDocuments();
        } catch {
            setMessage("Could not delete the document.");
        }
    };

    // Generate single document summary modal
    const handleSummarizeSingle = async (id) => {
        setMessage("");
        setSingleSummary("");
        setSummarizingId(id);

        try {
            const response = await summarizeDocument(id);
            setSingleSummary(response.data.summary);
        } catch (error) {
            setMessage(
                error.response?.data?.error ||
                "Could not generate summary. Check that Ollama is running."
            );
        } finally {
            setSummarizingId(null);
        }
    };

    // Generate Consolidated AI Summary across ALL user documents
    const handleGenerateConsolidated = async () => {
        if (documents.length === 0) {
            setConsolidatedError("Please upload or add at least one health document first.");
            return;
        }

        setConsolidatedLoading(true);
        setConsolidatedError("");

        try {
            const response = await generateConsolidatedSummary();
            setConsolidatedSummary(response.data.summary);
            setSummaryGeneratedAt(new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }));
        } catch (error) {
            setConsolidatedError(
                error.response?.data?.error ||
                "Failed to generate consolidated summary. Check that Ollama is running."
            );
        } finally {
            setConsolidatedLoading(false);
        }
    };

    // Ask a question using RAG
    const handleAskQuestion = async (queryToAsk) => {
        const query = (queryToAsk || questionInput).trim();
        if (!query) return;

        if (documents.length === 0) {
            setQaError("You do not have any saved health documents. Please add documents first.");
            return;
        }

        setQaLoading(true);
        setQaError("");

        try {
            const response = await askRagQuestion(query);
            const newEntry = {
                id: Date.now(),
                question: query,
                answer: response.data.answer,
                sources: response.data.sources || [],
                timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
            };

            setQaHistory((prev) => [newEntry, ...prev]);
            setQuestionInput("");
        } catch (error) {
            setQaError(
                error.response?.data?.error ||
                "Could not retrieve answer. Check backend and Ollama connectivity."
            );
        } finally {
            setQaLoading(false);
        }
    };

    const handleFormAsk = (e) => {
        e.preventDefault();
        handleAskQuestion();
    };

    if (!currentUser) {
        return <Auth onLogin={handleLogin} />;
    }

    return (
        <div className="app lycoris-app-shell">
            {/* Top Navigation Bar */}
            <header className="navbar lycoris-navbar">
                <div className="navbar-brand">
                    <LycorisMark size={32} />
                    <div>
                        <h1>HealthSummarizer</h1>
                        <span className="navbar-subtitle">Unified Health Summary & RAG Q&A</span>
                    </div>
                </div>

                <div className="navbar-user">
                    <span className="user-indicator">
                        <span className="user-dot" />
                        {currentUser.name || currentUser.email}
                    </span>

                    <button className="logout-button" onClick={handleLogout}>
                        Sign Out
                    </button>
                </div>
            </header>

            {/* Navigation Tabs */}
            <nav className="main-navigation lycoris-navigation">
                <button
                    className={activePage === "summary" ? "nav-button active" : "nav-button"}
                    onClick={() => setActivePage("summary")}
                >
                    <span className="nav-tab-icon">✦</span>
                    Generate Summary
                </button>

                <button
                    className={activePage === "intake" ? "nav-button active" : "nav-button"}
                    onClick={() => setActivePage("intake")}
                >
                    <span className="nav-tab-icon">◈</span>
                    Patient Intake Assistant
                </button>
            </nav>

            <main className="container lycoris-container">
                {activePage === "intake" ? (
                    <PatientIntake />
                ) : (
                    <div className="unified-health-page">
                        {/* =========================================================
                            SECTION A: Unified Page Header
                            ========================================================= */}
                        <div className="page-header lycoris-page-header">
                            <div>
                                <h2>My Health Summary</h2>
                                <p className="page-header-subtitle">
                                    Your health documents, summarized in one place.
                                </p>
                            </div>

                            <div className="header-actions-group">
                                <span className="doc-count-badge">
                                    {documents.length} {documents.length === 1 ? "Document" : "Documents"} Available
                                </span>

                                <button
                                    className="primary-accent-btn"
                                    onClick={handleGenerateConsolidated}
                                    disabled={consolidatedLoading || documents.length === 0}
                                >
                                    {consolidatedLoading
                                        ? "Synthesizing Summary..."
                                        : consolidatedSummary
                                            ? "Regenerate Summary ✦"
                                            : "Generate Summary ✦"}
                                </button>
                            </div>
                        </div>

                        {/* Status notification banner */}
                        {message && (
                            <div className="message-banner" role="status">
                                <span className="msg-icon">!</span>
                                <span>{message}</span>
                            </div>
                        )}

                        {/* =========================================================
                            SECTION B: My Health Documents
                            ========================================================= */}
                        <section className="unified-section documents-management-section">
                            <div className="section-title-bar">
                                <div>
                                    <h3 className="section-title">My Health Documents</h3>
                                    <p className="section-desc">
                                        Upload clinical reports or create manual records to ground your AI summary and Q&A.
                                    </p>
                                </div>

                                <button
                                    className="add-button"
                                    onClick={() => setShowForm(!showForm)}
                                >
                                    {showForm ? "Close Document Form" : "+ Add Document"}
                                </button>
                            </div>

                            {/* Collapsible Document Ingestion & Creation Form */}
                            {showForm && (
                                <div className="forms-wrapper">
                                    {/* Upload Form */}
                                    <form
                                        className="document-form lycoris-card"
                                        onSubmit={handleUpload}
                                    >
                                        <div className="card-section-header">
                                            <div>
                                                <h4>Upload Document (PDF / TXT)</h4>
                                                <p>Extract clinical text directly from laboratory reports or medical records.</p>
                                            </div>
                                            <span className="file-format-badge">PDF / TXT</span>
                                        </div>

                                        <div className="form-grid">
                                            <div className="form-field">
                                                <label htmlFor="uploadTitle">
                                                    Document Title <span className="req">*</span>
                                                </label>
                                                <input
                                                    id="uploadTitle"
                                                    type="text"
                                                    value={uploadTitle}
                                                    onChange={(e) => setUploadTitle(e.target.value)}
                                                    placeholder="e.g. Complete Blood Count (CBC)"
                                                    required
                                                />
                                            </div>

                                            <div className="form-field">
                                                <label htmlFor="uploadType">Document Category</label>
                                                <select
                                                    id="uploadType"
                                                    value={uploadType}
                                                    onChange={(e) => setUploadType(e.target.value)}
                                                >
                                                    <option>Blood Report</option>
                                                    <option>Prescription</option>
                                                    <option>Diagnostic Report</option>
                                                    <option>Medical History</option>
                                                    <option>Other</option>
                                                </select>
                                            </div>
                                        </div>

                                        <div className="form-field file-picker-field">
                                            <label htmlFor="uploadFile">
                                                Select File <span className="req">*</span>
                                            </label>
                                            <div className="file-input-wrapper">
                                                <input
                                                    id="uploadFile"
                                                    type="file"
                                                    accept=".pdf,.txt"
                                                    onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                                                    required
                                                />
                                            </div>
                                            {selectedFile && (
                                                <div className="file-selected-badge">
                                                    <span>Selected:</span>
                                                    <strong>{selectedFile.name}</strong>
                                                    <span>({(selectedFile.size / 1024).toFixed(1)} KB)</span>
                                                </div>
                                            )}
                                        </div>

                                        <div className="form-actions">
                                            <button
                                                className="add-button"
                                                type="submit"
                                                disabled={saving}
                                            >
                                                {saving ? "Uploading..." : "Upload Document →"}
                                            </button>
                                        </div>
                                    </form>

                                    {/* Manual Entry Form */}
                                    <form
                                        className="document-form lycoris-card"
                                        onSubmit={handleSubmit}
                                    >
                                        <div className="card-section-header">
                                            <div>
                                                <h4>
                                                    {editingId !== null ? "Edit Document" : "Create Manual Entry"}
                                                </h4>
                                                <p>Manually record prescriptions, clinical findings, or doctor consultations.</p>
                                            </div>
                                        </div>

                                        <div className="form-grid">
                                            <div className="form-field">
                                                <label htmlFor="title">
                                                    Title <span className="req">*</span>
                                                </label>
                                                <input
                                                    id="title"
                                                    type="text"
                                                    value={title}
                                                    onChange={(e) => setTitle(e.target.value)}
                                                    placeholder="e.g. Cardiology Follow-up Note"
                                                    required
                                                />
                                            </div>

                                            <div className="form-field">
                                                <label htmlFor="documentType">Category</label>
                                                <select
                                                    id="documentType"
                                                    value={documentType}
                                                    onChange={(e) => setDocumentType(e.target.value)}
                                                >
                                                    <option>Blood Report</option>
                                                    <option>Prescription</option>
                                                    <option>Diagnostic Report</option>
                                                    <option>Medical History</option>
                                                    <option>Other</option>
                                                </select>
                                            </div>
                                        </div>

                                        <div className="form-field">
                                            <label htmlFor="content">
                                                Document Content <span className="req">*</span>
                                            </label>
                                            <textarea
                                                id="content"
                                                value={content}
                                                onChange={(e) => setContent(e.target.value)}
                                                placeholder="Enter observations, test results, or prescription details..."
                                                rows={5}
                                                required
                                            />
                                        </div>

                                        <div className="form-actions">
                                            <button
                                                className="add-button"
                                                type="submit"
                                                disabled={saving}
                                            >
                                                {saving
                                                    ? "Saving..."
                                                    : editingId !== null
                                                        ? "Update Document"
                                                        : "Save Document"}
                                            </button>

                                            <button
                                                type="button"
                                                className="secondary-button"
                                                onClick={resetForm}
                                                disabled={saving}
                                            >
                                                Cancel
                                            </button>
                                        </div>
                                    </form>
                                </div>
                            )}

                            {/* Documents Cards List */}
                            {loading ? (
                                <div className="empty-state">
                                    <h3>Loading Documents...</h3>
                                    <p>Fetching your health records from database storage.</p>
                                </div>
                            ) : documents.length === 0 ? (
                                <div className="empty-state">
                                    <h3>No Health Documents Found</h3>
                                    <p>
                                        Add your first health document above to unlock the consolidated AI summary and document Q&A.
                                    </p>
                                    <button
                                        className="add-button"
                                        style={{ marginTop: 16 }}
                                        onClick={handleAdd}
                                    >
                                        + Add Your First Document
                                    </button>
                                </div>
                            ) : (
                                <div className="document-list">
                                    {documents.map((doc) => (
                                        <div className="document-card lycoris-doc-card" key={doc.id}>
                                            <div className="document-card-top">
                                                <span className="document-type-badge">
                                                    {doc.documentType || "General"}
                                                </span>
                                                <span className="document-id-chip">#{doc.id}</span>
                                            </div>

                                            <div className="document-details">
                                                <h3>{doc.title}</h3>
                                                <p className="document-snippet">
                                                    {(doc.content || "").substring(0, 150)}
                                                    {(doc.content || "").length > 150 ? "..." : ""}
                                                </p>
                                                <div className="document-meta">
                                                    {doc.createdAt
                                                        ? `Added: ${new Date(doc.createdAt).toLocaleDateString()}`
                                                        : "Date recorded"}
                                                </div>
                                            </div>

                                            <div className="card-actions">
                                                <button
                                                    className="btn-card-action"
                                                    onClick={() => handleView(doc.id)}
                                                >
                                                    View
                                                </button>
                                                <button
                                                    className="btn-card-action"
                                                    onClick={() => handleEdit(doc.id)}
                                                >
                                                    Edit
                                                </button>
                                                <button
                                                    className="btn-card-action delete-button"
                                                    onClick={() => handleDelete(doc.id)}
                                                >
                                                    Delete
                                                </button>
                                                <button
                                                    className="btn-card-summarize"
                                                    onClick={() => handleSummarizeSingle(doc.id)}
                                                    disabled={summarizingId !== null}
                                                >
                                                    {summarizingId === doc.id ? "Analyzing..." : "Doc Summary ✦"}
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </section>

                        {/* =========================================================
                            SECTION C: Consolidated AI Health Summary
                            ========================================================= */}
                        <section className="unified-section consolidated-summary-section">
                            <div className="section-title-bar">
                                <div>
                                    <h3 className="section-title">Consolidated AI Health Summary</h3>
                                    <p className="section-desc">
                                        Synthesized from all your available medical records by local Ollama model (llama3.2:3b).
                                    </p>
                                </div>

                                {consolidatedSummary && (
                                    <button
                                        className="secondary-button"
                                        onClick={handleGenerateConsolidated}
                                        disabled={consolidatedLoading}
                                    >
                                        {consolidatedLoading ? "Updating..." : "Regenerate ⟳"}
                                    </button>
                                )}
                            </div>

                            {consolidatedError && (
                                <div className="intake-error" style={{ marginBottom: 16 }}>
                                    {consolidatedError}
                                </div>
                            )}

                            {consolidatedLoading ? (
                                <div className="summary-loading-card">
                                    <div className="lycoris-spinner" style={{ width: 28, height: 28 }} />
                                    <div style={{ marginTop: 14 }}>
                                        <strong>Generating Consolidated Summary...</strong>
                                        <p>Analyzing {documents.length} document(s) with Ollama. This takes a few moments.</p>
                                    </div>
                                </div>
                            ) : consolidatedSummary ? (
                                <div className="consolidated-summary-display lycoris-card">
                                    <div className="summary-meta-header">
                                        <span className="summary-badge">
                                            ✓ Unified Clinical Synthesis Across {documents.length} Records
                                        </span>
                                        <span className="summary-date-tag">
                                            {summaryGeneratedAt ? `Generated at ${summaryGeneratedAt}` : "Clinical Synthesis"}
                                        </span>
                                    </div>

                                    <div className="consolidated-content-box">
                                        {consolidatedSummary}
                                    </div>

                                    <div className="summary-disclaimer-box">
                                        <span className="disclaimer-icon">ℹ</span>
                                        <span>
                                            <strong>Medical Disclaimer:</strong> This consolidated summary is generated automatically by an AI model for informational reference only. It does not replace professional medical evaluation, diagnosis, or advice from a qualified doctor.
                                        </span>
                                    </div>
                                </div>
                            ) : (
                                <div className="summary-empty-callout lycoris-card">
                                    <h4>No Consolidated Summary Generated Yet</h4>
                                    <p>
                                        Click <strong>Generate Summary</strong> to combine all your {documents.length} saved health documents into one structured clinical overview.
                                    </p>
                                    <button
                                        className="primary-accent-btn"
                                        onClick={handleGenerateConsolidated}
                                        disabled={documents.length === 0}
                                        style={{ marginTop: 12 }}
                                    >
                                        Generate Consolidated Summary ✦
                                    </button>
                                </div>
                            )}
                        </section>

                        {/* =========================================================
                            SECTION D: Ask Questions About My Documents (RAG Q&A)
                            ========================================================= */}
                        <section className="unified-section rag-qa-section">
                            <div className="section-title-bar">
                                <div>
                                    <h3 className="section-title">Ask Questions About My Documents</h3>
                                    <p className="section-desc">
                                        Retrieval-Augmented Generation (RAG): answers are strictly grounded in your saved document text.
                                    </p>
                                </div>
                            </div>

                            {/* Suggested Quick Question Chips */}
                            <div className="rag-suggested-wrapper">
                                <span className="suggested-label">Suggested Questions:</span>
                                <div className="rag-chips-row">
                                    {suggestedQuestions.map((q, idx) => (
                                        <button
                                            key={idx}
                                            type="button"
                                            className="rag-chip-btn"
                                            onClick={() => handleAskQuestion(q)}
                                            disabled={qaLoading || documents.length === 0}
                                        >
                                            {q}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Question Input Form */}
                            <form className="rag-query-form lycoris-card" onSubmit={handleFormAsk}>
                                <div className="rag-input-group">
                                    <input
                                        type="text"
                                        className="rag-text-input"
                                        value={questionInput}
                                        onChange={(e) => setQuestionInput(e.target.value)}
                                        placeholder="Ask a question about your health documents..."
                                        disabled={qaLoading}
                                    />
                                    <button
                                        type="submit"
                                        className="primary-accent-btn"
                                        disabled={qaLoading || !questionInput.trim() || documents.length === 0}
                                    >
                                        {qaLoading ? "Searching..." : "Ask Question →"}
                                    </button>
                                </div>

                                {qaError && (
                                    <div className="intake-error" style={{ marginTop: 12 }}>
                                        {qaError}
                                    </div>
                                )}
                            </form>

                            {/* Conversation / Q&A History */}
                            {qaLoading && (
                                <div className="rag-loading-state">
                                    <span className="lycoris-spinner" style={{ width: 20, height: 20 }} />
                                    <span>Retrieving relevant excerpts and generating grounded answer...</span>
                                </div>
                            )}

                            {qaHistory.length > 0 && (
                                <div className="rag-history-list">
                                    {qaHistory.map((entry) => (
                                        <div key={entry.id} className="rag-qa-card lycoris-card">
                                            {/* User Question */}
                                            <div className="rag-question-row">
                                                <div className="rag-user-tag">
                                                    <span className="role-chip">QUESTION</span>
                                                    <span className="time-tag">{entry.timestamp}</span>
                                                </div>
                                                <h4 className="rag-question-text">{entry.question}</h4>
                                            </div>

                                            {/* Ollama Grounded Answer */}
                                            <div className="rag-answer-row">
                                                <div className="rag-ai-tag">
                                                    <span className="ai-chip">OLLAMA RAG ANSWER</span>
                                                </div>
                                                <div className="rag-answer-content">
                                                    {entry.answer}
                                                </div>
                                            </div>

                                            {/* Source Citations */}
                                            {entry.sources && entry.sources.length > 0 && (
                                                <div className="rag-sources-box">
                                                    <span className="sources-heading">
                                                        Retrieved Document Sources ({entry.sources.length}):
                                                    </span>
                                                    <div className="sources-list">
                                                        {entry.sources.map((src, sIdx) => (
                                                            <div key={sIdx} className="source-citation-badge">
                                                                <div className="source-header">
                                                                    <strong>📄 {src.title}</strong>
                                                                    <span className="source-type-pill">
                                                                        {src.documentType}
                                                                    </span>
                                                                </div>
                                                                <p className="source-excerpt">
                                                                    "{src.excerpt}"
                                                                </p>
                                                            </div>
                                                        ))}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            )}
                        </section>

                        {/* =========================================================
                            MODALS
                            ========================================================= */}

                        {/* Single Document AI Summary Modal */}
                        {singleSummary && (
                            <div className="modal-overlay" onClick={() => setSingleSummary("")}>
                                <section
                                    className="view-modal"
                                    role="dialog"
                                    aria-modal="true"
                                    onClick={(e) => e.stopPropagation()}
                                >
                                    <h2>Document Summary</h2>
                                    <p>Individual document analysis via Ollama (llama3.2:3b).</p>
                                    <div className="full-content">{singleSummary}</div>
                                    <div className="form-actions">
                                        <button
                                            className="secondary-button"
                                            onClick={() => setSingleSummary("")}
                                        >
                                            Close Summary
                                        </button>
                                    </div>
                                </section>
                            </div>
                        )}

                        {/* Full Document View Modal */}
                        {viewingDocument && (
                            <div className="modal-overlay" onClick={() => setViewingDocument(null)}>
                                <section
                                    className="view-modal"
                                    role="dialog"
                                    aria-modal="true"
                                    onClick={(e) => e.stopPropagation()}
                                >
                                    <h2>{viewingDocument.title}</h2>
                                    <p><strong>Document ID:</strong> #{viewingDocument.id}</p>
                                    <p><strong>Category:</strong> {viewingDocument.documentType}</p>
                                    <p>
                                        <strong>Date:</strong>{" "}
                                        {viewingDocument.createdAt
                                            ? new Date(viewingDocument.createdAt).toLocaleString()
                                            : "Recorded"}
                                    </p>
                                    <h3>Document Content</h3>
                                    <div className="full-content">{viewingDocument.content}</div>
                                    <div className="form-actions">
                                        <button
                                            className="add-button"
                                            onClick={() => handleEdit(viewingDocument.id)}
                                        >
                                            Edit Document
                                        </button>
                                        <button
                                            className="secondary-button"
                                            onClick={() => setViewingDocument(null)}
                                        >
                                            Close
                                        </button>
                                    </div>
                                </section>
                            </div>
                        )}
                    </div>
                )}
            </main>
        </div>
    );
}

export default App;
