
import { useEffect, useState } from "react";

import {
    getDocuments,
    getDocument,
    createDocument,
    updateDocument,
    deleteDocument,
    uploadDocument,
    summarizeDocument
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
    };

    const [documents, setDocuments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activePage, setActivePage] = useState("documents");

    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [viewingDocument, setViewingDocument] = useState(null);

    const [title, setTitle] = useState("");
    const [documentType, setDocumentType] = useState("Blood Report");
    const [content, setContent] = useState("");

    const [message, setMessage] = useState("");
    const [saving, setSaving] = useState(false);

    const [summary, setSummary] = useState("");
    const [summarizingId, setSummarizingId] = useState(null);

    // File upload states
    const [uploadTitle, setUploadTitle] = useState("");
    const [uploadType, setUploadType] = useState("Blood Report");
    const [selectedFile, setSelectedFile] = useState(null);

    // Load all documents
    const loadDocuments = async () => {
        try {
            const response = await getDocuments();
            setDocuments(response.data);
        } catch (error) {
            setMessage("Could not load documents. Check the backend.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (currentUser) {
            loadDocuments();
        }
    }, [currentUser]);

    // Clear the manual document form
    const resetForm = () => {
        setTitle("");
        setDocumentType("Blood Report");
        setContent("");
        setEditingId(null);
        setShowForm(false);
    };

    // Open the form for a new document
    const handleAdd = () => {
        resetForm();
        setMessage("");
        setShowForm(true);
    };

    // View a complete document
    const handleView = async (id) => {
        setMessage("");

        try {
            const response = await getDocument(id);
            setViewingDocument(response.data);
        } catch (error) {
            setMessage("Could not load this document.");
        }
    };

    // Load existing values into the edit form
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
        } catch (error) {
            setMessage("Could not load the document for editing.");
        }
    };

    // Add a new document or update an existing document
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
        } catch (error) {
            setMessage(
                editingId !== null
                    ? "Could not update the document."
                    : "Could not save the document."
            );
        } finally {
            setSaving(false);
        }
    };

    // Upload a PDF or TXT document
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
            await uploadDocument(
                uploadTitle.trim(),
                uploadType,
                selectedFile
            );

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

    // Generate AI summary for a saved document
    const handleSummarize = async (id) => {
        setMessage("");
        setSummary("");
        setSummarizingId(id);

        try {
            const response = await summarizeDocument(id);
            setSummary(response.data.summary);
        } catch (error) {
            setMessage(
                error.response?.data?.error ||
                "Could not generate summary. Check that Ollama is running."
            );
        } finally {
            setSummarizingId(null);
        }
    };

    // Delete a document
    const handleDelete = async (id) => {
        const confirmed = window.confirm(
            "Are you sure you want to delete this document?"
        );

        if (!confirmed) {
            return;
        }

        setMessage("");

        try {
            await deleteDocument(id);

            if (viewingDocument?.id === id) {
                setViewingDocument(null);
            }

            if (editingId === id) {
                resetForm();
            }

            setMessage("Document deleted successfully.");
            await loadDocuments();
        } catch (error) {
            setMessage("Could not delete the document.");
        }
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
                        <span className="navbar-subtitle">Clinical Archive & Intelligence</span>
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
                    className={
                        activePage === "documents"
                            ? "nav-button active"
                            : "nav-button"
                    }
                    onClick={() => setActivePage("documents")}
                >
                    <span className="nav-tab-icon">✦</span>
                    Health Documents
                </button>

                <button
                    className={
                        activePage === "intake"
                            ? "nav-button active"
                            : "nav-button"
                    }
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
                    <>
                        {/* Page Header */}
                        <div className="page-header lycoris-page-header">
                            <div>
                                <h2>Clinical Documents Archive</h2>
                                <p>
                                    Archive, inspect, and analyze laboratory investigations, imaging, and clinical narratives.
                                </p>
                            </div>

                            <button
                                className="add-button"
                                onClick={handleAdd}
                            >
                                + Add Manual Document
                            </button>
                        </div>

                        {/* Success and error messages */}
                        {message && (
                            <div className="message-banner" role="status">
                                <span className="msg-icon">!</span>
                                <span>{message}</span>
                            </div>
                        )}

                        {/* PDF and TXT upload form */}
                        <form
                            className="document-form lycoris-card"
                            onSubmit={handleUpload}
                        >
                            <div className="card-section-header">
                                <div>
                                    <h3>Upload Diagnostic Record</h3>
                                    <p>
                                        Extract and archive clinical text directly from PDF reports or TXT files.
                                    </p>
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
                                        onChange={(event) =>
                                            setUploadTitle(event.target.value)
                                        }
                                        placeholder="e.g. Comprehensive Metabolic Panel (CMP)"
                                        required
                                    />
                                </div>

                                <div className="form-field">
                                    <label htmlFor="uploadType">
                                        Document Category
                                    </label>
                                    <select
                                        id="uploadType"
                                        value={uploadType}
                                        onChange={(event) =>
                                            setUploadType(event.target.value)
                                        }
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
                                    Select Report File <span className="req">*</span>
                                </label>
                                <div className="file-input-wrapper">
                                    <input
                                        id="uploadFile"
                                        type="file"
                                        accept=".pdf,.txt"
                                        onChange={(event) =>
                                            setSelectedFile(
                                                event.target.files?.[0] || null
                                            )
                                        }
                                        required
                                    />
                                </div>
                                {selectedFile && (
                                    <div className="file-selected-badge">
                                        <span>✓ Ready for ingestion:</span>
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
                                    {saving
                                        ? "Ingesting Document..."
                                        : "Upload & Ingest Document →"}
                                </button>
                            </div>
                        </form>

                        {/* Add and edit form */}
                        {showForm && (
                            <form
                                className="document-form lycoris-card"
                                onSubmit={handleSubmit}
                            >
                                <div className="card-section-header">
                                    <div>
                                        <h3>
                                            {editingId !== null
                                                ? "Edit Clinical Record"
                                                : "Create Manual Clinical Entry"}
                                        </h3>
                                        <p>Document observations, symptoms, or clinical consultation summaries.</p>
                                    </div>
                                </div>

                                <div className="form-grid">
                                    <div className="form-field">
                                        <label htmlFor="title">
                                            Document Title <span className="req">*</span>
                                        </label>
                                        <input
                                            id="title"
                                            type="text"
                                            value={title}
                                            onChange={(event) =>
                                                setTitle(event.target.value)
                                            }
                                            placeholder="e.g. Endocrine Consultation Note"
                                            required
                                        />
                                    </div>

                                    <div className="form-field">
                                        <label htmlFor="documentType">
                                            Document Category
                                        </label>
                                        <select
                                            id="documentType"
                                            value={documentType}
                                            onChange={(event) =>
                                                setDocumentType(event.target.value)
                                            }
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
                                        Clinical Observations & Content <span className="req">*</span>
                                    </label>
                                    <textarea
                                        id="content"
                                        value={content}
                                        onChange={(event) =>
                                            setContent(event.target.value)
                                        }
                                        placeholder="Enter clinical observations, laboratory findings, or medical instructions..."
                                        rows={8}
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
                                            ? "Saving Record..."
                                            : editingId !== null
                                                ? "Update Document"
                                                : "Save to Vault"}
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
                        )}

                        {/* Document list section */}
                        <div className="documents-section-header">
                            <h3>Archived Records ({documents.length})</h3>
                            <span className="section-note">End-to-end encrypted clinical storage</span>
                        </div>

                        {loading ? (
                            <div className="empty-state">
                                <h3>Accessing Vault...</h3>
                                <p>Loading your clinical documents securely.</p>
                            </div>
                        ) : documents.length === 0 ? (
                            <div className="empty-state">
                                <h3>No Clinical Documents Found</h3>
                                <p>
                                    Upload a lab report or create a manual entry above to begin building your clinical archive.
                                </p>
                            </div>
                        ) : (
                            <div className="document-list">
                                {documents.map((document) => (
                                    <div
                                        className="document-card lycoris-doc-card"
                                        key={document.id}
                                    >
                                        <div className="document-card-top">
                                            <span className="document-type-badge">
                                                {document.documentType || "Clinical Record"}
                                            </span>
                                            <span className="document-id-chip">#{document.id}</span>
                                        </div>

                                        <div className="document-details">
                                            <h3>{document.title}</h3>

                                            <p className="document-snippet">
                                                {(document.content || "")
                                                    .substring(0, 160)}
                                                {(document.content || "").length > 160
                                                    ? "..."
                                                    : ""}
                                            </p>

                                            <div className="document-meta">
                                                {document.createdAt
                                                    ? `Archived: ${new Date(
                                                        document.createdAt
                                                    ).toLocaleDateString()}`
                                                    : "Archive timestamp recorded"}
                                            </div>
                                        </div>

                                        <div className="card-actions">
                                            <button
                                                className="btn-card-action"
                                                onClick={() =>
                                                    handleView(document.id)
                                                }
                                            >
                                                View
                                            </button>

                                            <button
                                                className="btn-card-action"
                                                onClick={() =>
                                                    handleEdit(document.id)
                                                }
                                            >
                                                Edit
                                            </button>

                                            <button
                                                className="btn-card-action delete-button"
                                                onClick={() =>
                                                    handleDelete(document.id)
                                                }
                                            >
                                                Delete
                                            </button>

                                            <button
                                                className="btn-card-summarize"
                                                onClick={() =>
                                                    handleSummarize(document.id)
                                                }
                                                disabled={summarizingId !== null}
                                            >
                                                {summarizingId === document.id
                                                    ? "Synthesizing..."
                                                    : "AI Summary ✦"}
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* AI Summary modal */}
                        {summary && (
                            <div
                                className="modal-overlay"
                                onClick={() => setSummary("")}
                            >
                                <section
                                    className="view-modal"
                                    role="dialog"
                                    aria-modal="true"
                                    aria-labelledby="summary-title"
                                    onClick={(event) =>
                                        event.stopPropagation()
                                    }
                                >
                                    <h2 id="summary-title">
                                        AI Clinical Synthesis
                                    </h2>

                                    <p>
                                        Synthesized by clinical intelligence models from your archived document.
                                    </p>

                                    <div className="full-content">
                                        {summary}
                                    </div>

                                    <div className="form-actions">
                                        <button
                                            className="secondary-button"
                                            onClick={() => setSummary("")}
                                        >
                                            Close Synthesis
                                        </button>
                                    </div>

                                    <small>
                                        For informational purposes only. Consult a qualified healthcare professional for medical diagnosis and interpretation.
                                    </small>
                                </section>
                            </div>
                        )}

                        {/* Full document view modal */}
                        {viewingDocument && (
                            <div
                                className="modal-overlay"
                                onClick={() => setViewingDocument(null)}
                            >
                                <section
                                    className="view-modal"
                                    role="dialog"
                                    aria-modal="true"
                                    aria-labelledby="view-title"
                                    onClick={(event) =>
                                        event.stopPropagation()
                                    }
                                >
                                    <h2 id="view-title">
                                        {viewingDocument.title}
                                    </h2>

                                    <p>
                                        <strong>Document Identifier:</strong> #{viewingDocument.id}
                                    </p>

                                    <p>
                                        <strong>Category:</strong> {viewingDocument.documentType}
                                    </p>

                                    <p>
                                        <strong>Archived At:</strong>{" "}
                                        {viewingDocument.createdAt
                                            ? new Date(
                                                viewingDocument.createdAt
                                            ).toLocaleString()
                                            : "Recorded"}
                                    </p>

                                    <h3>Archived Content</h3>

                                    <div className="full-content">
                                        {viewingDocument.content}
                                    </div>

                                    <div className="form-actions">
                                        <button
                                            className="add-button"
                                            onClick={() =>
                                                handleEdit(viewingDocument.id)
                                            }
                                        >
                                            Edit Document
                                        </button>

                                        <button
                                            className="secondary-button"
                                            onClick={() =>
                                                setViewingDocument(null)
                                            }
                                        >
                                            Close
                                        </button>
                                    </div>
                                </section>
                            </div>
                        )}
                    </>
                )}
            </main>
        </div>
    );
}

export default App;
